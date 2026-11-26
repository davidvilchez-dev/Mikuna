package com.mikuna.backend.order.service;

import com.mikuna.backend.common.exception.InvalidOrderTransitionException;
import com.mikuna.backend.common.exception.OrderConflictException;
import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.common.exception.RestaurantNotEnabledException;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderReadyEvent;
import com.mikuna.backend.order.model.OrderStatusChangedEvent;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChange;
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
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambios de estado del pedido (RF-18, RF-19, RF-20). Cada operación es una única transacción que bloquea la
 * fila del pedido antes que la del pago (orden fijo, research §1), valida el estado con la fila bloqueada,
 * invoca el pago cuando corresponde y registra el cambio. Si el pago falla la excepción revierte todo y el
 * pedido conserva su estado. El restaurante y el cliente salen siempre del token, nunca de la petición.
 */
@Service
public class OrderStatusService {

    static final String EXPIRED_REASON = "reserva vencida";

    /**
     * Pedido resultante con el estado de su pago; {@code reservationExpired} solo al aceptar sin reserva y
     * {@code expired} cuando la decisión llegó con el plazo de respuesta ya vencido (spec 012).
     */
    public record Result(Order order, PaymentStatus payment, boolean reservationExpired, boolean expired) {

        public Result(Order order, PaymentStatus payment, boolean reservationExpired) {
            this(order, payment, reservationExpired, false);
        }
    }

    private final OrderRepository orders;
    private final OrderStatusChangeRepository changes;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final RestaurantRepository restaurants;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public OrderStatusService(OrderRepository orders, OrderStatusChangeRepository changes,
            PaymentRepository payments, PaymentService paymentService, RestaurantRepository restaurants,
            Clock clock, ApplicationEventPublisher events) {
        this.orders = orders;
        this.changes = changes;
        this.payments = payments;
        this.paymentService = paymentService;
        this.restaurants = restaurants;
        this.clock = clock;
        this.events = events;
    }

    /** Pedidos del restaurante propio en el estado indicado, los más antiguos primero (FR-001). */
    @Transactional(readOnly = true)
    public List<Result> list(UUID accountId, OrderStatus status) {
        Restaurant restaurant = enabledRestaurant(accountId);
        return orders.findByRestaurantIdAndStatusOrderByCreatedAtAsc(restaurant.getId(), status).stream()
                .map(order -> result(order, false)).toList();
    }

    @Transactional(readOnly = true)
    public Result get(UUID accountId, UUID orderId) {
        Restaurant restaurant = enabledRestaurant(accountId);
        Order order = orders.findById(orderId).filter(o -> o.getRestaurantId().equals(restaurant.getId()))
                .orElseThrow(OrderNotFoundException::new);
        return result(order, false);
    }

    /** PENDING a ACCEPTED con cobro; si la reserva venció, el sistema cancela el pedido sin cobro (FR-011a). */
    @Transactional
    public Result accept(UUID accountId, UUID orderId) {
        Order order = lockOwnedByRestaurant(accountId, orderId);
        if (expireIfDue(order)) {
            return expiredResult(order);
        }
        requirePending(order);
        if (paymentService.capture(order.getId()) == CaptureResult.RESERVATION_EXPIRED) {
            changeStatus(order, OrderStatus.CANCELLED, null, ActorRole.SYSTEM, EXPIRED_REASON);
            return result(order, true);
        }
        changeStatus(order, OrderStatus.ACCEPTED, accountId, ActorRole.RESTAURANT, null);
        return result(order, false);
    }

    /** PENDING a REJECTED con liberación de la reserva, sin cobro. */
    @Transactional
    public Result reject(UUID accountId, UUID orderId, String reason) {
        Order order = lockOwnedByRestaurant(accountId, orderId);
        if (expireIfDue(order)) {
            return expiredResult(order);
        }
        requirePending(order);
        paymentService.release(order.getId());
        changeStatus(order, OrderStatus.REJECTED, accountId, ActorRole.RESTAURANT, reason);
        return result(order, false);
    }

    /** PENDING a CANCELLED por el cliente dueño, con liberación de la reserva, sin cobro. */
    @Transactional
    public Result cancel(UUID customerId, UUID orderId, String reason) {
        Order order = orders.findByIdForUpdate(orderId).filter(o -> o.getCustomerId().equals(customerId))
                .orElseThrow(OrderNotFoundException::new);
        OrderStatus current = order.getStatus();
        if (current != OrderStatus.PENDING && current != OrderStatus.REJECTED && current != OrderStatus.CANCELLED) {
            throw new InvalidOrderTransitionException(InvalidOrderTransitionException.NO_REFUND);
        }
        requirePending(order);
        paymentService.release(order.getId());
        changeStatus(order, OrderStatus.CANCELLED, customerId, ActorRole.CUSTOMER, reason);
        return result(order, false);
    }

    /** ACCEPTED a PREPARING; no toca el pago. */
    @Transactional
    public Result startPreparation(UUID accountId, UUID orderId) {
        return advance(accountId, orderId, OrderStatus.PREPARING);
    }

    /** PREPARING a READY; publica el evento que crea la entrega para la asignación de repartidor (spec 008). */
    @Transactional
    public Result markReady(UUID accountId, UUID orderId) {
        Result result = advance(accountId, orderId, OrderStatus.READY);
        events.publishEvent(new OrderReadyEvent(orderId));
        return result;
    }

    private Result advance(UUID accountId, UUID orderId, OrderStatus target) {
        Order order = lockOwnedByRestaurant(accountId, orderId);
        changeStatus(order, target, accountId, ActorRole.RESTAURANT, null);
        return result(order, false);
    }

    /**
     * Vencimiento de un pedido cuya fila ya bloqueó quien llama (spec 012): si sigue Pendiente y su plazo terminó,
     * libera la reserva (pedido y luego pago, el mismo orden de bloqueo que el resto) y lo pasa a Cancelado por el
     * sistema. Devuelve si venció. Si la pasarela falla, la excepción revierte todo y el pedido sigue Pendiente.
     */
    public boolean expireIfDue(Order order) {
        if (!order.isExpiredAt(Instant.now(clock))) {
            return false;
        }
        paymentService.release(order.getId());
        changeStatus(order, OrderStatus.CANCELLED, null, ActorRole.SYSTEM, OrderExpiryPolicy.REASON);
        return true;
    }

    /** Decidir sobre un pedido que acaba de vencer o que ya había vencido responde lo mismo (FR-014). */
    private Result expiredResult(Order order) {
        return new Result(order, paymentStatus(order), false, true);
    }

    private void requirePending(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED && changes.findFirstByOrderIdOrderByChangedAtDesc(order.getId())
                .map(change -> OrderExpiryPolicy.REASON.equals(change.getReason())).orElse(false)) {
            throw new OrderConflictException(OrderConflictException.ORDER_EXPIRED,
                    OrderConflictException.EXPIRED_MESSAGE);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderTransitionException();
        }
    }

    /** Aplica la transición validada y deja constancia del cambio en la misma transacción (FR-012). */
    public void changeStatus(Order order, OrderStatus target, UUID actorId, ActorRole role, String reason) {
        OrderStatus from = order.getStatus();
        order.transitionTo(target);
        orders.save(order);
        OrderStatusChange change = new OrderStatusChange(order.getId(), from, target, changedAt(order.getId()), actorId,
                role, normalize(reason));
        changes.save(change);
        events.publishEvent(new OrderStatusChangedEvent(change.getId(), order.getId(), from, target, actorId, role,
                change.getReason()));
    }

    /**
     * Momento del cambio, estrictamente posterior al último cambio del mismo pedido. Si el reloj del sistema retrocede
     * (por ejemplo, la sincronización de hora al reanudar un equipo), el historial conserva el orden real en que
     * ocurrieron los cambios. Quien llama ya bloqueó el pedido, así que el último cambio leído es el vigente. Se trunca
     * a microsegundos, la precisión con la que PostgreSQL guarda la hora.
     */
    private Instant changedAt(UUID orderId) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return changes.findFirstByOrderIdOrderByChangedAtDesc(orderId).map(OrderStatusChange::getChangedAt)
                .filter(last -> !now.isAfter(last)).map(last -> last.plus(1, ChronoUnit.MICROS)).orElse(now);
    }

    private static String normalize(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.strip();
    }

    private Restaurant enabledRestaurant(UUID accountId) {
        Restaurant restaurant = restaurants.findByOwnerId(accountId).orElseThrow(RestaurantNotEnabledException::new);
        if (restaurant.getStatus() != RestaurantStatus.APPROVED) {
            throw new RestaurantNotEnabledException();
        }
        return restaurant;
    }

    /** Bloquea el pedido y comprueba que sea del restaurante del token; ajeno o inexistente es 404. */
    private Order lockOwnedByRestaurant(UUID accountId, UUID orderId) {
        Restaurant restaurant = enabledRestaurant(accountId);
        return orders.findByIdForUpdate(orderId).filter(o -> o.getRestaurantId().equals(restaurant.getId()))
                .orElseThrow(OrderNotFoundException::new);
    }

    private Result result(Order order, boolean reservationExpired) {
        return new Result(order, paymentStatus(order), reservationExpired);
    }

    private PaymentStatus paymentStatus(Order order) {
        order.getLines().size();
        return payments.findByOrderId(order.getId()).map(Payment::getStatus).orElse(PaymentStatus.AUTHORIZED);
    }
}
