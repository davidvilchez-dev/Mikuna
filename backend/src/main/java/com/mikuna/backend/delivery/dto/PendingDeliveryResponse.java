package com.mikuna.backend.delivery.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entrega que el administrador debe resolver: pendiente de asignación (READY sin repartidor) o Asignada a un
 * repartidor inhabilitado ({@code courierDisabled}); incluye los candidatos y, tras actuar, el estado resultante.
 */
public record PendingDeliveryResponse(UUID orderId, String status, String restaurantName, String district,
        Instant readyAt, UUID courierId, boolean courierDisabled, List<Candidate> candidates) {

    public record Candidate(UUID courierId, String name, Instant freeSince) {
    }
}
