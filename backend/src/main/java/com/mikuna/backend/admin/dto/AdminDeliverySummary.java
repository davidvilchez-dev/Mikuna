package com.mikuna.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Fila del listado administrativo de entregas; el estado es el del pedido (spec 008). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminDeliverySummary(UUID id, UUID orderId, String status, Instant createdAt,
        AdminOrderSummary.Party courier, String restaurantName) {
}
