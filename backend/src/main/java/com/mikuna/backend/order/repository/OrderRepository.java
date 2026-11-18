package com.mikuna.backend.order.repository;

import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
