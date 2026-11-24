package com.mikuna.backend.order.repository;

import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<Order> findByCustomerIdAndConfirmationKey(UUID customerId, String confirmationKey);

    /** Bloquea la fila del pedido: aceptar, rechazar y cancelar se serializan y solo una prospera (research §1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtAsc(UUID restaurantId, OrderStatus status);

    /** Pedidos en {@code status} cuyo plazo terminó, los más antiguos primero (barrido de la spec 012). */
    @Query("select o.id from Order o where o.status = :status and o.expiresAt <= :now order by o.expiresAt")
    List<UUID> findExpiredIds(@Param("status") OrderStatus status, @Param("now") Instant now, Pageable page);

    /** Fija el vencimiento de los pedidos anteriores a la spec 012 (creación + plazo vigente). */
    @Modifying
    @Query(value = "update pedidos set expires_at = created_at + make_interval(secs => :seconds) "
            + "where expires_at is null", nativeQuery = true)
    int backfillExpiresAt(@Param("seconds") double seconds);
}
