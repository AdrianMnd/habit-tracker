package com.adrian.habittracker.dto.push;

public record ReminderRunResponse(int usersWithReminder, int usersNotified, int notificationsDelivered) {
}