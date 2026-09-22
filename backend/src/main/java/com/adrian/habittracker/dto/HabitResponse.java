package com.adrian.habittracker.dto;

import java.time.Instant;

public record HabitResponse(
        Long id,
        String name,
        String description,
        Instant createdAt
) {
}
