package com.mikuna.backend.order.repository;

import com.mikuna.backend.order.model.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<Order> findByCustomerIdAndConfirmationKey(UUID customerId, String confirmationKey);
}
