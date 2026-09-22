package com.adrian.habittracker.dto;

public record StreakResponse(
        Long habitId,
        int currentStreak,
        double weeklyCompletionRate // 0.0 a 1.0, sobre los ultimos 7 dias
) {
}
