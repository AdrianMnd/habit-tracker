package com.adrian.habittracker.service;

import com.adrian.habittracker.entity.RefreshToken;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.InvalidRefreshTokenException;
import com.adrian.habittracker.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    // SecureRandom (no Random): Random es predecible si conoces la semilla,
    // SecureRandom usa la fuente de entropia criptografica del sistema
    // operativo. Es thread-safe, asi que una unica instancia compartida vale.
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /** Resultado de rotar: a quien pertenecia el token y el nuevo token en claro. */
    public record Rotation(User user, String newRefreshToken) {
    }

    /**
     * Crea un refresh token nuevo para el usuario y devuelve su valor EN
     * CLARO - es el unico momento en que existe en claro en el servidor; en
     * la base de datos solo queda su hash.
     */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32]; // 256 bits de aleatoriedad
        RANDOM.nextBytes(bytes);
        // Base64 "URL-safe" y sin relleno "=": un token que se puede meter
        // en JSON, cabeceras o URLs sin escapar nada.
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    /**
     * Consume un refresh token y emite uno nuevo (rotacion). Cada refresh
     * token es de UN SOLO USO.
     *
     * noRollbackFor: por defecto, una RuntimeException lanzada dentro de un
     * metodo @Transactional deshace TODO lo que hizo la transaccion. En el
     * caso de reutilizacion queremos justo lo contrario: revocar todas las
     * sesiones del usuario Y ADEMAS responder con error. Sin noRollbackFor,
     * el throw de abajo desharia el revokeAllActiveByUserId() que lo precede
     * y la deteccion de reutilizacion no serviria de nada.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        Instant now = Instant.now();

        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token no valido"));

        if (current.getRevokedAt() != null) {
            // Un token ya consumido vuelve a aparecer: alguien tiene una
            // copia. No sabemos si quien lo presenta ahora es el usuario
            // legitimo o un atacante, asi que cortamos por lo sano: todas
            // las sesiones de ese usuario quedan revocadas y ambos tendran
            // que volver a hacer login (el atacante no puede, no conoce la
            // contraseña).
            refreshTokenRepository.revokeAllActiveByUserId(current.getUser().getId(), now);
            throw new InvalidRefreshTokenException("Refresh token reutilizado: se han cerrado todas las sesiones");
        }

        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException("Refresh token caducado");
        }

        // Entidad gestionada dentro de una transaccion: basta con cambiarla,
        // Hibernate hace el UPDATE al hacer commit (dirty checking, igual
        // que en HabitService.setArchived).
        current.setRevokedAt(now);

        // Llamada interna a issue(): su @Transactional se ignora (el proxy
        // de Spring solo intercepta llamadas que vienen DE FUERA del bean),
        // pero da igual porque ya estamos dentro de la transaccion de
        // rotate() - el token nuevo se guarda en la misma transaccion.
        String newRawToken = issue(current.getUser());
        return new Rotation(current.getUser(), newRawToken);
    }

    /** Logout: revoca el token si existe y sigue activo. Idempotente. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    /**
     * SHA-256 y no BCrypt (a diferencia de las contraseñas): BCrypt es lento
     * a proposito y lleva una sal aleatoria para frenar ataques de fuerza
     * bruta contra contraseñas humanas, que tienen poca entropia. Un token de
     * 256 bits aleatorios no se puede adivinar por fuerza bruta, asi que no
     * hace falta esa lentitud - y ademas necesitamos un hash DETERMINISTA
     * para poder buscar el token por su hash (con la sal de BCrypt, el mismo
     * token daria un hash distinto cada vez y no habria forma de encontrarlo).
     */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Todas las JVM estan obligadas a incluir SHA-256: no deberia
            // pasar nunca, pero la API obliga a tratar la excepcion.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}