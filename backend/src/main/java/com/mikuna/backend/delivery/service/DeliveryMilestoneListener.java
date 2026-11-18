package com.mikuna.backend.delivery.service;

import com.mikuna.backend.delivery.model.DeliveryMilestone;
import com.mikuna.backend.delivery.model.MilestoneType;
import com.mikuna.backend.delivery.repository.CourierLocationRepository;
import com.mikuna.backend.delivery.repository.DeliveryMilestoneRepository;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChangedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registra de forma permanente la ubicación al recoger y al entregar, y descarta la ubicación vigente cuando la
 * entrega termina o se libera (FR-006). Corre en la misma transacción que el cambio de estado.
 */
@Component
public class DeliveryMilestoneListener {

    private final CourierLocationRepository locations;
    private final DeliveryMilestoneRepository milestones;
    private final Clock clock;

    public DeliveryMilestoneListener(CourierLocationRepository locations, DeliveryMilestoneRepository milestones,
            Clock clock) {
        this.locations = locations;
        this.milestones = milestones;
        this.clock = clock;
    }

    @EventListener
    public void onStatusChanged(OrderStatusChangedEvent event) {
        if (event.to() == OrderStatus.PICKED_UP) {
            record(event, MilestoneType.PICKUP);
        } else if (event.to() == OrderStatus.DELIVERED) {
            record(event, MilestoneType.DELIVERY);
            locations.deleteByOrderId(event.orderId());
        } else if (event.to() == OrderStatus.CANCELLED
                || (event.from() == OrderStatus.ASSIGNED && event.to() == OrderStatus.READY)) {
            locations.deleteByOrderId(event.orderId());
        }
    }

    /** Copia la ubicación vigente del repartidor que actúa; sin ubicación conocida no se inventa ningún hito. */
    private void record(OrderStatusChangedEvent event, MilestoneType type) {
        if (event.actorId() == null || milestones.existsByOrderIdAndType(event.orderId(), type)) {
            return;
        }
        locations.findByCourierIdAndOrderId(event.actorId(), event.orderId()).ifPresent(location ->
                milestones.save(new DeliveryMilestone(event.orderId(), type, location.getLatitude(),
                        location.getLongitude(), location.getRecordedAt(), Instant.now(clock))));
    }
}
