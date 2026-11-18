package com.mikuna.backend.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.delivery.model.CourierLocation;
import com.mikuna.backend.delivery.model.DeliveryMilestone;
import com.mikuna.backend.delivery.model.MilestoneType;
import com.mikuna.backend.delivery.repository.CourierLocationRepository;
import com.mikuna.backend.delivery.repository.DeliveryMilestoneRepository;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChangedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** FR-006: hito permanente al recoger y al entregar; la ubicación vigente se descarta al terminar la entrega. */
class DeliveryMilestoneListenerTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    private final CourierLocationRepository locations = mock(CourierLocationRepository.class);
    private final DeliveryMilestoneRepository milestones = mock(DeliveryMilestoneRepository.class);
    private final DeliveryMilestoneListener listener = new DeliveryMilestoneListener(locations, milestones,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID orderId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();

    private OrderStatusChangedEvent event(OrderStatus from, OrderStatus to, UUID actor, ActorRole role) {
        return new OrderStatusChangedEvent(UUID.randomUUID(), orderId, from, to, actor, role);
    }

    private CourierLocation location() {
        CourierLocation location = mock(CourierLocation.class);
        when(location.getLatitude()).thenReturn(-13.15);
        when(location.getLongitude()).thenReturn(-74.22);
        when(location.getRecordedAt()).thenReturn(NOW.minusSeconds(3));
        when(locations.findByCourierIdAndOrderId(courierId, orderId)).thenReturn(Optional.of(location));
        return location;
    }

    @Test
    void alRecogerGuardaElHitoDeRecogidaYConservaLaUbicacionVigente() {
        location();

        listener.onStatusChanged(event(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, courierId, ActorRole.COURIER));

        ArgumentCaptor<DeliveryMilestone> captor = ArgumentCaptor.forClass(DeliveryMilestone.class);
        verify(milestones).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(MilestoneType.PICKUP);
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(captor.getValue().getLatitude()).isEqualTo(-13.15);
        assertThat(captor.getValue().getRecordedAt()).isEqualTo(NOW.minusSeconds(3));
        verify(locations, never()).deleteByOrderId(any());
    }

    @Test
    void alEntregarGuardaElHitoDeEntregaYBorraLaUbicacionVigente() {
        location();

        listener.onStatusChanged(event(OrderStatus.PICKED_UP, OrderStatus.DELIVERED, courierId, ActorRole.COURIER));

        ArgumentCaptor<DeliveryMilestone> captor = ArgumentCaptor.forClass(DeliveryMilestone.class);
        verify(milestones).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(MilestoneType.DELIVERY);
        verify(locations).deleteByOrderId(orderId);
    }

    @Test
    void sinUbicacionVigenteNoSeInventaNingunHito() {
        when(locations.findByCourierIdAndOrderId(courierId, orderId)).thenReturn(Optional.empty());

        listener.onStatusChanged(event(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, courierId, ActorRole.COURIER));

        verify(milestones, never()).save(any());
    }

    @Test
    void unHitoYaRegistradoNoSeRepite() {
        location();
        when(milestones.existsByOrderIdAndType(orderId, MilestoneType.PICKUP)).thenReturn(true);

        listener.onStatusChanged(event(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, courierId, ActorRole.COURIER));

        verify(milestones, never()).save(any());
    }

    @Test
    void laCancelacionYLaLiberacionPorRechazoBorranLaUbicacionVigente() {
        listener.onStatusChanged(event(OrderStatus.PENDING, OrderStatus.CANCELLED, null, ActorRole.SYSTEM));
        listener.onStatusChanged(event(OrderStatus.ASSIGNED, OrderStatus.READY, courierId, ActorRole.COURIER));

        verify(locations, org.mockito.Mockito.times(2)).deleteByOrderId(orderId);
        verify(milestones, never()).save(any());
    }

    @Test
    void otrosCambiosNoTocanLaUbicacion() {
        listener.onStatusChanged(event(OrderStatus.READY, OrderStatus.ASSIGNED, null, ActorRole.SYSTEM));

        verify(locations, never()).deleteByOrderId(any());
        verify(milestones, never()).save(any());
    }
}
