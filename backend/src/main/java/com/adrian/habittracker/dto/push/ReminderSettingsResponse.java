package com.adrian.habittracker.dto.push;

public record ReminderSettingsResponse(
        Integer hour,
        String timeZone,
        long subscribedDevices
) {
}