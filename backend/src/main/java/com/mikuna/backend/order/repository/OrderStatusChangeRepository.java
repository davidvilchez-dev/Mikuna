package com.mikuna.backend.order.repository;

import com.mikuna.backend.order.model.OrderStatusChange;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusChangeRepository extends JpaRepository<OrderStatusChange, UUID> {

    List<OrderStatusChange> findByOrderIdOrderByChangedAtAsc(UUID orderId);

    /** Último cambio del pedido: distingue un Cancelado por vencimiento (spec 012). */
    Optional<OrderStatusChange> findFirstByOrderIdOrderByChangedAtDesc(UUID orderId);
}
