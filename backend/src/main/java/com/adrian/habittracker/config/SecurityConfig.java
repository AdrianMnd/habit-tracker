package com.adrian.habittracker.config;

import com.adrian.habittracker.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Sin esto, Spring Security no sabe como "reaccionar" ante una peticion
     * sin autenticar valida en un setup JWT sin formulario de login ni auth
     * basica - y por defecto acaba devolviendo 403 (Forbidden, "se quien
     * eres pero no puedes") incluso cuando el problema real es que no sabe
     * quien eres (401, Unauthorized). Un token ausente, mal formado o
     * caducado son todos casos de "no autenticado": deben ser 401.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":401,"error":"Unauthorized","message":"Token ausente, invalido o caducado"}""");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API sin estado (JWT en cada peticion): no hace falta CSRF,
                // que protege sesiones basadas en cookies, algo que no usamos.
                .csrf(csrf -> csrf.disable())
                // Usa el bean CorsConfigurationSource definido en CorsConfig.
                .cors(Customizer.withDefaults())
                // No guardamos sesion en el servidor: cada peticion se
                // autentica sola con su propio token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Los endpoints que devuelven Flux (streaming) provocan un
                // "redespacho" async interno de Servlet cuando el Flux
                // termina, que vuelve a pasar por toda la cadena de Spring
                // Security. Sin esto, ese segundo pase no reconoce al
                // usuario autenticado en el primer pase y corta la
                // conexion en seco a mitad del streaming. Guardar el
                // contexto como atributo de la request (en vez del
                // repositorio "vacio" por defecto) y desactivar el guardado
                // explicito hace que sobreviva automaticamente a ese
                // redespacho.
                .securityContext(context -> context
                        .securityContextRepository(new RequestAttributeSecurityContextRepository())
                        .requireExplicitSave(false)
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
