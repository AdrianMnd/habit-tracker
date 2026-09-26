package com.adrian.habittracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Un refresh token emitido a un usuario. NUNCA se guarda el token en claro:
 * solo su hash SHA-256 (igual que una contraseña nunca se guarda en claro) -
 * si alguien lee esta tabla, no puede usar lo que encuentre para renovar
 * sesiones ajenas.
 *
 * revokedAt == null significa "activo". Al rotarlo (o al hacer logout) se
 * rellena con la fecha de revocacion en vez de borrar la fila: necesitamos
 * seguir encontrandolo despues para poder DETECTAR que alguien intenta
 * reutilizar un token ya consumido.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 64 = longitud de un SHA-256 en hexadecimal (32 bytes * 2 caracteres).
    // unique = true crea tambien un indice, que es justo lo que necesitamos:
    // cada /refresh busca por esta columna.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}