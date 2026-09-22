package com.adrian.habittracker.repository;

import com.adrian.habittracker.entity.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

    Optional<HabitLog> findByHabitIdAndLogDate(Long habitId, LocalDate logDate);

    // Ordenado desc para que el calculo de racha en el service pueda
    // recorrer desde el dia mas reciente hacia atras.
    List<HabitLog> findByHabitIdOrderByLogDateDesc(Long habitId);

    List<HabitLog> findByHabitIdAndLogDateBetween(Long habitId, LocalDate from, LocalDate to);
}
