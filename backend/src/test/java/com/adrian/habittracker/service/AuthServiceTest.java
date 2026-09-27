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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerRechazaUnEmailQueYaExiste() {
        when(userRepository.existsByEmail("adrian@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("adrian@example.com", "password123");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyInUseException.class);

        // Si ya existe, no debe llegar a guardarse un segundo usuario con
        // ese email - comprobamos el efecto, no solo la excepcion.
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerGuardaLaContraseñaHasheadaNuncaEnTextoPlano() {
        when(userRepository.existsByEmail("adrian@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(jwtService.generateToken("adrian@example.com")).thenReturn("fake-jwt");
        when(refreshTokenService.issue(any(User.class))).thenReturn("fake-refresh");

        RegisterRequest request = new RegisterRequest("adrian@example.com", "password123");
        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("fake-jwt");
        assertThat(response.email()).isEqualTo("adrian@example.com");
        assertThat(response.refreshToken()).isEqualTo("fake-refresh");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // La propia esencia de bcrypt/PasswordEncoder: nunca debe llegar
        // a persistirse "password123" tal cual, solo su hash.
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
    }

       @Test
    void loginConCredencialesValidasDevuelveAccessYRefreshToken() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("adrian@example.com");
        when(userRepository.findByEmail("adrian@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("adrian@example.com")).thenReturn("fake-jwt");
        when(refreshTokenService.issue(user)).thenReturn("fake-refresh");

        AuthResponse response = authService.login(new LoginRequest("adrian@example.com", "password123"));

        assertThat(response.token()).isEqualTo("fake-jwt");
        assertThat(response.refreshToken()).isEqualTo("fake-refresh");
        assertThat(response.email()).isEqualTo("adrian@example.com");
    }

    @Test
    void refreshDevuelveUnAccessTokenNuevoYElRefreshTokenRotado() {
        User user = new User();
        user.setEmail("adrian@example.com");
        when(refreshTokenService.rotate("refresh-viejo"))
                .thenReturn(new RefreshTokenService.Rotation(user, "refresh-nuevo"));
        when(jwtService.generateToken("adrian@example.com")).thenReturn("jwt-nuevo");

        AuthResponse response = authService.refresh("refresh-viejo");

        assertThat(response.token()).isEqualTo("jwt-nuevo");
        assertThat(response.refreshToken()).isEqualTo("refresh-nuevo");
    }

    @Test
    void loginConCredencialesInvalidasLanzaBadCredentialsConMensajePropio() {
        // authenticationManager.authenticate() es quien de verdad compara
        // la contraseña (via CustomUserDetailsService + PasswordEncoder
        // por debajo) - aqui solo simulamos que ese proceso rechaza al
        // usuario, sin entrar en como lo hace.
        when(authenticationManager.authenticate(any())).thenThrow(new AuthenticationException("bad creds") {});

        assertThatThrownBy(() -> authService.login(new LoginRequest("adrian@example.com", "incorrecta")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos");
    }

    @Test
    void changePasswordFallaSiElUsuarioNoExiste() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(USER_ID, new ChangePasswordRequest("actual123", "nueva12345")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changePasswordFallaSiLaContraseñaActualNoCoincide() {
        User user = new User();
        user.setId(USER_ID);
        user.setPasswordHash("$2a$10$hashActual");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("incorrecta", "$2a$10$hashActual")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(USER_ID, new ChangePasswordRequest("incorrecta", "nueva12345")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordActualizaElHashCuandoLaActualEsCorrecta() {
        User user = new User();
        user.setId(USER_ID);
        user.setPasswordHash("$2a$10$hashViejo");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("actual123", "$2a$10$hashViejo")).thenReturn(true);
        when(passwordEncoder.encode("nueva12345")).thenReturn("$2a$10$hashNuevo");

        authService.changePassword(USER_ID, new ChangePasswordRequest("actual123", "nueva12345"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
        verify(userRepository).save(user);
    }
}
