package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.HabitRequest;
import com.adrian.habittracker.dto.HabitResponse;
import com.adrian.habittracker.dto.HabitsSummaryResponse;
import com.adrian.habittracker.dto.StreakResponse;
import com.adrian.habittracker.entity.Habit;
import com.adrian.habittracker.entity.HabitLog;
import com.adrian.habittracker.entity.Priority;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.ResourceNotFoundException;
import com.adrian.habittracker.repository.CategoryRepository;
import com.adrian.habittracker.repository.HabitLogRepository;
import com.adrian.habittracker.repository.HabitRepository;
import com.adrian.habittracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    private static final Long USER_ID = 99L;

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitLogRepository habitLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

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

        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabitIdOrderByLogDateDesc(1L)).thenReturn(logs);

        StreakResponse result = habitService.calculateStreak(1L, USER_ID);

        assertThat(result.currentStreak()).isEqualTo(3);
    }

    @Test
    void rachaEsCeroSiHoyNoSeHaCumplido() {
        LocalDate today = LocalDate.now();

        List<HabitLog> logs = List.of(
                logOn(today, false),
                logOn(today.minusDays(1), true)
        );

        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabitIdOrderByLogDateDesc(1L)).thenReturn(logs);

        StreakResponse result = habitService.calculateStreak(1L, USER_ID);

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

        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabitIdOrderByLogDateDesc(1L)).thenReturn(logs);

        StreakResponse result = habitService.calculateStreak(1L, USER_ID);

        assertThat(result.weeklyCompletionRate()).isEqualTo(4.0 / 7.0);
    }

    @Test
    void findByIdLanzaResourceNotFoundSiElHabitoNoEsDelUsuario() {
        // IDOR: findByIdAndUserId (no un simple findById) es lo que impide
        // que el usuario 99 pueda leer/tocar un habito de otro usuario -
        // el repositorio, tal y como esta mockeado aqui, devuelve
        // Optional.empty() tanto si el id no existe como si pertenece a
        // otro; el servicio no debe distinguir esos dos casos hacia fuera.
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.findById(1L, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createUsaPrioridadMediaPorDefectoSiNoSeIndicaNinguna() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitRequest request = new HabitRequest("Meditar", null, null, null);
        HabitResponse response = habitService.create(USER_ID, request);

        assertThat(response.priority()).isEqualTo(Priority.MEDIA);

        // Ademas de comprobar la respuesta, verificamos QUE se guardo con
        // esa prioridad - una respuesta "de mentira" con el valor correcto
        // no demostraria que el habito realmente guardado la tenga.
        ArgumentCaptor<Habit> captor = ArgumentCaptor.forClass(Habit.class);
        verify(habitRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(Priority.MEDIA);
        assertThat(captor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void setArchivedCambiaElFlagSinLlamarASaveExplicitamente() {
        habit.setArchived(false);
        when(habitRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(habit));

        habitService.setArchived(1L, USER_ID, true);

        assertThat(habit.isArchived()).isTrue();
        // Documenta a proposito el patron de "dirty checking" de
        // Hibernate que se explica en el comentario de setArchived(): en
        // un metodo @Transactional no hace falta guardar explicitamente
        // una entidad ya gestionada para que el cambio se persista.
        verify(habitRepository, never()).save(any());
    }

    @Test
    void getSummaryDevuelveCerosSiElUsuarioNoTieneHabitos() {
        when(habitRepository.findByUserId(USER_ID)).thenReturn(List.of());

        HabitsSummaryResponse summary = habitService.getSummary(USER_ID);

        assertThat(summary.activeHabits()).isZero();
        assertThat(summary.averageStreak()).isZero();
        assertThat(summary.averageWeeklyCompletionRate()).isZero();
    }

    @Test
    void getSummaryPromediaRachaYCumplimientoEntreVariosHabitos() {
        LocalDate today = LocalDate.now();

        Habit segundoHabito = new Habit();
        segundoHabito.setId(2L);
        segundoHabito.setName("Correr");

        when(habitRepository.findByUserId(USER_ID)).thenReturn(List.of(habit, segundoHabito));
        // Habito 1: racha de 2 dias. Habito 2: sin logs (racha 0).
        when(habitLogRepository.findByHabitIdOrderByLogDateDesc(1L))
                .thenReturn(List.of(logOn(today, true), logOn(today.minusDays(1), true)));
        when(habitLogRepository.findByHabitIdOrderByLogDateDesc(2L)).thenReturn(List.of());

        HabitsSummaryResponse summary = habitService.getSummary(USER_ID);

        assertThat(summary.activeHabits()).isEqualTo(2);
        // Media de rachas (2 + 0) / 2 = 1.0
        assertThat(summary.averageStreak()).isEqualTo(1.0);
    }

    private HabitLog logOn(LocalDate date, boolean completed) {
        HabitLog log = new HabitLog();
        log.setHabit(habit);
        log.setLogDate(date);
        log.setCompleted(completed);
        return log;
    }
}
