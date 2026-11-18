package com.mikuna.backend.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mikuna.backend.order.model.OrderStatusChange;
import jakarta.validation.constraints.Size;

/** Cuerpo opcional de {@code reject}: motivo de hasta 200 caracteres. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RejectDeliveryRequest(
        @Size(max = OrderStatusChange.REASON_MAX, message = "El motivo admite hasta 200 caracteres") String reason) {
}
