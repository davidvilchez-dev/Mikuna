package com.mikuna.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detalle administrativo de una entrega: su estado, repartidor, cambios de estado y el pedido asociado con su pago. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminDeliveryDetail(UUID id, UUID orderId, String status, Instant readyAt, Instant assignedAt,
        AdminOrderSummary.Party courier, List<StatusChangeEntry> statusChanges, AdminOrderSummary order) {
}
