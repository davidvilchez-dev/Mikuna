package com.mikuna.backend.order.model;

import java.util.UUID;

/** Se publica solo cuando una confirmación crea un pedido nuevo; un reenvío idempotente no lo publica. */
public record OrderPlacedEvent(UUID orderId) {
}
