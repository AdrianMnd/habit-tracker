package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.*;
import com.adrian.habittracker.security.UserPrincipal;
import com.adrian.habittracker.service.HabitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
public class HabitController {

    private final HabitService habitService;

    @GetMapping
    public List<HabitResponse> findAll(@RequestParam(required = false) Long categoryId,
                                        @AuthenticationPrincipal UserPrincipal currentUser) {
        return habitService.findAll(currentUser.getId(), categoryId);
    }

    @GetMapping("/{id}")
    public HabitResponse findById(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        return habitService.findById(id, currentUser.getId());
    }

    @GetMapping("/archived")
    public List<HabitResponse> archived(@AuthenticationPrincipal UserPrincipal currentUser) {
        return habitService.findArchived(currentUser.getId());
    }

    // PATCH, no PUT ni POST: modificamos UN campo concreto (archived) de
    // un recurso existente, no lo reemplazamos entero (eso seria PUT) ni
    // creamos uno nuevo (POST). Es la primera vez que usamos PATCH aqui -
    // el verbo HTTP pensado exactamente para actualizaciones parciales.
    @PatchMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        habitService.setArchived(id, currentUser.getId(), true);
    }

    @PatchMapping("/{id}/unarchive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unarchive(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        habitService.setArchived(id, currentUser.getId(), false);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitResponse create(@Valid @RequestBody HabitRequest request,
                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        return habitService.create(currentUser.getId(), request);
    }

    @PutMapping("/{id}")
    public HabitResponse update(@PathVariable Long id, @Valid @RequestBody HabitRequest request,
                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        return habitService.update(id, currentUser.getId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        habitService.delete(id, currentUser.getId());
    }

    @PostMapping("/{id}/logs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logCompletion(@PathVariable Long id, @Valid @RequestBody HabitLogRequest request,
                               @AuthenticationPrincipal UserPrincipal currentUser) {
        habitService.logCompletion(id, currentUser.getId(), request);
    }

    @GetMapping("/{id}/streak")
    public StreakResponse streak(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        return habitService.calculateStreak(id, currentUser.getId());
    }

    /**
     * Sin parametro "start", devuelve la semana actual (lunes a domingo).
     * Spring resuelve "/week" como ruta literal antes que "/{id}" como
     * variable, asi que no hay ambiguedad con findById.
     */
    @GetMapping("/summary")
    public HabitsSummaryResponse summary(@AuthenticationPrincipal UserPrincipal currentUser) {
        return habitService.getSummary(currentUser.getId());
    }

    @GetMapping("/week")
    public List<HabitWeekEntry> weekView(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        LocalDate weekStart = start != null ? start : LocalDate.now().with(DayOfWeek.MONDAY);
        return habitService.getWeekView(currentUser.getId(), weekStart);
    }
}
