package com.adrian.habittracker.dto.auth;

public record AuthResponse(
        String token,
        String refreshToken,
        String email
) {
}