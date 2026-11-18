package com.mikuna.backend.order.model;

/** Estado del pedido (spec 007): nace PENDING y avanza solo por las transiciones de {@link Order#transitionTo}. */
public enum OrderStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, PREPARING, READY
}
