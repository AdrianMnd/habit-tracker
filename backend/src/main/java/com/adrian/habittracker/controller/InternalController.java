package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.push.ReminderRunResponse;
import com.adrian.habittracker.service.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Endpoints maquina-a-maquina, para el cron de GitHub Actions. No usan JWT
 * (no hay ningun usuario detras): se autentican con un secreto compartido en
 * la cabecera X-Cron-Secret. En SecurityConfig, /api/internal/** es
 * permitAll PORQUE la comprobacion la hace este controller.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {

    private final PushService pushService;

    @Value("${cron.secret}")
    private String cronSecret;

    @PostMapping("/reminders/run")
    public ResponseEntity<ReminderRunResponse> runReminders(
            @RequestHeader(value = "X-Cron-Secret", required = false) String providedSecret) {
        if (!secretMatches(cronSecret, providedSecret)) {
            // 404 y no 401/403: a quien no conoce el secreto ni siquiera le
            // confirmamos que este endpoint existe.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(pushService.sendDueReminders());
    }

    /**
     * Comparacion en TIEMPO CONSTANTE. Un equals() normal deja de comparar en
     * el primer caracter distinto, asi que tarda un poco mas cuanto mas
     * caracteres iniciales aciertas; midiendo esos microsegundos, un atacante
     * podria ir adivinando el secreto caracter a caracter (timing attack).
     * MessageDigest.isEqual recorre siempre todos los bytes.
     *
     * Un secreto sin configurar desactiva el endpoint entero: si no, bastaria
     * con mandar la cabecera vacia para pasar la comprobacion.
     */
    static boolean secretMatches(String configured, String provided) {
        if (configured == null || configured.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}