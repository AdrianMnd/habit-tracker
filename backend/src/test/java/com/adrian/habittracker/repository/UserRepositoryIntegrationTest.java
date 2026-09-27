package com.adrian.habittracker.repository;

import com.adrian.habittracker.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * La reclamacion atomica del recordatorio diario, contra Postgres real: lo
 * que importa aqui es el UPDATE ... WHERE de verdad (incluida la comparacion
 * con NULL, que en SQL tiene sus trampas: "NULL <> fecha" no es true, es
 * NULL - de ahi el "IS NULL OR" explicito de la query).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Test
    void elRecordatorioDeUnDiaSoloSePuedeReclamarUnaVez() {
        User user = new User();
        user.setEmail("adrian@example.com");
        user.setPasswordHash("hash-de-prueba");
        user.setReminderHour(21);
        user = userRepository.save(user);

        LocalDate today = LocalDate.of(2026, 9, 27);

        assertThat(userRepository.claimDailyReminder(user.getId(), today)).isEqualTo(1); // nunca enviado (NULL)
        assertThat(userRepository.claimDailyReminder(user.getId(), today)).isEqualTo(0); // ya reclamado hoy
        assertThat(userRepository.claimDailyReminder(user.getId(), today.plusDays(1))).isEqualTo(1); // dia nuevo
    }

    @Test
    void soloDevuelveUsuariosConRecordatorioActivado() {
        User withReminder = new User();
        withReminder.setEmail("con@example.com");
        withReminder.setPasswordHash("hash");
        withReminder.setReminderHour(20);
        userRepository.save(withReminder);

        User without = new User();
        without.setEmail("sin@example.com");
        without.setPasswordHash("hash");
        userRepository.save(without);

        assertThat(userRepository.findByReminderHourIsNotNull())
                .extracting(User::getEmail)
                .containsExactly("con@example.com");
    }
}