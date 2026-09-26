package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.auth.AuthResponse;
import com.adrian.habittracker.dto.auth.LoginRequest;
import com.adrian.habittracker.dto.auth.RegisterRequest;
import com.adrian.habittracker.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.adrian.habittracker.dto.auth.RefreshRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // Publico (cae bajo /api/auth/**, permitAll en SecurityConfig) a
    // proposito: se llama justo cuando el access token ya ha caducado, asi
    // que no puede exigir uno valido. La "credencial" aqui es el propio
    // refresh token del body.
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
