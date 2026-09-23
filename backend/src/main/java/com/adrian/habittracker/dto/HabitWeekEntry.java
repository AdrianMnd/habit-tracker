package com.adrian.habittracker.dto;

import java.time.LocalDate;
import java.util.Map;

public record HabitWeekEntry(
        Long habitId,
        String habitName,
        Map<LocalDate, Boolean> days
) {
}
