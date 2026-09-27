package com.adrian.habittracker.repository;

import com.adrian.habittracker.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    // Query derivada: Spring Data traduce "UserId" a la ruta user.id.
    List<PushSubscription> findByUserId(Long userId);

    long countByUserId(Long userId);

    // Filtrar tambien por usuario: nadie puede dar de baja la suscripcion de
    // otro aunque conozca su endpoint (misma idea que findByIdAndUserId).
    @Transactional
    @Modifying
    @Query("DELETE FROM PushSubscription ps WHERE ps.endpoint = :endpoint AND ps.user.id = :userId")
    int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") Long userId);
}