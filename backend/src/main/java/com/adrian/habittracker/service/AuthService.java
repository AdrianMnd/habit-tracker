package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.auth.AuthResponse;
import com.adrian.habittracker.dto.auth.ChangePasswordRequest;
import com.adrian.habittracker.dto.auth.LoginRequest;
import com.adrian.habittracker.dto.auth.RegisterRequest;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.EmailAlreadyInUseException;
import com.adrian.habittracker.exception.ResourceNotFoundException;
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
import com.adrian.habittracker.service.RefreshTokenService.Rotation;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException("Ya existe una cuenta con ese email");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        return buildAuthResponse(user);
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

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Email o contraseña incorrectos"));
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        Rotation rotation = refreshTokenService.rotate(refreshToken);
        String email = rotation.user().getEmail();
        return new AuthResponse(jwtService.generateToken(email), rotation.newRefreshToken(), email);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken, user.getEmail());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("La contraseña actual no es correcta");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
