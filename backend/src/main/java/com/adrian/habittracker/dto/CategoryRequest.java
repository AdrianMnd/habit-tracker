package com.adrian.habittracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "El nombre de la categoria es obligatorio")
        @Size(max = 50)
        String name
) {
}
