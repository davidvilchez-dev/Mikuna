package com.mikuna.backend.restaurant.repository;

import com.mikuna.backend.restaurant.model.Restaurant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Optional<Restaurant> findByOwnerId(UUID ownerId);

    /**
     * Restaurantes visibles al público (spec 004): aprobados y con la cuenta habilitada, con su horario
     * en la misma consulta. Que atiendan o no en este momento lo decide {@code OrderEligibility}.
     */
    @Query("""
            select distinct r from Restaurant r left join fetch r.schedule
            where r.status = 'APPROVED'
              and exists (select 1 from Account a where a.id = r.ownerId and a.status = 'ENABLED' and a.operationEnabled = true)""")
    List<Restaurant> findVisible();

    @Query("""
            select distinct r from Restaurant r left join fetch r.schedule
            where r.id = :id and r.status = 'APPROVED'
              and exists (select 1 from Account a where a.id = r.ownerId and a.status = 'ENABLED' and a.operationEnabled = true)""")
    Optional<Restaurant> findVisibleById(@Param("id") UUID id);

    /** El estado llega como texto (valor de RestaurantStatus); de la más antigua a la más reciente. */
    List<Restaurant> findByStatusOrderBySubmittedAtAsc(String status);

    /**
     * Resolución única (constitución IV): la condición sobre el estado de origen va en la propia
     * sentencia, de modo que solo una de varias resoluciones simultáneas afecta una fila.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Restaurant r set r.status = :to, r.rejectionReason = :reason, r.resolvedBy = :adminId,
                r.resolvedAt = :now, r.available = false, r.version = r.version + 1
            where r.id = :id and r.status = :from""")
    int resolve(@Param("id") UUID id, @Param("from") String from, @Param("to") String to,
            @Param("reason") String reason, @Param("adminId") UUID adminId, @Param("now") Instant now);

    /** Reenvío del titular: solo desde REJECTED y solo su propio restaurante. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Restaurant r set r.status = 'PENDING', r.submittedAt = :now, r.rejectionReason = null,
                r.resolvedBy = null, r.resolvedAt = null, r.version = r.version + 1
            where r.id = :id and r.ownerId = :ownerId and r.status = 'REJECTED'""")
    int resubmit(@Param("id") UUID id, @Param("ownerId") UUID ownerId, @Param("now") Instant now);
}
