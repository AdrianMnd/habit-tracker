package com.adrian.habittracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Un Clock como bean en vez de llamar a Instant.now() directamente: en
 * produccion es el reloj real, pero en un test se puede sustituir por
 * Clock.fixed(...) y probar "que pasa a las 21:30 en Madrid" sin esperar a
 * que sean las 21:30. La hora actual es una dependencia mas, como un
 * repositorio - y como tal, se inyecta.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}