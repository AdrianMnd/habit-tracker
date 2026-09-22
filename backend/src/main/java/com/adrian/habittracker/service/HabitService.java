package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.*;
import com.adrian.habittracker.entity.Habit;
import com.adrian.habittracker.entity.HabitLog;
import com.adrian.habittracker.exception.ResourceNotFoundException;
import com.adrian.habittracker.repository.HabitLogRepository;
import com.adrian.habittracker.repository.HabitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;

    public List<HabitResponse> findAll() {
        return habitRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public HabitResponse findById(Long id) {
        return toResponse(getHabitOrThrow(id));
    }

    @Transactional
    public HabitResponse create(HabitRequest request) {
        Habit habit = new Habit();
        habit.setName(request.name());
        habit.setDescription(request.description());
        return toResponse(habitRepository.save(habit));
    }

    @Transactional
    public HabitResponse update(Long id, HabitRequest request) {
        Habit habit = getHabitOrThrow(id);
        habit.setName(request.name());
        habit.setDescription(request.description());
        return toResponse(habitRepository.save(habit));
    }

    @Transactional
    public void delete(Long id) {
        Habit habit = getHabitOrThrow(id);
        habitRepository.delete(habit);
    }

    /**
     * Marca (o desmarca) el cumplimiento de un habito en una fecha concreta.
     * Si ya existe un log para esa fecha, lo actualiza; si no, lo crea.
     */
    @Transactional
    public void logCompletion(Long habitId, HabitLogRequest request) {
        Habit habit = getHabitOrThrow(habitId);

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
     * Calcula la racha actual (dias consecutivos cumplidos hasta hoy,
     * contando hacia atras) y el porcentaje de cumplimiento de los
     * ultimos 7 dias.
     */
    public StreakResponse calculateStreak(Long habitId) {
        getHabitOrThrow(habitId); // valida que exista

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

    private Habit getHabitOrThrow(Long id) {
        return habitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Habito no encontrado: " + id));
    }

    private HabitResponse toResponse(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getDescription(), habit.getCreatedAt());
    }
}
