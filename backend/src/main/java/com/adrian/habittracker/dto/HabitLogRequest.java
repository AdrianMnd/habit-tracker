package com.adrian.habittracker.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HabitLogRequest(
        @NotNull
        LocalDate logDate,

        @NotNull
        Boolean completed
) {
}
