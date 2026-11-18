package com.mikuna.backend.delivery.service;

import com.mikuna.backend.delivery.dto.AvailabilityResponse;
import com.mikuna.backend.delivery.model.CourierAvailability;
import com.mikuna.backend.delivery.repository.CourierAvailabilityRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disponibilidad del repartidor (RF-21). Activarla lo hace candidato y, ya confirmada, se le asigna la entrega
 * pendiente más antigua (FR-004). Desactivarla no toca una entrega ya asignada (US1.5). El repartidor sale del token.
 */
@Service
public class CourierAvailabilityService {

    private final CourierAvailabilityRepository availability;
    private final DeliveryDispatcher dispatcher;
    private final Clock clock;

    public CourierAvailabilityService(CourierAvailabilityRepository availability, DeliveryDispatcher dispatcher,
            Clock clock) {
        this.availability = availability;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse get(UUID courierId) {
        return AvailabilityResponse.of(availability.findById(courierId).orElse(null));
    }

    @Transactional
    public AvailabilityResponse set(UUID courierId, boolean available) {
        Instant now = Instant.now(clock);
        CourierAvailability row = availability.findByIdForUpdate(courierId)
                .orElseGet(() -> new CourierAvailability(courierId, now));
        row.setAvailable(available, now);
        availability.save(row);
        if (available) {
            AfterCommit.run(() -> dispatcher.courierBecameFree(courierId));
        }
        return AvailabilityResponse.of(row);
    }
}
