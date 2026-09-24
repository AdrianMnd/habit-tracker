package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.*;
import com.adrian.habittracker.entity.Category;
import com.adrian.habittracker.entity.Habit;
import com.adrian.habittracker.entity.HabitLog;
import com.adrian.habittracker.entity.Priority;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.ResourceNotFoundException;
import com.adrian.habittracker.repository.CategoryRepository;
import com.adrian.habittracker.repository.HabitLogRepository;
import com.adrian.habittracker.repository.HabitRepository;
import com.adrian.habittracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private final CategoryRepository categoryRepository;

    public List<HabitResponse> findAll(Long userId) {
        return findAll(userId, null);
    }

    public List<HabitResponse> findAll(Long userId, Long categoryId) {
        List<Habit> habits = categoryId != null
                ? habitRepository.findByUserIdAndCategoryId(userId, categoryId)
                : habitRepository.findByUserId(userId);
        return habits.stream().map(this::toResponse).toList();
    }

    public HabitResponse findById(Long id, Long userId) {
        return toResponse(getOwnedHabitOrThrow(id, userId));
    }

    public List<HabitResponse> findArchived(Long userId) {
        return habitRepository.findArchivedByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void setArchived(Long id, Long userId, boolean archived) {
        Habit habit = getOwnedHabitOrThrow(id, userId);
        habit.setArchived(archived);
        // No hace falta llamar a habitRepository.save(habit) aqui: dentro
        // de un metodo @Transactional, una entidad que ya viene de una
        // consulta (getOwnedHabitOrThrow) esta "gestionada" (managed) por
        // Hibernate. Cualquier cambio sobre sus campos se detecta solo
        // ("dirty checking") y se vuelca a la BD al confirmar la
        // transaccion, sin necesidad de guardar explicitamente. Si ves
        // save() en otros metodos de esta clase (create/update) es por
        // estilo/claridad, no porque sea estrictamente necesario ahi
        // tampoco - pero aqui lo dejo fuera a proposito para que veas
        // que existe esta alternativa.
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
        habit.setCategory(resolveCategory(request.categoryId(), userId));
        return toResponse(habitRepository.save(habit));
    }

    @Transactional
    public HabitResponse update(Long id, Long userId, HabitRequest request) {
        Habit habit = getOwnedHabitOrThrow(id, userId);
        habit.setName(request.name());
        habit.setDescription(request.description());
        habit.setPriority(request.priority() != null ? request.priority() : Priority.MEDIA);
        habit.setCategory(resolveCategory(request.categoryId(), userId));
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

    /**
     * Resumen agregado para el dashboard: racha media y % de cumplimiento
     * semanal medio a traves de TODOS los habitos del usuario, en una sola
     * llamada (reutiliza los mismos helpers privados que calculateStreak,
     * asi que la logica de calculo vive en un solo sitio).
     */
    public HabitsSummaryResponse getSummary(Long userId) {
        List<Habit> habits = habitRepository.findByUserId(userId);
        if (habits.isEmpty()) {
            return new HabitsSummaryResponse(0, 0, 0);
        }

        double totalStreak = 0;
        double totalWeeklyRate = 0;

        for (Habit habit : habits) {
            List<HabitLog> logs = habitLogRepository.findByHabitIdOrderByLogDateDesc(habit.getId());
            Map<LocalDate, Boolean> completionByDate = logs.stream()
                    .collect(Collectors.toMap(HabitLog::getLogDate, HabitLog::isCompleted, (a, b) -> a));

            totalStreak += computeCurrentStreak(completionByDate);
            totalWeeklyRate += computeWeeklyCompletionRate(completionByDate);
        }

        int count = habits.size();
        return new HabitsSummaryResponse(count, totalStreak / count, totalWeeklyRate / count);
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

    /**
     * Tendencia de cumplimiento medio (a traves de todos los habitos
     * activos) semana a semana, de mas antigua a mas reciente - pensada
     * para dibujar directamente como grafico de lineas.
     */
    public List<WeeklyProgressPoint> getWeeklyProgress(Long userId, int weeksBack) {
        List<Habit> habits = habitRepository.findByUserId(userId);
        LocalDate currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY);

        List<WeeklyProgressPoint> points = new ArrayList<>();
        for (int i = weeksBack - 1; i >= 0; i--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(i);
            points.add(new WeeklyProgressPoint(weekStart, averageCompletionRateForWeek(habits, weekStart)));
        }
        return points;
    }

    private double averageCompletionRateForWeek(List<Habit> habits, LocalDate weekStart) {
        if (habits.isEmpty()) {
            return 0;
        }

        LocalDate weekEnd = weekStart.plusDays(6);
        double totalRate = 0;

        for (Habit habit : habits) {
            List<HabitLog> logs = habitLogRepository.findByHabitIdAndLogDateBetween(habit.getId(), weekStart, weekEnd);
            Map<LocalDate, Boolean> completionByDate = logs.stream()
                    .collect(Collectors.toMap(HabitLog::getLogDate, HabitLog::isCompleted, (a, b) -> a));
            totalRate += computeCompletionRateForRange(completionByDate, weekStart, weekEnd);
        }

        return totalRate / habits.size();
    }

    private double computeCompletionRateForRange(Map<LocalDate, Boolean> completionByDate, LocalDate start, LocalDate end) {
        long totalDays = 0;
        long completedDays = 0;

        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            totalDays++;
            if (Boolean.TRUE.equals(completionByDate.get(day))) {
                completedDays++;
            }
        }
        return completedDays / (double) totalDays;
    }

    /**
     * Racha actual de cada habito activo, pensada para un grafico de
     * barras comparativo.
     */
    public List<HabitStreakSummary> getStreakSummaries(Long userId) {
        List<Habit> habits = habitRepository.findByUserId(userId);

        return habits.stream().map(habit -> {
            List<HabitLog> logs = habitLogRepository.findByHabitIdOrderByLogDateDesc(habit.getId());
            Map<LocalDate, Boolean> completionByDate = logs.stream()
                    .collect(Collectors.toMap(HabitLog::getLogDate, HabitLog::isCompleted, (a, b) -> a));
            int streak = computeCurrentStreak(completionByDate);
            return new HabitStreakSummary(habit.getId(), habit.getName(), streak);
        }).toList();
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

    /**
     * null es un valor valido (habito sin categoria); si viene un id que
     * no existe o no es tuyo, falla alto y claro en vez de guardarlo con
     * una referencia rota en silencio.
     */
    private Category resolveCategory(Long categoryId, Long userId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada: " + categoryId));
    }

    private HabitResponse toResponse(Habit habit) {
        // Filas creadas antes de anadir esta columna podrian tener
        // priority=null en BD (la columna es nullable a proposito).
        Priority priority = habit.getPriority() != null ? habit.getPriority() : Priority.MEDIA;

        CategoryResponse category = habit.getCategory() != null
                ? new CategoryResponse(habit.getCategory().getId(), habit.getCategory().getName())
                : null;

        return new HabitResponse(habit.getId(), habit.getName(), habit.getDescription(), priority, category, habit.getCreatedAt());
    }
}
