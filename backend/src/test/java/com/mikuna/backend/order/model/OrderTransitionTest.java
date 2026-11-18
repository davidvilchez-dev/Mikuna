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

/**
 * FR-007 (spec 007) y FR-013 (spec 008): solo las transiciones permitidas; Rechazado, Cancelado y Entregado son
 * finales. El retroceso Asignado a Listo solo existe por el rechazo del repartidor.
 */
public class OrderTransitionTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED),
            OrderStatus.ACCEPTED, Set.of(OrderStatus.PREPARING),
            OrderStatus.PREPARING, Set.of(OrderStatus.READY),
            OrderStatus.READY, Set.of(OrderStatus.ASSIGNED),
            OrderStatus.ASSIGNED, Set.of(OrderStatus.PICKED_UP, OrderStatus.READY),
            OrderStatus.PICKED_UP, Set.of(OrderStatus.DELIVERED));

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
            case ASSIGNED -> {
                order.transitionTo(OrderStatus.ACCEPTED);
                order.transitionTo(OrderStatus.PREPARING);
                order.transitionTo(OrderStatus.READY);
                order.transitionTo(OrderStatus.ASSIGNED);
            }
            case PICKED_UP -> {
                order.transitionTo(OrderStatus.ACCEPTED);
                order.transitionTo(OrderStatus.PREPARING);
                order.transitionTo(OrderStatus.READY);
                order.transitionTo(OrderStatus.ASSIGNED);
                order.transitionTo(OrderStatus.PICKED_UP);
            }
            case DELIVERED -> {
                order.transitionTo(OrderStatus.ACCEPTED);
                order.transitionTo(OrderStatus.PREPARING);
                order.transitionTo(OrderStatus.READY);
                order.transitionTo(OrderStatus.ASSIGNED);
                order.transitionTo(OrderStatus.PICKED_UP);
                order.transitionTo(OrderStatus.DELIVERED);
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
    void losSaltosYRetrocesosDelRepartoSeRechazan() {
        assertThatThrownBy(() -> orderIn(OrderStatus.READY).transitionTo(OrderStatus.PICKED_UP))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.ASSIGNED).transitionTo(OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.PICKED_UP).transitionTo(OrderStatus.ASSIGNED))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.PICKED_UP).transitionTo(OrderStatus.READY))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void losEstadosFinalesNoTienenSalida() {
        for (OrderStatus finalStatus : Set.of(OrderStatus.REJECTED, OrderStatus.CANCELLED, OrderStatus.DELIVERED)) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThatThrownBy(() -> orderIn(finalStatus).transitionTo(to))
                        .isInstanceOf(InvalidOrderTransitionException.class);
            }
        }
    }
}
