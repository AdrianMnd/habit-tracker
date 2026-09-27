package com.adrian.habittracker.repository;

import com.adrian.habittracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findByReminderHourIsNotNull();

    /**
     * "Reclama" el recordatorio de hoy de forma ATOMICA: solo actualiza la
     * fila si todavia no estaba marcada para ese dia, y devuelve cuantas
     * filas cambio (1 = te toca enviarlo a ti, 0 = ya lo hizo otro).
     *
     * Si se hiciera en dos pasos (leer lastReminderDate en Java, comprobar,
     * y luego guardar), dos ejecuciones del cron solapadas podrian leer
     * ambas "no enviado" antes de que ninguna guarde, y el usuario recibiria
     * el recordatorio dos veces. Un UPDATE ... WHERE es atomico en la base de
     * datos: de dos UPDATE simultaneos sobre la misma fila, solo uno ve la
     * condicion como cierta.
     */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastReminderDate = :today " +
           "WHERE u.id = :userId AND (u.lastReminderDate IS NULL OR u.lastReminderDate <> :today)")
    int claimDailyReminder(@Param("userId") Long userId, @Param("today") LocalDate today);
}