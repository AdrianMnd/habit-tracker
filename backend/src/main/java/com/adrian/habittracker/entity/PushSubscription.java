package com.adrian.habittracker.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Una suscripcion Web Push = un navegador/dispositivo concreto de un usuario.
 * Un usuario puede tener varias (movil + portatil); un recordatorio se envia
 * a todas.
 *
 * Los tres campos los genera el NAVEGADOR al suscribirse (PushSubscription
 * de la API Push): no son secretos del servidor, pero si permiten enviar a
 * ese dispositivo junto con nuestra clave VAPID privada.
 */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // La URL del servicio de push para este navegador. Unica: identifica la
    // suscripcion (si el mismo navegador se vuelve a suscribir, se actualiza
    // la fila en vez de duplicarla). 1024: las de algunos servicios son largas.
    @Column(nullable = false, unique = true, length = 1024)
    private String endpoint;

    /** Clave publica ECDH del navegador (base64url) - "p256dh" en la API Push. */
    @Column(nullable = false)
    private String p256dh;

    /** Secreto de autenticacion del navegador (base64url) - "auth" en la API Push. */
    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}