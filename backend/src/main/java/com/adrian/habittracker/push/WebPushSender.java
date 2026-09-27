package com.adrian.habittracker.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Envia notificaciones Web Push. Si las claves VAPID no estan configuradas,
 * la app arranca igual con el push desactivado (util en local y en CI, donde
 * no hacen falta), en vez de impedir el arranque entero.
 */
@Slf4j
@Component
public class WebPushSender {

    /**
     * Servicios de push de los navegadores. El endpoint de una suscripcion lo
     * envia el NAVEGADOR (o sea, cualquiera con un token valido), y el backend
     * hara un POST a esa URL: sin esta lista, alguien podria registrar
     * "http://servicio-interno/..." y usar nuestro servidor para lanzar
     * peticiones a donde quisiera (SSRF, Server-Side Request Forgery).
     */
    private static final List<String> ALLOWED_PUSH_HOSTS = List.of(
            "fcm.googleapis.com",                  // Chrome, Edge (Android), Brave, Opera...
            "updates.push.services.mozilla.com",   // Firefox
            ".push.apple.com",                     // Safari (macOS / iOS con la PWA instalada)
            ".notify.windows.com"                  // Edge en Windows
    );

    public enum Result { DELIVERED, EXPIRED, FAILED }

    private final WebPushEncryption encryption; // null = push desactivado
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WebPushSender(@Value("${push.vapid.public-key}") String publicKey,
                         @Value("${push.vapid.private-key}") String privateKey,
                         @Value("${push.vapid.subject}") String subject) throws GeneralSecurityException {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.warn("Web Push desactivado: faltan VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY");
            this.encryption = null;
            return;
        }
        if (subject.isBlank()) {
            // Algunos servicios (Apple) rechazan un JWT sin contacto valido:
            // mejor un error claro al arrancar que fallos misteriosos al enviar.
            throw new IllegalStateException("VAPID_SUBJECT es obligatorio (p. ej. mailto:tu@email.com)");
        }
        this.encryption = new WebPushEncryption(publicKey, privateKey, subject);
    }

    public boolean isEnabled() {
        return encryption != null;
    }

    public Optional<String> publicKey() {
        return Optional.ofNullable(encryption).map(WebPushEncryption::vapidPublicKey);
    }

    public static boolean isAllowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null
                    && ALLOWED_PUSH_HOSTS.stream().anyMatch(allowed ->
                    allowed.startsWith(".") ? host.endsWith(allowed) : host.equals(allowed));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Envia un payload (JSON) a UNA suscripcion. No lanza excepciones: el
     * llamador recorre muchas suscripciones y un fallo en una no debe cortar
     * el envio al resto.
     */
    public Result send(String endpoint, String p256dh, String auth, String payloadJson) {
        if (!isEnabled()) {
            throw new IllegalStateException("Web Push no esta configurado");
        }
        try {
            byte[] body = encryption.encrypt(payloadJson.getBytes(StandardCharsets.UTF_8), p256dh, auth);

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    // TTL: cuanto tiempo guarda el servicio de push el mensaje
                    // si el dispositivo esta apagado. Un recordatorio de "hoy"
                    // no tiene sentido entregarlo pasado mañana: 12 horas.
                    .header("TTL", String.valueOf(12 * 3600))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", encryption.authorizationHeader(endpoint))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();

            if (status >= 200 && status < 300) {
                return Result.DELIVERED;
            }
            if (status == 404 || status == 410) {
                // 404/410: la suscripcion ya no existe (permiso revocado,
                // navegador desinstalado...). Hay que borrarla para no seguir
                // intentandolo cada dia.
                return Result.EXPIRED;
            }
            log.warn("El servicio de push {} respondio {}", URI.create(endpoint).getHost(), status);
            return Result.FAILED;
        } catch (InterruptedException e) {
            // Si alguien interrumpe este hilo, se restaura la marca de
            // interrupcion en vez de "tragarsela": asi quien lo gestione
            // (p. ej. un apagado ordenado del servidor) se sigue enterando.
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.warn("No se pudo enviar una notificacion push: {}", e.getMessage());
            return Result.FAILED;
        }
    }
}