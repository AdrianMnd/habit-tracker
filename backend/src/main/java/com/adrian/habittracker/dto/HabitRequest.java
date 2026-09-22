package com.adrian.habittracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HabitRequest(
        @NotBlank(message = "El nombre del habito es obligatorio")
        @Size(max = 100)
        String name,

        @Size(max = 255)
        String description
) {
}
