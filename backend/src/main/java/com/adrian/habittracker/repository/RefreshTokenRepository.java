package com.adrian.habittracker.repository;

import com.adrian.habittracker.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // JOIN FETCH del usuario: tras rotar necesitamos su email para firmar el
    // nuevo access token, y con open-in-view=false no podemos contar con
    // cargar la relacion LAZY despues (mismo motivo que en HabitRepository).
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    // Una sola UPDATE en SQL en vez de cargar todos los tokens y cambiarlos
    // uno a uno en Java. Devuelve cuantas filas se revocaron.
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}