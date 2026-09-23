package com.adrian.habittracker.dto;

public record HabitsSummaryResponse(
        int activeHabits,
        double averageStreak,
        double averageWeeklyCompletionRate
) {
}
