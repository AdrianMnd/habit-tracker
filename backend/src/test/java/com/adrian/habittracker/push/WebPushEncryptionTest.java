package com.adrian.habittracker.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hace de "navegador": genera sus propias claves de suscripcion, recibe el
 * mensaje cifrado por WebPushEncryption y lo descifra siguiendo RFC 8291 desde
 * el lado del receptor. Si el cifrado no siguiera el estandar al pie de la
 * letra, el descifrado fallaria (AES-GCM no perdona ni un bit).
 */
class WebPushEncryptionTest {

    private KeyPair vapidKeys;
    private KeyPair browserKeys;
    private byte[] authSecret;
    private WebPushEncryption encryption;

    @BeforeEach
    void setUp() throws Exception {
        vapidKeys = newP256KeyPair();
        browserKeys = newP256KeyPair();
        authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);

        encryption = new WebPushEncryption(
                WebPushEncryption.b64(WebPushEncryption.encodePoint((ECPublicKey) vapidKeys.getPublic())),
                WebPushEncryption.b64(toFixed32(((ECPrivateKey) vapidKeys.getPrivate()).getS())),
                "mailto:test@example.com");
    }

    @Test
    void elNavegadorPuedeDescifrarElMensaje() throws Exception {
        String payload = "{\"title\":\"Habit Tracker\",\"body\":\"Te quedan 2 hábitos por marcar hoy ✓\"}";

        byte[] body = encryption.encrypt(payload.getBytes(StandardCharsets.UTF_8),
                WebPushEncryption.b64(WebPushEncryption.encodePoint((ECPublicKey) browserKeys.getPublic())),
                WebPushEncryption.b64(authSecret));

        assertThat(new String(decryptAsBrowser(body), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void cadaEnvioUsaClavesEfimerasDistintas() throws Exception {
        String userKey = WebPushEncryption.b64(WebPushEncryption.encodePoint((ECPublicKey) browserKeys.getPublic()));
        byte[] first = encryption.encrypt("hola".getBytes(StandardCharsets.UTF_8), userKey, WebPushEncryption.b64(authSecret));
        byte[] second = encryption.encrypt("hola".getBytes(StandardCharsets.UTF_8), userKey, WebPushEncryption.b64(authSecret));

        // Mismo mensaje, mismo destinatario... y aun asi bytes distintos
        // (salt y clave efimera nuevos): un observador ni siquiera puede
        // saber si dos notificaciones dicen lo mismo.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void elJwtVapidEstaFirmadoConLaClaveDelServidorYApuntaAlOrigenDelServicio() throws Exception {
        String jwt = encryption.vapidJwt("https://fcm.googleapis.com/fcm/send/abc123", Instant.now().plusSeconds(3600));
        String[] parts = jwt.split("\\.");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(vapidKeys.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(WebPushEncryption.decode(parts[2]))).isTrue();

        String claims = new String(WebPushEncryption.decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims)
                .contains("\"aud\":\"https://fcm.googleapis.com\"")
                .contains("\"sub\":\"mailto:test@example.com\"");
    }

    @Test
    void unaClavePublicaVapidMalFormadaSeRechazaAlArrancar() {
        assertThatThrownBy(() -> new WebPushEncryption("no-es-una-clave", "aaaa", "mailto:x@example.com"))
                .isInstanceOf(GeneralSecurityException.class);
    }

    // --- el lado del navegador (receptor), segun RFC 8291 ---

    private byte[] decryptAsBrowser(byte[] body) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        int recordSize = buffer.getInt();
        byte[] senderPublic = new byte[buffer.get() & 0xFF]; // "& 0xFF": byte de Java es con signo
        buffer.get(senderPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        assertThat(recordSize).isEqualTo(4096);

        byte[] browserPublic = WebPushEncryption.encodePoint((ECPublicKey) browserKeys.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browserKeys.getPrivate());
        agreement.doPhase(toPublicKey(senderPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, senderPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] contentKey = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);

        // Ultimo byte: delimitador 0x02 de "ultimo registro"
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private PublicKey toPublicKey(byte[] point) throws Exception {
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(
                new ECPublicKeySpec(w, ((ECPublicKey) browserKeys.getPublic()).getParams()));
    }

    private static KeyPair newP256KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static byte[] toFixed32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, fixed, 32 - length, length);
        return fixed;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int total = Arrays.stream(parts).mapToInt(p -> p.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(total);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }
}