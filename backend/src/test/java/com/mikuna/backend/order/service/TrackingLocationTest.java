package com.mikuna.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.order.dto.TrackingResponse;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.PaymentMethod;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.repository.OrderStatusChangeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-007 y FR-009: la ubicación solo se devuelve con el pedido Recogido; en otro caso, el motivo. */
class TrackingLocationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderStatusChangeRepository changes = mock(OrderStatusChangeRepository.class);
    private final CourierLocationLookup locations = mock(CourierLocationLookup.class);
    private final TrackingService service = new TrackingService(orders, changes, locations);
    private final UUID customerId = UUID.randomUUID();

    private Order orderIn(OrderStatus... path) {
        Order order = new Order(UUID.randomUUID(), customerId, UUID.randomUUID(), "Local", "k", PaymentMethod.CARD,
                new Order.Amounts(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN),
                new Order.Delivery(null, "Calle 1", "Centro", null, null), NOW);
        for (OrderStatus status : path) {
            order.transitionTo(status);
        }
        when(orders.findByIdAndCustomerId(order.getId(), customerId)).thenReturn(Optional.of(order));
        when(changes.findByOrderIdOrderByChangedAtAsc(order.getId())).thenReturn(List.of());
        return order;
    }

    private Order pickedUp() {
        return orderIn(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.ASSIGNED,
                OrderStatus.PICKED_UP);
    }

    @Test
    void asignadoNoMuestraUbicacionYNiSiquieraLaConsulta() {
        Order order = orderIn(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.ASSIGNED);

        TrackingResponse response = service.get(customerId, order.getId());

        assertThat(response.courierLocation()).isNull();
        assertThat(response.locationUnavailableReason()).isEqualTo(TrackingResponse.NOT_PICKED_UP);
        verify(locations, never()).latestFor(order.getId());
    }

    @Test
    void recogidoConUbicacionLaDevuelveConSuHora() {
        Order order = pickedUp();
        when(locations.latestFor(order.getId()))
                .thenReturn(Optional.of(new CourierLocationLookup.Position(-13.15, -74.22, NOW)));

        TrackingResponse response = service.get(customerId, order.getId());

        assertThat(response.courierLocation().latitude()).isEqualTo(-13.15);
        assertThat(response.courierLocation().longitude()).isEqualTo(-74.22);
        assertThat(response.courierLocation().updatedAt()).isEqualTo(NOW);
        assertThat(response.locationUnavailableReason()).isNull();
    }

    @Test
    void recogidoSinUbicacionIndicaQueAunNoHay() {
        Order order = pickedUp();
        when(locations.latestFor(order.getId())).thenReturn(Optional.empty());

        TrackingResponse response = service.get(customerId, order.getId());

        assertThat(response.courierLocation()).isNull();
        assertThat(response.locationUnavailableReason()).isEqualTo(TrackingResponse.NO_LOCATION_YET);
    }

    @Test
    void entregadoCanceladoYRechazadoNoMuestranUbicacion() {
        Order delivered = orderIn(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY,
                OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.DELIVERED);
        Order cancelled = orderIn(OrderStatus.CANCELLED);
        Order rejected = orderIn(OrderStatus.REJECTED);

        for (Order order : List.of(delivered, cancelled, rejected)) {
            TrackingResponse response = service.get(customerId, order.getId());
            assertThat(response.courierLocation()).isNull();
            assertThat(response.locationUnavailableReason()).isEqualTo(TrackingResponse.FINISHED);
        }
        verify(locations, never()).latestFor(any());
    }

    @Test
    void antesDeLaAsignacionElMotivoEsNoRecogido() {
        Order order = orderIn(OrderStatus.ACCEPTED);

        assertThat(service.get(customerId, order.getId()).locationUnavailableReason())
                .isEqualTo(TrackingResponse.NOT_PICKED_UP);
    }
}
