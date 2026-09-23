package com.adrian.habittracker.dto;

import com.adrian.habittracker.entity.Priority;

import java.time.Instant;

public record HabitResponse(
        Long id,
        String name,
        String description,
        Priority priority,
        Instant createdAt
) {
}
