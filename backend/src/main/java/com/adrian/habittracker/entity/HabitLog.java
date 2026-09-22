package com.adrian.habittracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Registro de si un habito se cumplio o no en una fecha concreta.
 * Un habito tiene, como mucho, un log por fecha (constraint unico
 * habit_id + log_date).
 */
@Entity
@Table(
        name = "habit_logs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "log_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HabitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false)
    private Habit habit;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    private boolean completed;
}
