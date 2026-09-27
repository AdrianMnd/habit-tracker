package com.adrian.habittracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    // --- Recordatorio diario por push ---
    // Las tres columnas son nullable: asi ddl-auto=update puede añadirlas
    // sobre usuarios ya existentes sin fallar (igual que "priority" en Habit).

    /** Hora local (0-23) del recordatorio. null = recordatorio desactivado. */
    @Column(name = "reminder_hour")
    private Integer reminderHour;

    /**
     * Zona horaria IANA del usuario ("Europe/Madrid"), la que detecta su
     * navegador. Imprescindible: "las 21:00" no es el mismo instante en
     * Sevilla que en Buenos Aires, y el servidor corre en UTC.
     */
    @Column(name = "reminder_time_zone", length = 64)
    private String reminderTimeZone;

    /** Ultimo dia (en la zona del usuario) en que ya se le envio el recordatorio. */
    @Column(name = "last_reminder_date")
    private LocalDate lastReminderDate;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
