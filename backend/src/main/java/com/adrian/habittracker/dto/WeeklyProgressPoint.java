package com.adrian.habittracker.dto;

import java.time.LocalDate;

public record WeeklyProgressPoint(
        LocalDate weekStart,
        double averageCompletionRate
) {
}
