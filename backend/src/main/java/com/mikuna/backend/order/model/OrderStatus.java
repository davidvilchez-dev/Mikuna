package com.mikuna.backend.order.model;

/** Estado del pedido (specs 007 y 008): nace PENDING y avanza solo por las transiciones de {@link Order#transitionTo}. */
public enum OrderStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, PREPARING, READY, ASSIGNED, PICKED_UP, DELIVERED
}
