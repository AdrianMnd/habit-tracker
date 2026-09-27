package com.adrian.habittracker.dto.push;

import jakarta.validation.constraints.NotBlank;

public record UnsubscribeRequest(@NotBlank String endpoint) {
}