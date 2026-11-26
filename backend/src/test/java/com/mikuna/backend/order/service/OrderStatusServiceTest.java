package com.mikuna.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.InvalidOrderTransitionException;
import com.mikuna.backend.common.exception.OrderConflictException;
import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.common.exception.RestaurantNotEnabledException;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChange;
import com.mikuna.backend.order.model.PaymentMethod;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.repository.OrderStatusChangeRepository;
import com.mikuna.backend.payment.model.Payment;
import com.mikuna.backend.payment.model.PaymentStatus;
import com.mikuna.backend.payment.repository.PaymentRepository;
import com.mikuna.backend.payment.service.CaptureResult;
import com.mikuna.backend.payment.service.PaymentService;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** RF-18, RF-19, RF-20: reglas de decisión, cancelación y avance con repositorios y pago simulados. */
class OrderStatusServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderStatusChangeRepository changes = mock(OrderStatusChangeRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final Restaurant restaurant = mock(Restaurant.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private OrderStatusService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderStatusService(orders, changes, payments, paymentService, restaurants, CLOCK, events);
        when(restaurant.getId()).thenReturn(restaurantId);
        when(restaurant.getStatus()).thenReturn(RestaurantStatus.APPROVED);
        when(restaurants.findByOwnerId(ownerId)).thenReturn(Optional.of(restaurant));
        order = new Order(UUID.randomUUID(), customerId, restaurantId, "Local", "k", PaymentMethod.CARD,
                new Order.Amounts(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN),
                new Order.Delivery(null, "Calle 1", "Centro", null, null), Instant.now(CLOCK));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.of(
                Payment.authorized(order.getId(), customerId, BigDecimal.TEN, "pen", "pi_1", Instant.now(CLOCK))));
        when(paymentService.capture(order.getId())).thenReturn(CaptureResult.CAPTURED);
    }

    private OrderStatusChange savedChange() {
        ArgumentCaptor<OrderStatusChange> captor = ArgumentCaptor.forClass(OrderStatusChange.class);
        verify(changes).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void aceptarCobraUnaVezYRegistraAlRestaurante() {
        OrderStatusService.Result result = service.accept(ownerId, order.getId());

        verify(paymentService).capture(order.getId());
        verify(paymentService, never()).release(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(result.reservationExpired()).isFalse();
        OrderStatusChange change = savedChange();
        assertThat(change.getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(change.getToStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(change.getActorId()).isEqualTo(ownerId);
        assertThat(change.getActorRole()).isEqualTo(ActorRole.RESTAURANT);
        assertThat(change.getChangedAt()).isEqualTo(Instant.now(CLOCK));
    }

    @Test
    void siElRelojRetrocedeElCambioQuedaDespuesDelAnterior() {
        // El último cambio quedó registrado 1,1 s "en el futuro" respecto del reloj actual (el reloj retrocedió).
        Instant later = Instant.now(CLOCK).plusMillis(1100);
        OrderStatusChange previous = new OrderStatusChange(order.getId(), OrderStatus.PENDING, OrderStatus.PENDING,
                later, null, ActorRole.SYSTEM, null);
        when(changes.findFirstByOrderIdOrderByChangedAtDesc(order.getId())).thenReturn(Optional.of(previous));

        service.accept(ownerId, order.getId());

        assertThat(savedChange().getChangedAt()).isEqualTo(later.plus(1, java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void dosCambiosEnElMismoInstanteQuedanOrdenados() {
        OrderStatusChange previous = new OrderStatusChange(order.getId(), OrderStatus.PENDING, OrderStatus.PENDING,
                Instant.now(CLOCK), null, ActorRole.SYSTEM, null);
        when(changes.findFirstByOrderIdOrderByChangedAtDesc(order.getId())).thenReturn(Optional.of(previous));

        service.accept(ownerId, order.getId());

        assertThat(savedChange().getChangedAt()).isAfter(previous.getChangedAt());
    }

    @Test
    void aceptarConReservaVencidaCancelaComoSistemaSinLanzar() {
        when(paymentService.capture(order.getId())).thenReturn(CaptureResult.RESERVATION_EXPIRED);

        OrderStatusService.Result result = service.accept(ownerId, order.getId());

        assertThat(result.reservationExpired()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        OrderStatusChange change = savedChange();
        assertThat(change.getActorRole()).isEqualTo(ActorRole.SYSTEM);
        assertThat(change.getActorId()).isNull();
        assertThat(change.getReason()).isEqualTo("reserva vencida");
    }

    @Test
    void siLaPasarelaFallaAlAceptarElPedidoSigueEnPendienteYNoSeRegistraNada() {
        doThrow(new PaymentUnavailableException()).when(paymentService).capture(order.getId());

        assertThatThrownBy(() -> service.accept(ownerId, order.getId()))
                .isInstanceOf(PaymentUnavailableException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(changes, never()).save(any());
    }

    @Test
    void rechazarLiberaUnaVezSinCobrarYGuardaElMotivoRecortado() {
        service.reject(ownerId, order.getId(), "  sin stock  ");

        verify(paymentService).release(order.getId());
        verify(paymentService, never()).capture(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(savedChange().getReason()).isEqualTo("sin stock");
    }

    @Test
    void unMotivoEnBlancoNoSeGuarda() {
        service.reject(ownerId, order.getId(), "   ");
        assertThat(savedChange().getReason()).isNull();
    }

    @Test
    void decidirSobreUnPedidoYaDecididoSeRechazaSinTocarElPago() {
        service.reject(ownerId, order.getId(), null);

        assertThatThrownBy(() -> service.accept(ownerId, order.getId()))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> service.reject(ownerId, order.getId(), null))
                .isInstanceOf(InvalidOrderTransitionException.class);
        verify(paymentService, times(1)).release(order.getId());
        verify(paymentService, never()).capture(any());
        verify(changes, times(1)).save(any());
    }

    @Test
    void unPedidoDeOtroRestauranteEsInexistente() {
        Order other = new Order(UUID.randomUUID(), customerId, UUID.randomUUID(), "Otro", "k2", PaymentMethod.CARD,
                new Order.Amounts(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN),
                new Order.Delivery(null, "Calle 1", "Centro", null, null), Instant.now(CLOCK));
        when(orders.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.accept(ownerId, other.getId())).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> service.get(ownerId, other.getId())).isInstanceOf(OrderNotFoundException.class);
        verify(paymentService, never()).capture(any());
    }

    @Test
    void unRestauranteNoAprobadoOSinRestauranteNoPuedeOperar() {
        when(restaurant.getStatus()).thenReturn(RestaurantStatus.REJECTED);
        assertThatThrownBy(() -> service.accept(ownerId, order.getId()))
                .isInstanceOf(RestaurantNotEnabledException.class);
        assertThatThrownBy(() -> service.list(ownerId, OrderStatus.PENDING))
                .isInstanceOf(RestaurantNotEnabledException.class);

        assertThatThrownBy(() -> service.accept(UUID.randomUUID(), order.getId()))
                .isInstanceOf(RestaurantNotEnabledException.class);
        verify(paymentService, never()).capture(any());
    }

    @Test
    void listaSoloLosPedidosDelRestauranteEnElEstadoPedido() {
        when(orders.findByRestaurantIdAndStatusOrderByCreatedAtAsc(restaurantId, OrderStatus.PENDING))
                .thenReturn(List.of(order));

        List<OrderStatusService.Result> result = service.list(ownerId, OrderStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).payment()).isEqualTo(PaymentStatus.AUTHORIZED);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        assertThat(service.get(ownerId, order.getId()).order()).isSameAs(order);
    }

    @Test
    void cancelarLiberaYRegistraAlCliente() {
        service.cancel(customerId, order.getId(), "me equivoqué");

        verify(paymentService).release(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        OrderStatusChange change = savedChange();
        assertThat(change.getActorRole()).isEqualTo(ActorRole.CUSTOMER);
        assertThat(change.getActorId()).isEqualTo(customerId);
        assertThat(change.getReason()).isEqualTo("me equivoqué");
    }

    @Test
    void cancelarUnPedidoAjenoEsInexistente() {
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), order.getId(), null))
                .isInstanceOf(OrderNotFoundException.class);
        verify(paymentService, never()).release(any());
    }

    @Test
    void cancelarTrasLaAceptacionExplicaQueNoHayReembolso() {
        for (OrderStatus status : List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)) {
            Order accepted = com.mikuna.backend.order.model.OrderTransitionTest.orderIn(status);
            when(orders.findByIdForUpdate(accepted.getId())).thenReturn(Optional.of(accepted));
            UUID owner = accepted.getCustomerId();
            assertThatThrownBy(() -> service.cancel(owner, accepted.getId(), null))
                    .isInstanceOf(InvalidOrderTransitionException.class)
                    .hasMessage(InvalidOrderTransitionException.NO_REFUND);
        }
        verify(paymentService, never()).release(any());
    }

    @Test
    void cancelarUnPedidoYaRechazadoOCanceladoDaElMensajeGenericoYNoLiberaDeNuevo() {
        service.cancel(customerId, order.getId(), null);

        assertThatThrownBy(() -> service.cancel(customerId, order.getId(), null))
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessage(InvalidOrderTransitionException.GENERIC);
        verify(paymentService, times(1)).release(order.getId());
    }

    @Test
    void siLaPasarelaFallaAlCancelarElPedidoSigueEnPendiente() {
        doThrow(new PaymentUnavailableException()).when(paymentService).release(order.getId());

        assertThatThrownBy(() -> service.cancel(customerId, order.getId(), null))
                .isInstanceOf(PaymentUnavailableException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(changes, never()).save(any());
    }

    @Test
    void laPreparacionAvanzaEnOrdenYSinTocarElPago() {
        service.accept(ownerId, order.getId());
        service.startPreparation(ownerId, order.getId());
        service.markReady(ownerId, order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
        verify(paymentService, times(1)).capture(order.getId());
        verify(paymentService, never()).release(any());
        verify(changes, times(3)).save(any());
        verify(events, times(1)).publishEvent(new com.mikuna.backend.order.model.OrderReadyEvent(order.getId()));
    }

    @Test
    void saltosYRetrocesosSeRechazanSinRegistrar() {
        assertThatThrownBy(() -> service.startPreparation(ownerId, order.getId()))
                .isInstanceOf(InvalidOrderTransitionException.class);
        service.accept(ownerId, order.getId());
        assertThatThrownBy(() -> service.markReady(ownerId, order.getId()))
                .isInstanceOf(InvalidOrderTransitionException.class);
        service.startPreparation(ownerId, order.getId());
        assertThatThrownBy(() -> service.startPreparation(ownerId, order.getId()))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(changes, times(2)).save(any());
    }
    // --- Spec 012: vencimiento al decidir ---

    @Test
    void aceptarUnPendienteVencidoLoCancelaPorElSistemaSinCobrar() {
        order.setExpiresAt(Instant.now(CLOCK).minusSeconds(1));

        OrderStatusService.Result result = service.accept(ownerId, order.getId());

        assertThat(result.expired()).isTrue();
        verify(paymentService, never()).capture(any());
        verify(paymentService).release(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        OrderStatusChange change = savedChange();
        assertThat(change.getActorRole()).isEqualTo(ActorRole.SYSTEM);
        assertThat(change.getActorId()).isNull();
        assertThat(change.getReason()).isEqualTo(OrderExpiryPolicy.REASON);
    }

    @Test
    void rechazarUnPendienteVencidoLoCancelaPorElSistema() {
        order.setExpiresAt(Instant.now(CLOCK));

        OrderStatusService.Result result = service.reject(ownerId, order.getId(), "sin stock");

        assertThat(result.expired()).isTrue();
        verify(paymentService, times(1)).release(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(savedChange().getReason()).isEqualTo(OrderExpiryPolicy.REASON);
    }

    @Test
    void aceptarAntesDelVencimientoCobraNormalmente() {
        order.setExpiresAt(Instant.now(CLOCK).plusSeconds(1));

        OrderStatusService.Result result = service.accept(ownerId, order.getId());

        assertThat(result.expired()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void decidirSobreUnPedidoYaVencidoEsOrderExpiredSinTocarElPago() {
        order.setExpiresAt(Instant.now(CLOCK).minusSeconds(60));
        service.expireIfDue(order);
        OrderStatusChange expiredChange = savedChange();
        when(changes.findFirstByOrderIdOrderByChangedAtDesc(order.getId())).thenReturn(Optional.of(expiredChange));

        assertThatThrownBy(() -> service.accept(ownerId, order.getId()))
                .isInstanceOfSatisfying(OrderConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(OrderConflictException.ORDER_EXPIRED));
        assertThatThrownBy(() -> service.reject(ownerId, order.getId(), null))
                .isInstanceOf(OrderConflictException.class);
        verify(paymentService, times(1)).release(order.getId());
        verify(paymentService, never()).capture(any());
    }

    @Test
    void expireIfDueNoHaceNadaSiNoVencioOSiNoEstaPendiente() {
        assertThat(service.expireIfDue(order)).isFalse(); // sin vencimiento fijado
        order.setExpiresAt(Instant.now(CLOCK).plusSeconds(30));
        assertThat(service.expireIfDue(order)).isFalse();
        verify(paymentService, never()).release(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void siLaPasarelaFallaAlVencerNoCambiaElEstado() {
        order.setExpiresAt(Instant.now(CLOCK).minusSeconds(1));
        doThrow(new PaymentUnavailableException()).when(paymentService).release(order.getId());

        assertThatThrownBy(() -> service.expireIfDue(order)).isInstanceOf(PaymentUnavailableException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(changes, never()).save(any());
    }
}
