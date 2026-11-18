package com.mikuna.backend.delivery.repository;

import com.mikuna.backend.delivery.model.DeliveryMilestone;
import com.mikuna.backend.delivery.model.MilestoneType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryMilestoneRepository extends JpaRepository<DeliveryMilestone, UUID> {

    boolean existsByOrderIdAndType(UUID orderId, MilestoneType type);
}
