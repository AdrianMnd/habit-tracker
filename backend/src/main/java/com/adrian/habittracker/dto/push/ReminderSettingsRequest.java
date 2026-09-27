package com.adrian.habittracker.dto.push;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReminderSettingsRequest(
        // null = desactivar el recordatorio. @Min/@Max ignoran los null, asi
        // que no hace falta ningun caso especial para permitirlo.
        @Min(0) @Max(23) Integer hour,
        @NotBlank String timeZone
) {
}