package com.adrian.habittracker.dto;

public record HabitStreakSummary(
        Long habitId,
        String habitName,
        int currentStreak
) {
}
