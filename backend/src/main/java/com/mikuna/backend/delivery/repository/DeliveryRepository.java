package com.mikuna.backend.delivery.repository;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.order.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    Optional<Delivery> findByOrderId(UUID orderId);

    /**
     * Asignación condicional (RNF-05): solo prospera si la entrega sigue sin repartidor. Devuelve las filas
     * afectadas; si no es 1, otra solicitud ya la asignó.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Delivery d set d.courierId = :courierId, d.assignedAt = :now "
            + "where d.orderId = :orderId and d.courierId is null")
    int assignIfFree(@Param("orderId") UUID orderId, @Param("courierId") UUID courierId,
            @Param("now") Instant now);

    /** Entregas sin repartidor cuyo pedido está Listo para recoger, las más antiguas primero. */
    @Query("select d from Delivery d where d.courierId is null and exists "
            + "(select 1 from Order o where o.id = d.orderId and o.status = :status) order by d.readyAt, d.id")
    List<Delivery> findPending(@Param("status") OrderStatus status);

    /** Entregas Asignadas cuyo repartidor tiene la cuenta inhabilitada (FR-010a). */
    @Query("select d from Delivery d where d.courierId is not null "
            + "and exists (select 1 from Order o where o.id = d.orderId and o.status = :status) "
            + "and exists (select 1 from Account a where a.id = d.courierId and a.status = 'DISABLED') "
            + "order by d.readyAt, d.id")
    List<Delivery> findAssignedToDisabledCourier(@Param("status") OrderStatus status);

    /** Entregas activas del repartidor: su pedido está Asignado o Recogido. */
    @Query("select d from Delivery d where d.courierId = :courierId and exists "
            + "(select 1 from Order o where o.id = d.orderId and o.status in :statuses) order by d.assignedAt")
    List<Delivery> findActiveByCourier(@Param("courierId") UUID courierId,
            @Param("statuses") List<OrderStatus> statuses);
}
