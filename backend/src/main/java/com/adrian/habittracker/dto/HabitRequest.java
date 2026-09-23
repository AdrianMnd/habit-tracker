package com.adrian.habittracker.dto;

import com.adrian.habittracker.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HabitRequest(
        @NotBlank(message = "El nombre del habito es obligatorio")
        @Size(max = 100)
        String name,

        @Size(max = 255)
        String description,

        // Opcional: si no se manda, el servicio usa Priority.MEDIA por defecto.
        Priority priority,

        // Opcional: null significa "sin categoria".
        Long categoryId
) {
}
