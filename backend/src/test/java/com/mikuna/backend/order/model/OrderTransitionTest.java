package com.mikuna.backend.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikuna.backend.common.exception.InvalidOrderTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-007: solo las seis transiciones permitidas; Rechazado, Cancelado y Listo para recoger son finales. */
public class OrderTransitionTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED),
            OrderStatus.ACCEPTED, Set.of(OrderStatus.PREPARING),
            OrderStatus.PREPARING, Set.of(OrderStatus.READY));

    public static Order orderIn(OrderStatus status) {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Local", "k",
                PaymentMethod.CARD, new Order.Amounts(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN),
                new Order.Delivery(null, "Calle 1", "Centro", null, null), Instant.now());
        // Se lleva el pedido al estado de partida solo con transiciones válidas.
        switch (status) {
            case PENDING -> { }
            case ACCEPTED -> order.transitionTo(OrderStatus.ACCEPTED);
            case REJECTED -> order.transitionTo(OrderStatus.REJECTED);
            case CANCELLED -> order.transitionTo(OrderStatus.CANCELLED);
            case PREPARING -> {
                order.transitionTo(OrderStatus.ACCEPTED);
                order.transitionTo(OrderStatus.PREPARING);
            }
            case READY -> {
                order.transitionTo(OrderStatus.ACCEPTED);
                order.transitionTo(OrderStatus.PREPARING);
                order.transitionTo(OrderStatus.READY);
            }
        }
        return order;
    }

    @Test
    void naceEnPendiente() {
        assertThat(orderIn(OrderStatus.PENDING).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void permiteExactamenteLasTransicionesDeLaTabla() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                Order order = orderIn(from);
                if (ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
                    order.transitionTo(to);
                    assertThat(order.getStatus()).as(from + " -> " + to).isEqualTo(to);
                } else {
                    assertThatThrownBy(() -> order.transitionTo(to)).as(from + " -> " + to)
                            .isInstanceOf(InvalidOrderTransitionException.class);
                    assertThat(order.getStatus()).as("sin cambios").isEqualTo(from);
                }
            }
        }
    }

    @Test
    void losEstadosFinalesNoTienenSalida() {
        for (OrderStatus finalStatus : Set.of(OrderStatus.REJECTED, OrderStatus.CANCELLED, OrderStatus.READY)) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThatThrownBy(() -> orderIn(finalStatus).transitionTo(to))
                        .isInstanceOf(InvalidOrderTransitionException.class);
            }
        }
    }
}
