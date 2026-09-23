package com.adrian.habittracker.repository;

import com.adrian.habittracker.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HabitRepository extends JpaRepository<Habit, Long> {
    List<Habit> findByUserId(Long userId);

    // Filtrar por userId aqui, no solo por id, es lo que impide que un
    // usuario acceda/edite/borre habitos de otro adivinando su id numerico
    // (proteccion basica contra IDOR - Insecure Direct Object Reference).
    Optional<Habit> findByIdAndUserId(Long id, Long userId);
}
