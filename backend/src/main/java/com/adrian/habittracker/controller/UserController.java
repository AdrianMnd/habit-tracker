package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.auth.ChangePasswordRequest;
import com.adrian.habittracker.security.UserPrincipal;
import com.adrian.habittracker.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// /api/auth/** es la unica ruta con acceso publico (permitAll en
// SecurityConfig); todo lo demas exige autenticacion por defecto, asi
// que basta con vivir fuera de ese prefijo - no hace falta tocar
// SecurityConfig para que esto quede protegido.
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                @AuthenticationPrincipal UserPrincipal currentUser) {
        authService.changePassword(currentUser.getId(), request);
    }
}
