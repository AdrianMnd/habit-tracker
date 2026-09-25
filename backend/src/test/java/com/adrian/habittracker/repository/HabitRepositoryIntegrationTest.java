package com.adrian.habittracker.repository;

import com.adrian.habittracker.entity.Category;
import com.adrian.habittracker.entity.Habit;
import com.adrian.habittracker.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Tests de INTEGRACION del repositorio de habitos, contra un Postgres real
 * levantado en Docker por Testcontainers - a diferencia de HabitServiceTest
 * (que mockea HabitRepository con Mockito y por tanto nunca ejecuta SQL de
 * verdad), aqui verificamos el propio JPQL de HabitRepository y el
 * comportamiento real de Postgres: constraints, LEFT JOIN FETCH, y la
 * query @Modifying de clearCategoryReferences.
 *
 * @DataJpaTest configura solo la capa JPA (repositorios + EntityManager),
 * no todo el contexto de Spring Boot - arranca mucho mas rapido que
 * @SpringBootTest porque no crea controladores, filtros de seguridad, etc.
 * Por defecto @DataJpaTest sustituye el datasource por una base en memoria
 * (H2); Replace.NONE se lo impide para que use de verdad el datasource que
 * apunta al contenedor de Testcontainers.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class HabitRepositoryIntegrationTest {

    // "static": el contenedor se crea UNA vez para toda la clase (todos los
    // @Test comparten el mismo Postgres arrancado), no uno nuevo por test -
    // arrancar un contenedor Docker tarda varios segundos, hacerlo por cada
    // metodo seria muy lento. Cada test, aun asi, ve la base "limpia" porque
    // @DataJpaTest envuelve cada test en una transaccion que se hace
    // rollback automaticamente al terminar (igual que @Transactional).
    @Container
    @ServiceConnection // Boot 3.1+: autoconfigura spring.datasource.* apuntando
                        // al contenedor. Sin esto haria falta un metodo
                        // @DynamicPropertySource manual registrando url/user/password.
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User owner;
    private User otherUser;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(newUser("owner@example.com"));
        otherUser = userRepository.save(newUser("other@example.com"));
    }

    @Test
    void findByUserIdSoloDevuelveHabitosActivosDelUsuarioIndicado() {
        habitRepository.save(newHabit("Meditar", owner, null, false));
        habitRepository.save(newHabit("Habito archivado", owner, null, true)); // no debe aparecer
        habitRepository.save(newHabit("Habito de otro usuario", otherUser, null, false)); // no debe aparecer

        List<Habit> result = habitRepository.findByUserId(owner.getId());

        assertThat(result).extracting(Habit::getName).containsExactly("Meditar");
    }

    @Test
    void findByUserIdTraeLaCategoriaSinLazyInitializationException() {
        Category salud = categoryRepository.save(newCategory("Salud", owner));
        habitRepository.save(newHabit("Correr", owner, salud, false));

        List<Habit> result = habitRepository.findByUserId(owner.getId());

        // Si el LEFT JOIN FETCH del repositorio no trajera la categoria en la
        // misma consulta, esto lanzaria LazyInitializationException en cuanto
        // open-in-view esta desactivado (como en application.properties) y ya
        // hemos salido de la transaccion de Hibernate - aqui seguimos dentro
        // de la transaccion de @DataJpaTest, pero el punto de este test es
        // documentar/fijar ese comportamiento esperado del JOIN FETCH.
        assertThat(result.get(0).getCategory().getName()).isEqualTo("Salud");
    }

    @Test
    void findByIdAndUserIdNoEncuentraHabitosDeOtroUsuarioAunqueElIdExista() {
        Habit habitDeOtro = habitRepository.save(newHabit("Habito ajeno", otherUser, null, false));

        // Esta es la proteccion IDOR real: el propio HabitRepository ya lo
        // documenta, pero aqui lo comprobamos contra SQL de verdad en vez de
        // fiarnos de que el JPQL este bien escrito.
        Optional<Habit> result = habitRepository.findByIdAndUserId(habitDeOtro.getId(), owner.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void clearCategoryReferencesDejaLosHabitosSinCategoriaEnVezDeBorrarlos() {
        Category trabajo = categoryRepository.save(newCategory("Trabajo", owner));
        Habit habit = habitRepository.save(newHabit("Revisar backlog", owner, trabajo, false));

        habitRepository.clearCategoryReferences(trabajo.getId());
        // Una query @Modifying opera directamente en la base de datos, sin
        // pasar por la cache de primer nivel de Hibernate - hay que
        // refrescar la entidad para ver el cambio reflejado aqui. Sin el
        // clear(), findById devolveria la MISMA instancia cacheada (aun con
        // la categoria) sin llegar a consultar Postgres.
        entityManager.clear();
        Habit reloaded = habitRepository.findById(habit.getId()).orElseThrow();

        assertThat(reloaded.getCategory()).isNull();
    }

    @Test
    void noSePuedenRegistrarDosUsuariosConElMismoEmail() {
        userRepository.save(newUser("duplicado@example.com"));

        // El constraint UNIQUE de la columna email (User.java) es del propio
        // Postgres, no de Java - con HabitRepository mockeado en
        // AuthServiceTest esto nunca se comprueba de verdad, solo que el
        // codigo "hace lo que tocaria" si la base lo rechazara.
        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(newUser("duplicado@example.com"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash-de-prueba");
        return user;
    }

    private Category newCategory(String name, User user) {
        Category category = new Category();
        category.setName(name);
        category.setUser(user);
        return category;
    }

    private Habit newHabit(String name, User user, Category category, boolean archived) {
        Habit habit = new Habit();
        habit.setName(name);
        habit.setUser(user);
        habit.setCategory(category);
        habit.setArchived(archived);
        return habit;
    }
}