package com.mikuna.backend.delivery.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.model.OrderReadyEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El despacho tras el commit nunca propaga errores y se detiene en la primera entrega asignada (FR-004). */
class DeliveryDispatcherTest {

    private final DeliveryAssignmentService assignment = mock(DeliveryAssignmentService.class);
    private final DeliveryDispatcher dispatcher = new DeliveryDispatcher(assignment);
    private final UUID courier = UUID.randomUUID();

    @Test
    void unErrorEnLaAsignacionAutomaticaNoSePropagaYLaEntregaQuedaPendiente() {
        UUID order = UUID.randomUUID();
        when(assignment.assignAutomatically(order)).thenThrow(new IllegalStateException("fallo"));

        dispatcher.dispatch(order);

        verify(assignment).assignAutomatically(order);
    }

    @Test
    void alQuedarLibreSeAsignaLaPrimeraPendienteQuePuedaTomarYSeDetiene() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        when(assignment.pendingOrderIds()).thenReturn(List.of(first, second, third));
        when(assignment.assignToCourier(first, courier)).thenReturn(false);
        when(assignment.assignToCourier(second, courier)).thenReturn(true);

        dispatcher.courierBecameFree(courier);

        verify(assignment).assignToCourier(first, courier);
        verify(assignment).assignToCourier(second, courier);
        verify(assignment, never()).assignToCourier(third, courier);
    }

    @Test
    void sinPendientesOConErrorNoSeHaceNadaNiSePropaga() {
        when(assignment.pendingOrderIds()).thenReturn(List.of());
        dispatcher.courierBecameFree(courier);
        verify(assignment, never()).assignToCourier(any(), any());

        when(assignment.pendingOrderIds()).thenThrow(new IllegalStateException("fallo"));
        dispatcher.courierBecameFree(courier);
    }

    @Test
    void elListenerCreaLaEntregaUnaSolaVezYDespachaTrasElCommit() {
        DeliveryRepository deliveries = mock(DeliveryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
        OrderReadyListener listener = new OrderReadyListener(deliveries, dispatcher, clock);
        UUID order = UUID.randomUUID();
        when(deliveries.findByOrderId(order)).thenReturn(Optional.empty());

        listener.createDelivery(new OrderReadyEvent(order));
        verify(deliveries).save(any(Delivery.class));

        when(deliveries.findByOrderId(order)).thenReturn(Optional.of(new Delivery(order, Instant.now())));
        listener.createDelivery(new OrderReadyEvent(order));
        verify(deliveries, org.mockito.Mockito.times(1)).save(any(Delivery.class));

        listener.dispatchAfterCommit(new OrderReadyEvent(order));
        verify(assignment).assignAutomatically(order);
    }
}
