package com.mikuna.backend.delivery.service;

import com.mikuna.backend.common.exception.LocationRejectedException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.delivery.dto.LocationRequest;
import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.CourierLocationRepository;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.model.OrderStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ubicación vigente del repartidor (RF-26). Solo se acepta con una entrega en curso (Asignada o Recogida); el
 * servidor decide la entrega y conserva únicamente la ubicación más reciente, sin historial (FR-003 a FR-006).
 */
@Service
public class LocationService {

    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(1);
    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP);

    private final DeliveryRepository deliveries;
    private final CourierLocationRepository locations;
    private final Clock clock;

    public LocationService(DeliveryRepository deliveries, CourierLocationRepository locations, Clock clock) {
        this.deliveries = deliveries;
        this.locations = locations;
        this.clock = clock;
    }

    /** Guarda la ubicación; una más antigua que la vigente se ignora sin error. */
    @Transactional
    public void update(UUID courierId, LocationRequest request) {
        if (request.recordedAt().isAfter(Instant.now(clock).plus(FUTURE_TOLERANCE))) {
            throw new RegistrationValidationException("recordedAt", "La hora de la ubicación no puede ser futura");
        }
        Delivery delivery = deliveries.findActiveByCourier(courierId, ACTIVE).stream().findFirst()
                .orElseThrow(LocationRejectedException::new);
        locations.upsert(courierId, delivery.getOrderId(), request.latitude(), request.longitude(),
                request.recordedAt());
    }
}
