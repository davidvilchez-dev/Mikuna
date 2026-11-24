package com.mikuna.backend.order.model;

import java.util.UUID;

/**
 * {@code reason} es el motivo registrado (o {@code null}); permite, por ejemplo, avisar del vencimiento (spec 012).
 * Se publica al guardar cada cambio de estado del pedido, en la misma transacción (spec 009). Lo consumen los
 * módulos de reparto (hitos de ubicación) y de avisos; {@code order} no depende de ellos.
 */
public record OrderStatusChangedEvent(UUID changeId, UUID orderId, OrderStatus from, OrderStatus to, UUID actorId,
        ActorRole actorRole, String reason) {

    public OrderStatusChangedEvent(UUID changeId, UUID orderId, OrderStatus from, OrderStatus to, UUID actorId,
            ActorRole actorRole) {
        this(changeId, orderId, from, to, actorId, actorRole, null);
    }
}
