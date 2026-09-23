package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.*;
import com.adrian.habittracker.entity.Habit;
import com.adrian.habittracker.entity.HabitLog;
import com.adrian.habittracker.entity.Priority;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.ResourceNotFoundException;
import com.adrian.habittracker.repository.HabitLogRepository;
import com.adrian.habittracker.repository.HabitRepository;
import com.adrian.habittracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final UserRepository userRepository;

    public List<HabitResponse> findAll(Long userId) {
        return habitRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public HabitResponse findById(Long id, Long userId) {
        return toResponse(getOwnedHabitOrThrow(id, userId));
    }

    @Transactional
    public HabitResponse create(Long userId, HabitRequest request) {
        // getReferenceById no consulta la BD de inmediato: crea un proxy
        // "perezoso" que solo se resuelve si de verdad se accede a sus
        // campos. Nos vale porque solo lo usamos para fijar la relacion.
        User user = userRepository.getReferenceById(userId);

        Habit habit = new Habit();
        habit.setUser(user);
        habit.setName(request.name());
        habit.setDescription(request.description());
        habit.setPriority(request.priority() != null ? request.priority() : Priority.MEDIA);
        return toResponse(habitRepository.save(habit));
    }

    @Transactional
    public HabitResponse update(Long id, Long userId, HabitRequest request) {
        Habit habit = getOwnedHabitOrThrow(id, userId);
        habit.setName(request.name());
        habit.setDescription(request.description());
        habit.setPriority(request.priority() != null ? request.priority() : Priority.MEDIA);
        return toResponse(habitRepository.save(habit));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Habit habit = getOwnedHabitOrThrow(id, userId);
        habitRepository.delete(habit);
    }

    @Transactional
    public void logCompletion(Long habitId, Long userId, HabitLogRequest request) {
        Habit habit = getOwnedHabitOrThrow(habitId, userId);

        HabitLog log = habitLogRepository
                .findByHabitIdAndLogDate(habitId, request.logDate())
                .orElseGet(() -> {
                    HabitLog newLog = new HabitLog();
                    newLog.setHabit(habit);
                    newLog.setLogDate(request.logDate());
                    return newLog;
                });

        log.setCompleted(request.completed());
        habitLogRepository.save(log);
    }

    /**
     * Vista semanal: para cada habito del usuario, su estado (cumplido/no)
     * en cada uno de los 7 dias a partir de weekStart. Una sola llamada en
     * vez de una por habito, para que el calendario no dispare N peticiones.
     */
    public List<HabitWeekEntry> getWeekView(Long userId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Habit> habits = habitRepository.findByUserId(userId);

        return habits.stream()
                .map(habit -> buildWeekEntry(habit, weekStart, weekEnd))
                .toList();
    }

    private HabitWeekEntry buildWeekEntry(Habit habit, LocalDate weekStart, LocalDate weekEnd) {
        List<HabitLog> logs = habitLogRepository.findByHabitIdAndLogDateBetween(habit.getId(), weekStart, weekEnd);

        Map<LocalDate, Boolean> days = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            days.put(weekStart.plusDays(i), false);
        }
        for (HabitLog log : logs) {
            days.put(log.getLogDate(), log.isCompleted());
        }

        return new HabitWeekEntry(habit.getId(), habit.getName(), days);
    }

    public StreakResponse calculateStreak(Long habitId, Long userId) {
        getOwnedHabitOrThrow(habitId, userId); // valida propiedad antes de calcular

        List<HabitLog> logsDesc = habitLogRepository.findByHabitIdOrderByLogDateDesc(habitId);

        Map<LocalDate, Boolean> completionByDate = logsDesc.stream()
                .collect(Collectors.toMap(HabitLog::getLogDate, HabitLog::isCompleted, (a, b) -> a));

        int currentStreak = computeCurrentStreak(completionByDate);
        double weeklyRate = computeWeeklyCompletionRate(completionByDate);

        return new StreakResponse(habitId, currentStreak, weeklyRate);
    }

    private int computeCurrentStreak(Map<LocalDate, Boolean> completionByDate) {
        int streak = 0;
        LocalDate day = LocalDate.now();

        while (Boolean.TRUE.equals(completionByDate.get(day))) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    private double computeWeeklyCompletionRate(Map<LocalDate, Boolean> completionByDate) {
        LocalDate today = LocalDate.now();
        long completedDays = 0;

        for (int i = 0; i < 7; i++) {
            LocalDate day = today.minusDays(i);
            if (Boolean.TRUE.equals(completionByDate.get(day))) {
                completedDays++;
            }
        }
        return completedDays / 7.0;
    }

    /**
     * Busca por id Y userId a la vez (no solo por id): si el habito existe
     * pero pertenece a otro usuario, esto lanza el mismo 404 que si no
     * existiera en absoluto - no revelamos que el recurso existe pero no
     * es tuyo, evitamos filtrar esa informacion.
     */
    private Habit getOwnedHabitOrThrow(Long id, Long userId) {
        return habitRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Habito no encontrado: " + id));
    }

    private HabitResponse toResponse(Habit habit) {
        // Filas creadas antes de anadir esta columna podrian tener
        // priority=null en BD (la columna es nullable a proposito).
        Priority priority = habit.getPriority() != null ? habit.getPriority() : Priority.MEDIA;
        return new HabitResponse(habit.getId(), habit.getName(), habit.getDescription(), priority, habit.getCreatedAt());
    }
}
