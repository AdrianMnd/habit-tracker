package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.auth.AuthResponse;
import com.adrian.habittracker.dto.auth.LoginRequest;
import com.adrian.habittracker.dto.auth.RegisterRequest;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.EmailAlreadyInUseException;
import com.adrian.habittracker.repository.UserRepository;
import com.adrian.habittracker.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException("Ya existe una cuenta con ese email");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            // Delega en el AuthenticationManager de Spring Security: usa
            // nuestro CustomUserDetailsService + PasswordEncoder por debajo
            // para comparar la contraseña con el hash guardado.
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Email o contraseña incorrectos");
        }

        String token = jwtService.generateToken(request.email());
        return new AuthResponse(token, request.email());
    }
}
