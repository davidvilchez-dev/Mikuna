package com.mikuna.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.order.dto.TrackingResponse;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChange;
import com.mikuna.backend.order.model.PaymentMethod;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.repository.OrderStatusChangeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF-27: etapas del pedido con su fecha, sin exponer actores. */
class TrackingServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-18T12:00:00Z");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderStatusChangeRepository changes = mock(OrderStatusChangeRepository.class);
    private final CourierLocationLookup locations = mock(CourierLocationLookup.class);
    private final TrackingService service = new TrackingService(orders, changes, locations);
    private final UUID customerId = UUID.randomUUID();
    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order(UUID.randomUUID(), customerId, UUID.randomUUID(), "Local", "k", PaymentMethod.CARD,
                new Order.Amounts(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN),
                new Order.Delivery(null, "Calle 1", "Centro", null, null), T0);
        when(orders.findByIdAndCustomerId(order.getId(), customerId)).thenReturn(Optional.of(order));
    }

    private OrderStatusChange change(OrderStatus from, OrderStatus to, long seconds, ActorRole role) {
        return new OrderStatusChange(order.getId(), from, to, T0.plusSeconds(seconds), UUID.randomUUID(), role, null);
    }

    @Test
    void laEtapaInicialUsaLaFechaDeCreacionYLasDemasSusCambios() {
        order.transitionTo(OrderStatus.ACCEPTED);
        order.transitionTo(OrderStatus.PREPARING);
        when(changes.findByOrderIdOrderByChangedAtAsc(order.getId())).thenReturn(List.of(
                change(OrderStatus.PENDING, OrderStatus.ACCEPTED, 60, ActorRole.RESTAURANT),
                change(OrderStatus.ACCEPTED, OrderStatus.PREPARING, 120, ActorRole.RESTAURANT)));

        TrackingResponse response = service.get(customerId, order.getId());

        assertThat(response.status()).isEqualTo("PREPARING");
        assertThat(response.stages()).extracting(TrackingResponse.Stage::status)
                .containsExactly("PENDING", "ACCEPTED", "PREPARING");
        assertThat(response.stages().get(0).at()).isEqualTo(T0);
        assertThat(response.stages().get(2).at()).isEqualTo(T0.plusSeconds(120));
    }

    @Test
    void elRechazoDelRepartidorNoCreaUnaEtapaNiDuplicaAsignado() {
        for (OrderStatus s : List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY,
                OrderStatus.ASSIGNED)) {
            order.transitionTo(s);
        }
        List<OrderStatusChange> list = new ArrayList<>(List.of(
                change(OrderStatus.PENDING, OrderStatus.ACCEPTED, 60, ActorRole.RESTAURANT),
                change(OrderStatus.ACCEPTED, OrderStatus.PREPARING, 120, ActorRole.RESTAURANT),
                change(OrderStatus.PREPARING, OrderStatus.READY, 180, ActorRole.RESTAURANT),
                change(OrderStatus.READY, OrderStatus.ASSIGNED, 240, ActorRole.SYSTEM),
                change(OrderStatus.ASSIGNED, OrderStatus.READY, 600, ActorRole.COURIER),
                change(OrderStatus.READY, OrderStatus.ASSIGNED, 700, ActorRole.SYSTEM)));
        when(changes.findByOrderIdOrderByChangedAtAsc(order.getId())).thenReturn(list);

        TrackingResponse response = service.get(customerId, order.getId());

        assertThat(response.stages()).extracting(TrackingResponse.Stage::status)
                .containsExactly("PENDING", "ACCEPTED", "PREPARING", "READY", "ASSIGNED");
        assertThat(response.stages().get(4).at()).isEqualTo(T0.plusSeconds(700));
    }

    @Test
    void unPedidoAjenoOInexistenteEs404() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), order.getId()))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> service.get(customerId, UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void lasEtapasSoloExponenEstadoYFecha() {
        assertThat(TrackingResponse.Stage.class.getRecordComponents()).extracting("name")
                .containsExactly("status", "at");
    }
}
