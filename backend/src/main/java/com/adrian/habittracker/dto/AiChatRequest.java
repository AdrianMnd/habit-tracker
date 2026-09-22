package com.adrian.habittracker.dto;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
        @NotBlank(message = "El mensaje no puede estar vacio")
        String message
) {
}
