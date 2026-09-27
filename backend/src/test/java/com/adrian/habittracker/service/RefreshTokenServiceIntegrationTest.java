package com.adrian.habittracker.service;

import com.adrian.habittracker.entity.RefreshToken;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.InvalidRefreshTokenException;
import com.adrian.habittracker.repository.RefreshTokenRepository;
import com.adrian.habittracker.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Test de integracion de la rotacion de refresh tokens, contra Postgres real.
 *
 * @Import(RefreshTokenService.class): @DataJpaTest solo crea repositorios,
 * no servicios - importamos a mano el unico servicio que queremos probar.
 *
 * @Transactional(NOT_SUPPORTED): a diferencia de HabitRepositoryIntegrationTest,
 * aqui DESACTIVAMOS la transaccion que @DataJpaTest abre por defecto en cada
 * test. Con ella, todo lo que hiciera el servicio se uniria a esa transaccion
 * del test y nunca se haria commit ni rollback de verdad hasta el final - y
 * justo queremos comprobar que, al detectar una reutilizacion, la revocacion
 * se CONFIRMA en base de datos aunque el metodo termine lanzando una
 * excepcion (el noRollbackFor de RefreshTokenService.rotate). El precio: sin
 * rollback automatico, hay que limpiar las tablas a mano en @AfterEach.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(RefreshTokenService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        User newUser = new User();
        newUser.setEmail("adrian@example.com");
        newUser.setPasswordHash("hash-de-prueba");
        user = userRepository.save(newUser);
    }

    @AfterEach
    void cleanUp() {
        // Orden importante: primero los tokens (tienen FK hacia users).
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rotarRevocaElTokenUsadoYDevuelveUnoNuevoQueSiFunciona() {
        String original = refreshTokenService.issue(user);

        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(original);

        assertThat(rotation.user().getEmail()).isEqualTo("adrian@example.com");
        assertThat(rotation.newRefreshToken()).isNotEqualTo(original);
        assertThat(find(original).getRevokedAt()).isNotNull();
        assertThat(find(rotation.newRefreshToken()).getRevokedAt()).isNull();
    }

    @Test
    void reutilizarUnTokenYaRotadoRevocaTodasLasSesionesDelUsuario() {
        String sesionMovil = refreshTokenService.issue(user);
        String sesionPortatil = refreshTokenService.issue(user);
        String sesionMovilRotada = refreshTokenService.rotate(sesionMovil).newRefreshToken();

        // Alguien vuelve a presentar el token del movil que ya se consumio.
        assertThatThrownBy(() -> refreshTokenService.rotate(sesionMovil))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("reutilizado");

        // Las revocaciones se han CONFIRMADO en la base de datos pese a la
        // excepcion: si quitaras el noRollbackFor de rotate(), estas dos
        // aserciones fallarian (el rollback desharia la revocacion masiva).
        assertThat(find(sesionMovilRotada).getRevokedAt()).isNotNull();
        assertThat(find(sesionPortatil).getRevokedAt()).isNotNull();
    }

    @Test
    void unTokenCaducadoSeRechaza() {
        String token = refreshTokenService.issue(user);
        RefreshToken stored = find(token);
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(stored);

        assertThatThrownBy(() -> refreshTokenService.rotate(token))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("caducado");
    }

    @Test
    void unTokenQueNuncaSeEmitioSeRechaza() {
        assertThatThrownBy(() -> refreshTokenService.rotate("token-inventado"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void enBaseDeDatosSoloSeGuardaElHashNuncaElTokenEnClaro() {
        String token = refreshTokenService.issue(user);

        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getTokenHash)
                .containsExactly(RefreshTokenService.hash(token))
                .doesNotContain(token);
    }

    private RefreshToken find(String rawToken) {
        return refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(rawToken)).orElseThrow();
    }
}