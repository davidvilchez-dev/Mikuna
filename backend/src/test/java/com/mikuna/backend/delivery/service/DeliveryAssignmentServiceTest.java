package com.mikuna.backend.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.DeliveryConflictException;
import com.mikuna.backend.delivery.model.CourierAvailability;
import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.CourierAvailabilityRepository;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderTransitionTest;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.service.OrderStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** RF-22 y RNF-05: elección del candidato, exclusiones y asignación condicional con repositorios simulados. */
class DeliveryAssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final CourierAvailabilityRepository availability = mock(CourierAvailabilityRepository.class);
    private final OrderStatusService orderStatus = mock(OrderStatusService.class);
    private DeliveryAssignmentService service;
    private Order order;
    private Delivery delivery;
    private final UUID courierA = UUID.randomUUID();
    private final UUID courierB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DeliveryAssignmentService(orders, deliveries, availability, orderStatus,
                Clock.fixed(NOW, ZoneOffset.UTC));
        order = OrderTransitionTest.orderIn(OrderStatus.READY);
        delivery = new Delivery(order.getId(), NOW);
        ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(deliveries.findByOrderId(order.getId())).thenReturn(Optional.of(delivery));
        when(deliveries.assignIfFree(any(), any(), any())).thenReturn(1);
    }

    private void candidate(UUID courierId, boolean locked, long stillCandidate) {
        when(availability.lockSkipLocked(courierId))
                .thenReturn(locked ? Optional.of(new CourierAvailability(courierId, NOW)) : Optional.empty());
        when(availability.countCandidate(courierId, delivery.getId())).thenReturn(stillCandidate);
    }

    @Test
    void asignaAlPrimerCandidatoComoSistema() {
        when(availability.findCandidateIds(delivery.getId())).thenReturn(List.of(courierA, courierB));
        candidate(courierA, true, 1);

        assertThat(service.assignAutomatically(order.getId())).isTrue();

        verify(deliveries).assignIfFree(order.getId(), courierA, NOW);
        verify(orderStatus).changeStatus(order, OrderStatus.ASSIGNED, null, ActorRole.SYSTEM, null);
    }

    @Test
    void saltaAlBloqueadoPorOtraAsignacionYAlQueDejoDeSerCandidato() {
        when(availability.findCandidateIds(delivery.getId())).thenReturn(List.of(courierA, courierB));
        candidate(courierA, false, 1);
        candidate(courierB, true, 1);

        assertThat(service.assignAutomatically(order.getId())).isTrue();
        verify(deliveries).assignIfFree(order.getId(), courierB, NOW);

        candidate(courierB, true, 0);
        assertThat(service.assignAutomatically(order.getId())).isFalse();
    }

    @Test
    void sinCandidatosNoCambiaNada() {
        when(availability.findCandidateIds(delivery.getId())).thenReturn(List.of());

        assertThat(service.assignAutomatically(order.getId())).isFalse();

        verify(deliveries, never()).assignIfFree(any(), any(), any());
        verify(orderStatus, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    void noAsignaUnPedidoQueNoEstaListoNiUnaEntregaYaAsignadaONiUnaEntregaInexistente() {
        Order assigned = OrderTransitionTest.orderIn(OrderStatus.ASSIGNED);
        when(orders.findByIdForUpdate(assigned.getId())).thenReturn(Optional.of(assigned));
        assertThat(service.assignAutomatically(assigned.getId())).isFalse();
        assertThat(service.assignAutomatically(UUID.randomUUID())).isFalse();

        ReflectionTestUtils.setField(delivery, "courierId", courierA);
        assertThat(service.assignAutomatically(order.getId())).isFalse();

        when(deliveries.findByOrderId(order.getId())).thenReturn(Optional.empty());
        assertThat(service.assignAutomatically(order.getId())).isFalse();
        verify(deliveries, never()).assignIfFree(any(), any(), any());
    }

    @Test
    void siLaActualizacionCondicionalNoAfectaUnaFilaHayConflictoYNoSeCambiaElEstado() {
        when(availability.findCandidateIds(delivery.getId())).thenReturn(List.of(courierA));
        candidate(courierA, true, 1);
        when(deliveries.assignIfFree(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.assignAutomatically(order.getId()))
                .isInstanceOf(DeliveryConflictException.class)
                .extracting(e -> ((DeliveryConflictException) e).getCode()).isEqualTo("DELIVERY_CONFLICT");
        verify(orderStatus, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    void asignarAUnRepartidorConcretoExigeQueSeaCandidato() {
        when(availability.findByIdForUpdate(courierA))
                .thenReturn(Optional.of(new CourierAvailability(courierA, NOW)));
        when(availability.countCandidate(courierA, delivery.getId())).thenReturn(1L);

        assertThat(service.assignToCourier(order.getId(), courierA)).isTrue();
        verify(orderStatus).changeStatus(eq(order), eq(OrderStatus.ASSIGNED), eq(null), eq(ActorRole.SYSTEM), eq(null));

        when(availability.countCandidate(courierA, delivery.getId())).thenReturn(0L);
        assertThat(service.assignToCourier(order.getId(), courierA)).isFalse();
        assertThat(service.assignToCourier(order.getId(), courierB)).isFalse();
        assertThat(service.assignToCourier(UUID.randomUUID(), courierA)).isFalse();
    }

    @Test
    void laListaDePendientesSeLimitaYConservaElOrden() {
        List<Delivery> pending = new java.util.ArrayList<>();
        for (int i = 0; i < DeliveryAssignmentService.MAX_PENDING + 5; i++) {
            pending.add(new Delivery(UUID.randomUUID(), NOW.plusSeconds(i)));
        }
        when(deliveries.findPending(OrderStatus.READY)).thenReturn(pending);

        List<UUID> ids = service.pendingOrderIds();

        assertThat(ids).hasSize(DeliveryAssignmentService.MAX_PENDING);
        assertThat(ids.get(0)).isEqualTo(pending.get(0).getOrderId());
    }
}
