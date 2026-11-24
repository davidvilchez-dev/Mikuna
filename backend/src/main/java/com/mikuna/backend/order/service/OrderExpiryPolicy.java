package com.mikuna.backend.order.service;

import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Plazo para que el restaurante responda un pedido Pendiente (spec 012). Se fija en cada pedido al crearlo, así
 * un cambio de configuración no mueve los plazos de los pedidos ya creados.
 */
@Component
public class OrderExpiryPolicy {

    /** Motivo del cambio de estado al vencer; identifica el vencimiento en avisos, errores y consultas. */
    public static final String REASON = "sin respuesta del restaurante";

    private final Duration timeout;

    public OrderExpiryPolicy(@Value("${mikuna.orders.response-timeout}") Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("mikuna.orders.response-timeout debe ser positivo");
        }
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }

    public Instant expiresAt(Instant createdAt) {
        return createdAt.plus(timeout);
    }

    /** Segundos restantes para un pedido Pendiente, nunca negativos; {@code null} si no aplica. */
    public static Long secondsToExpire(Order order, Instant now) {
        if (order.getStatus() != OrderStatus.PENDING || order.getExpiresAt() == null) {
            return null;
        }
        return Math.max(0, Duration.between(now, order.getExpiresAt()).toSeconds());
    }
}
