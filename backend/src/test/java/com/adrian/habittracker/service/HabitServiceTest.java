package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.StreakResponse;
import com.adrian.habittracker.entity.Habit;
import com.adrian.habittracker.entity.HabitLog;
import com.adrian.habittracker.repository.HabitLogRepository;
import com.adrian.habittracker.repository.HabitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitLogRepository habitLogRepository;

    @InjectMocks
    private HabitService habitService;

    private Habit habit;

    @BeforeEach
    void setUp() {
        habit = new Habit();
        habit.setId(1L);
        habit.setName("Meditar");
    }

    @Test
    void calculaRachaDeTresDiasConsecutivosHastaHoy() {
        LocalDate today = LocalDate.now();

        List<HabitLog> logs = List.of(
                logOn(today, true),
                logOn(today.minusDays(1), true),
                logOn(today.minusDays(2), true),
                logOn(today.minusDays(3), false) // rompe la racha
        );

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabitIdOrderByLogDateDesc(1L)).thenReturn(logs);

        StreakResponse result = habitService.calculateStreak(1L);

        assertThat(result.currentStreak()).isEqualTo(3);
    }

    @Test
    void rachaEsCeroSiHoyNoSeHaCumplido() {
        LocalDate today = LocalDate.now();

        List<HabitLog> logs = List.of(
                logOn(today, false),
                logOn(today.minusDays(1), true)
        );

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabitIdOrderByLogDateDesc(1L)).thenReturn(logs);

        StreakResponse result = habitService.calculateStreak(1L);

        assertThat(result.currentStreak()).isZero();
    }

    @Test
    void calculaPorcentajeSemanalConCuatroDeSieteDiasCumplidos() {
        LocalDate today = LocalDate.now();

        List<HabitLog> logs = List.of(
                logOn(today, true),
                logOn(today.minusDays(1), true),
                logOn(today.minusDays(2), true),
                logOn(today.minusDays(3), true),
                logOn(today.minusDays(4), false),
                logOn(today.minusDays(5), false),
                logOn(today.minusDays(6), false)
        );

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabitIdOrderByLogDateDesc(1L)).thenReturn(logs);

        StreakResponse result = habitService.calculateStreak(1L);

        assertThat(result.weeklyCompletionRate()).isEqualTo(4.0 / 7.0);
    }

    private HabitLog logOn(LocalDate date, boolean completed) {
        HabitLog log = new HabitLog();
        log.setHabit(habit);
        log.setLogDate(date);
        log.setCompleted(completed);
        return log;
    }
}
