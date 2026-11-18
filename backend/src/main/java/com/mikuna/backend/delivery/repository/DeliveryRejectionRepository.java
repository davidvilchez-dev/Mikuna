package com.mikuna.backend.delivery.repository;

import com.mikuna.backend.delivery.model.DeliveryRejection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRejectionRepository extends JpaRepository<DeliveryRejection, UUID> {
}
