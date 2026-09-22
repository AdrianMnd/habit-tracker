package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.*;
import com.adrian.habittracker.service.HabitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
public class HabitController {

    private final HabitService habitService;

    @GetMapping
    public List<HabitResponse> findAll() {
        return habitService.findAll();
    }

    @GetMapping("/{id}")
    public HabitResponse findById(@PathVariable Long id) {
        return habitService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitResponse create(@Valid @RequestBody HabitRequest request) {
        return habitService.create(request);
    }

    @PutMapping("/{id}")
    public HabitResponse update(@PathVariable Long id, @Valid @RequestBody HabitRequest request) {
        return habitService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        habitService.delete(id);
    }

    @PostMapping("/{id}/logs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logCompletion(@PathVariable Long id, @Valid @RequestBody HabitLogRequest request) {
        habitService.logCompletion(id, request);
    }

    @GetMapping("/{id}/streak")
    public StreakResponse streak(@PathVariable Long id) {
        return habitService.calculateStreak(id);
    }
}
