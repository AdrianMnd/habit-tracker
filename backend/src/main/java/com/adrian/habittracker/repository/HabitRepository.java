package com.adrian.habittracker.repository;

import com.adrian.habittracker.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    // LEFT JOIN FETCH: trae la categoria (si existe) en la MISMA consulta,
    // no en una posterior. Es necesario porque con spring.jpa.open-in-view
    // en false, la sesion de Hibernate se cierra al salir de este
    // repositorio - si toResponse() intentara leer habit.getCategory()
    // (una relacion LAZY) mas tarde, fuera de una transaccion activa,
    // lanzaria LazyInitializationException. "LEFT" (no INNER) es
    // imprescindible: un habito sin categoria (category_id es NULL)
    // tiene que seguir apareciendo en el resultado.
    @Query("SELECT h FROM Habit h LEFT JOIN FETCH h.category WHERE h.user.id = :userId")
    List<Habit> findByUserId(@Param("userId") Long userId);

    @Query("SELECT h FROM Habit h LEFT JOIN FETCH h.category " +
           "WHERE h.user.id = :userId AND h.category.id = :categoryId")
    List<Habit> findByUserIdAndCategoryId(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

    // Filtrar por userId aqui, no solo por id, es lo que impide que un
    // usuario acceda/edite/borre habitos de otro adivinando su id numerico
    // (proteccion basica contra IDOR - Insecure Direct Object Reference).
    @Query("SELECT h FROM Habit h LEFT JOIN FETCH h.category WHERE h.id = :id AND h.user.id = :userId")
    Optional<Habit> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // Se ejecuta al borrar una categoria que todavia tiene habitos
    // asignados: los deja sin categoria en vez de dejar una fila
    // "huerfana" con un category_id que ya no existe en la BD.
    @Modifying
    @Query("UPDATE Habit h SET h.category = null WHERE h.category.id = :categoryId")
    void clearCategoryReferences(@Param("categoryId") Long categoryId);
}
