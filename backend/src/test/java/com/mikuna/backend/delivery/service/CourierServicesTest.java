package com.mikuna.backend.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.delivery.dto.AvailabilityResponse;
import com.mikuna.backend.delivery.dto.CourierDeliveryResponse;
import com.mikuna.backend.delivery.model.CourierAvailability;
import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.CourierAvailabilityRepository;
import com.mikuna.backend.delivery.repository.DeliveryRejectionRepository;
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

/** RF-21, RF-23 y RF-25: disponibilidad, recogida, entrega y rechazo del repartidor con repositorios simulados. */
class CourierServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final OrderRepository orders = mock(OrderRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final DeliveryRejectionRepository rejections = mock(DeliveryRejectionRepository.class);
    private final CourierAvailabilityRepository availability = mock(CourierAvailabilityRepository.class);
    private final OrderStatusService orderStatus = mock(OrderStatusService.class);
    private final DeliveryDispatcher dispatcher = mock(DeliveryDispatcher.class);
    private final com.mikuna.backend.payment.service.PaymentService payments =
            mock(com.mikuna.backend.payment.service.PaymentService.class);
    private final com.mikuna.backend.restaurant.repository.RestaurantRepository restaurants = mock(com.mikuna.backend.restaurant.repository.RestaurantRepository.class);
    private final UUID courier = UUID.randomUUID();
    private CourierDeliveryService deliveryService;
    private CourierAvailabilityService availabilityService;
    private Order order;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        deliveryService = new CourierDeliveryService(orders, deliveries, rejections, availability, orderStatus, restaurants,
                dispatcher, payments, CLOCK);
        availabilityService = new CourierAvailabilityService(availability, dispatcher, CLOCK);
        order = OrderTransitionTest.orderIn(OrderStatus.ASSIGNED);
        delivery = new Delivery(order.getId(), NOW);
        ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "courierId", courier);
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(deliveries.findByOrderId(order.getId())).thenReturn(Optional.of(delivery));
    }

    @Test
    void activarCreaLaFilaFijaLibreDesdeYDespachaLaPendiente() {
        when(availability.findByIdForUpdate(courier)).thenReturn(Optional.empty());

        AvailabilityResponse response = availabilityService.set(courier, true);

        assertThat(response.available()).isTrue();
        assertThat(response.freeSince()).isEqualTo(NOW);
        verify(dispatcher).courierBecameFree(courier);
    }

    @Test
    void desactivarNoDespachaYReactivarNoReiniciaLibreDesdeSiYaEstabaDisponible() {
        Instant earlier = NOW.minusSeconds(600);
        CourierAvailability row = new CourierAvailability(courier, earlier);
        row.setAvailable(true, earlier);
        when(availability.findByIdForUpdate(courier)).thenReturn(Optional.of(row));

        assertThat(availabilityService.set(courier, true).freeSince()).isEqualTo(earlier);
        assertThat(availabilityService.set(courier, false).available()).isFalse();
        assertThat(row.getFreeSince()).isEqualTo(earlier);
        verify(dispatcher).courierBecameFree(courier); // solo por la activación
    }

    @Test
    void sinFilaLaDisponibilidadEsFalsa() {
        when(availability.findById(courier)).thenReturn(Optional.empty());

        assertThat(availabilityService.get(courier).available()).isFalse();
        assertThat(availabilityService.get(courier).freeSince()).isNull();
    }

    @Test
    void recogerCambiaAPickedUpConElRepartidorComoResponsable() {
        CourierDeliveryResponse response = deliveryService.pickup(courier, order.getId());

        verify(orderStatus).changeStatus(order, OrderStatus.PICKED_UP, courier, ActorRole.COURIER, null);
        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.delivery().street()).isEqualTo("Calle 1");
    }

    @Test
    void entregarMarcaLibreAlRepartidorYDespachaLaSiguiente() {
        CourierAvailability row = new CourierAvailability(courier, NOW.minusSeconds(3600));
        when(availability.findByIdForUpdate(courier)).thenReturn(Optional.of(row));

        deliveryService.complete(courier, order.getId());

        verify(orderStatus).changeStatus(order, OrderStatus.DELIVERED, courier, ActorRole.COURIER, null);
        assertThat(row.getFreeSince()).isEqualTo(NOW);
        verify(dispatcher).courierBecameFree(courier);
    }

    @Test
    void entregarSinFilaDeDisponibilidadTambienDespacha() {
        when(availability.findByIdForUpdate(courier)).thenReturn(Optional.empty());

        deliveryService.complete(courier, order.getId());

        verify(dispatcher).courierBecameFree(courier);
    }

    @Test
    void rechazarGuardaElRechazoLiberaLaEntregaYDespacha() {
        deliveryService.reject(courier, order.getId(), "moto averiada");

        verify(orderStatus).changeStatus(order, OrderStatus.READY, courier, ActorRole.COURIER, "moto averiada");
        verify(rejections).save(any());
        assertThat(delivery.getCourierId()).isNull();
        verify(dispatcher).dispatch(order.getId());
        verify(dispatcher).courierBecameFree(courier);
    }

    @Test
    void siLaTransicionEsInvalidaNoSeGuardaElRechazoNiSeLiberaNiSeDespacha() {
        doThrow(new com.mikuna.backend.common.exception.InvalidOrderTransitionException()).when(orderStatus)
                .changeStatus(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> deliveryService.reject(courier, order.getId(), null))
                .isInstanceOf(com.mikuna.backend.common.exception.InvalidOrderTransitionException.class);

        verify(rejections, never()).save(any());
        assertThat(delivery.getCourierId()).isEqualTo(courier);
        verify(dispatcher, never()).dispatch(any());
        verify(dispatcher, never()).courierBecameFree(any());
    }

    @Test
    void laEntregaDeOtroRepartidorOInexistenteEsNoEncontrada() {
        UUID other = UUID.randomUUID();

        for (Runnable action : List.<Runnable>of(() -> deliveryService.pickup(other, order.getId()),
                () -> deliveryService.complete(other, order.getId()),
                () -> deliveryService.reject(other, order.getId(), null),
                () -> deliveryService.pickup(courier, UUID.randomUUID()))) {
            assertThatThrownBy(action::run).isInstanceOf(OrderNotFoundException.class);
        }
        verify(orderStatus, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    void laListaActivaSoloIncluyeLasEntregasConPedidoExistente() {
        Delivery orphan = new Delivery(UUID.randomUUID(), NOW);
        when(deliveries.findActiveByCourier(courier, List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(List.of(delivery, orphan));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(orders.findById(orphan.getOrderId())).thenReturn(Optional.empty());

        assertThat(deliveryService.listActive(courier)).hasSize(1);
    }
}
