package com.adrian.habittracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa un habito definido por el usuario (p.ej. "Meditar 10 minutos").
 * La frecuencia se modela de forma simple: por ahora solo "diaria",
 * pero se deja el campo abierto por si en el futuro se quiere ampliar
 * a semanal o a dias concretos.
 */
@Entity
@Table(name = "habits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    // Nullable a proposito: asi Hibernate puede anadir esta columna con
    // ddl-auto=update sin fallar sobre filas ya existentes (a diferencia
    // de cuando anadimos user_id como NOT NULL, que exigio un TRUNCATE).
    // El valor por defecto se aplica en Java al crear un habito nuevo.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority = Priority.MEDIA;

    // Sin "nullable = false": un habito puede no tener categoria asignada.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HabitLog> logs = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
