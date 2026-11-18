package com.mikuna.backend.notification.model;

/** Cambio relevante del pedido o la entrega que origina un aviso (RF-29). */
public enum NotificationType {
    NEW_ORDER, ACCEPTED, REJECTED, CANCELLED, PREPARING, READY, ASSIGNED, PICKED_UP, DELIVERED
}
