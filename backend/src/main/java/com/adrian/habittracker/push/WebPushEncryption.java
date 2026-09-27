package com.adrian.habittracker.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * La criptografia de Web Push, solo con el JDK (sin dependencias externas):
 *
 * - RFC 8291 (cifrado del mensaje): cada notificacion se cifra para UNA
 *   suscripcion concreta, con las claves que genero su navegador. El servicio
 *   de push (Google, Mozilla, Apple) transporta el mensaje pero NO puede leerlo.
 *
 * - RFC 8292 (VAPID): un JWT firmado con la clave privada del servidor, que
 *   demuestra al servicio de push que la notificacion viene de quien creo la
 *   suscripcion (el navegador la ato a nuestra clave publica al suscribirse).
 *
 * Todo en la curva eliptica P-256 ("secp256r1"), la que exige el estandar.
 * Clase sin Spring a proposito: es logica pura, facil de probar aislada.
 */
public final class WebPushEncryption {

    private static final String CURVE = "secp256r1";
    private static final int RECORD_SIZE = 4096;
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    // El decoder URL-safe del JDK acepta la cadena con o sin relleno "=".
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final SecureRandom random = new SecureRandom();
    private final ECParameterSpec curveParams;
    private final ECPrivateKey vapidPrivateKey;
    private final String vapidPublicKey;
    private final String subject;

    /**
     * @param vapidPublicKey  clave publica VAPID: punto sin comprimir de 65 bytes, en base64url
     * @param vapidPrivateKey clave privada VAPID: escalar de 32 bytes, en base64url
     * @param subject         contacto del emisor ("mailto:..."), exigido por RFC 8292
     */
    public WebPushEncryption(String vapidPublicKey, String vapidPrivateKey, String subject) throws GeneralSecurityException {
        this.curveParams = curveParams();
        this.vapidPublicKey = vapidPublicKey;
        this.subject = subject;
        // Validamos ya la publica (lanza si no es un punto valido de la curva):
        // mejor fallar al arrancar que en el primer envio.
        decodePublicKey(decode(vapidPublicKey));
        this.vapidPrivateKey = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, decode(vapidPrivateKey)), curveParams));
    }

    public String vapidPublicKey() {
        return vapidPublicKey;
    }

    /**
     * Cifra el payload para una suscripcion (formato "aes128gcm" de RFC 8188/8291).
     *
     * @param userPublicKey la clave "p256dh" de la suscripcion, en base64url
     * @param authSecret    el secreto "auth" de la suscripcion, en base64url
     * @return el cuerpo binario listo para enviar
     */
    public byte[] encrypt(byte[] payload, String userPublicKey, String authSecret) throws GeneralSecurityException {
        byte[] uaPublic = decode(userPublicKey);
        byte[] auth = decode(authSecret);

        // 1. Un par de claves EFIMERO, nuevo para cada mensaje: aunque alguien
        //    robara la clave de un mensaje, no le serviria para ningun otro.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(CURVE), random);
        KeyPair asKeys = generator.generateKeyPair();
        byte[] asPublic = encodePoint((ECPublicKey) asKeys.getPublic());

        // 2. ECDH (Diffie-Hellman con curvas elipticas): nuestra privada
        //    efimera + la publica del navegador dan un secreto compartido que
        //    el navegador tambien puede calcular (con su privada + nuestra
        //    publica efimera, que va en la cabecera del mensaje) y que nadie
        //    mas puede, aunque vea pasar ambas claves publicas.
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(asKeys.getPrivate());
        agreement.doPhase(decodePublicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        // 3. HKDF: "estira" el secreto compartido en claves de uso concreto.
        //    HKDF-Extract es un HMAC; HKDF-Expand, para salidas de 32 bytes o
        //    menos, es un unico HMAC con la "info" seguida del byte 0x01.
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        byte[] prkKey = hmac(auth, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat(ascii("WebPush: info"), new byte[]{0}, uaPublic, asPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] contentKey = Arrays.copyOf(hmac(prk, concat(ascii("Content-Encoding: aes128gcm"), new byte[]{0, 1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat(ascii("Content-Encoding: nonce"), new byte[]{0, 1})), 12);

        // 4. AES-128-GCM: cifra y autentica a la vez (si alguien altera un
        //    solo bit por el camino, el descifrado falla). El 0x02 final es el
        //    delimitador de "ultimo registro" que exige el formato.
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(concat(payload, new byte[]{2}));

        // 5. Cabecera: salt(16) | tamaño de registro(4) | longitud de clave(1) | clave publica efimera(65)
        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) asPublic.length)
                .put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /**
     * Cabecera Authorization VAPID para un endpoint concreto:
     * "vapid t=&lt;JWT&gt;, k=&lt;clave publica&gt;".
     */
    public String authorizationHeader(String endpoint) throws GeneralSecurityException {
        return "vapid t=" + vapidJwt(endpoint, Instant.now().plusSeconds(12 * 3600)) + ", k=" + vapidPublicKey;
    }

    String vapidJwt(String endpoint, Instant expiresAt) throws GeneralSecurityException {
        URI uri = URI.create(endpoint);
        // "aud" es el ORIGEN del servicio de push (esquema + host), no la URL
        // completa: un mismo JWT sirve para todas las suscripciones de ese
        // servicio.
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());

        String header = b64(ascii("{\"typ\":\"JWT\",\"alg\":\"ES256\"}"));
        String claims = b64(("{\"aud\":\"" + jsonEscape(audience) + "\",\"exp\":" + expiresAt.getEpochSecond()
                + ",\"sub\":\"" + jsonEscape(subject) + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;

        // "inP1363Format": la firma sale como R||S (64 bytes), que es lo que
        // exige JWT. Con "SHA256withECDSA" a secas saldria en formato DER
        // (ASN.1), otro envoltorio de los mismos dos numeros que los
        // servicios de push rechazarian.
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(vapidPrivateKey);
        signature.update(ascii(signingInput));
        return signingInput + "." + b64(signature.sign());
    }

    // --- utilidades de claves y bytes ---

    private static ECParameterSpec curveParams() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(CURVE));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    /** Punto sin comprimir (0x04 | X | Y, 65 bytes) -> clave publica del JDK. */
    private ECPublicKey decodePublicKey(byte[] point) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 0x04) {
            throw new GeneralSecurityException("Clave publica P-256 invalida: se esperaban 65 bytes empezando por 0x04");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curveParams));
    }

    /** Clave publica del JDK -> punto sin comprimir de 65 bytes. */
    static byte[] encodePoint(ECPublicKey key) {
        return concat(new byte[]{0x04}, toFixed32(key.getW().getAffineX()), toFixed32(key.getW().getAffineY()));
    }

    /**
     * BigInteger.toByteArray() no devuelve siempre 32 bytes: puede añadir un
     * 0x00 delante (el bit de signo, para que un numero "grande" no se lea
     * como negativo) o devolver menos si el numero empieza por ceros. Lo
     * alineamos a la derecha en un array de exactamente 32 bytes.
     */
    private static byte[] toFixed32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, fixed, 32 - length, length);
        return fixed;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    // "byte[]... parts": parametro varargs, igual que (...parts) en
    // JavaScript - se puede llamar con cualquier numero de arrays.
    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] decode(String base64Url) {
        return B64_DECODER.decode(base64Url.trim());
    }

    static String b64(byte[] bytes) {
        return B64_ENCODER.encodeToString(bytes);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}