package com.mikuna.backend.order.model;

import java.util.UUID;

/** El pedido quedó Listo para recoger (spec 007): el módulo de reparto crea su entrega al recibirlo (spec 008). */
public record OrderReadyEvent(UUID orderId) {
}
