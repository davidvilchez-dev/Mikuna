package com.mikuna.backend.delivery.repository;

import com.mikuna.backend.delivery.model.CourierAvailability;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourierAvailabilityRepository extends JpaRepository<CourierAvailability, UUID> {

    /**
     * Condiciones de candidato (FR-002, FR-010): disponible, cuenta de repartidor habilitada, sin entrega activa
     * (pedido ASSIGNED o PICKED_UP con su {@code courier_id}) y sin rechazo previo de esa entrega.
     */
    String CANDIDATE = """
            ca.available = true and a.status = 'ENABLED' and a.role = 'COURIER'
            and not exists (select 1 from entregas d join pedidos o on o.id = d.order_id
                            where d.courier_id = ca.courier_id and o.status in ('ASSIGNED', 'PICKED_UP'))
            and not exists (select 1 from rechazos_entrega r
                            where r.courier_id = ca.courier_id and r.delivery_id = :deliveryId)""";

    /** Candidatos en orden: el que lleva más tiempo libre primero. Sin bloqueo: se bloquean y revalidan después. */
    @Query(value = "select ca.courier_id from repartidores_disponibilidad ca "
            + "join cuentas a on a.id = ca.courier_id where " + CANDIDATE
            + " order by ca.free_since asc, ca.courier_id limit 20", nativeQuery = true)
    List<UUID> findCandidateIds(@Param("deliveryId") UUID deliveryId);

    /** Comprobación con sentencia nueva, tras bloquear la fila del repartidor (research §2). */
    @Query(value = "select count(*) from repartidores_disponibilidad ca join cuentas a on a.id = ca.courier_id "
            + "where ca.courier_id = :courierId and " + CANDIDATE, nativeQuery = true)
    long countCandidate(@Param("courierId") UUID courierId, @Param("deliveryId") UUID deliveryId);

    /** Bloquea la fila sin esperar: si otra asignación la tiene, se pasa al siguiente candidato. */
    @Query(value = "select ca.* from repartidores_disponibilidad ca where ca.courier_id = :courierId "
            + "for update skip locked", nativeQuery = true)
    Optional<CourierAvailability> lockSkipLocked(@Param("courierId") UUID courierId);

    /** Bloquea la fila esperando (asignación manual, entrega pendiente a un repartidor concreto, fin de entrega). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierAvailability c where c.courierId = :courierId")
    Optional<CourierAvailability> findByIdForUpdate(@Param("courierId") UUID courierId);
}
