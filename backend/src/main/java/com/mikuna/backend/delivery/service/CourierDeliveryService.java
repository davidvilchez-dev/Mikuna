package com.mikuna.backend.delivery.service;

import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.delivery.dto.CourierDeliveryResponse;
import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.model.DeliveryRejection;
import com.mikuna.backend.delivery.repository.CourierAvailabilityRepository;
import com.mikuna.backend.delivery.repository.DeliveryRejectionRepository;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.service.OrderStatusService;
import com.mikuna.backend.payment.service.PaymentService;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operaciones del repartidor sobre su entrega (RF-25, RF-23). Cada una bloquea el pedido, comprueba que la entrega
 * es del repartidor del token (ajena o inexistente: 404) y deja que la transición validada del pedido rechace los
 * saltos, retrocesos y reenvíos (409). Lo que libera un repartidor o una entrega se despacha tras el commit.
 */
@Service
public class CourierDeliveryService {

    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP);

    private final OrderRepository orders;
    private final DeliveryRepository deliveries;
    private final DeliveryRejectionRepository rejections;
    private final CourierAvailabilityRepository availability;
    private final OrderStatusService orderStatus;
    private final RestaurantRepository restaurants;
    private final DeliveryDispatcher dispatcher;
    private final PaymentService paymentService;
    private final Clock clock;

    public CourierDeliveryService(OrderRepository orders, DeliveryRepository deliveries,
            DeliveryRejectionRepository rejections, CourierAvailabilityRepository availability,
            OrderStatusService orderStatus, RestaurantRepository restaurants, DeliveryDispatcher dispatcher,
            PaymentService paymentService, Clock clock) {
        this.paymentService = paymentService;
        this.orders = orders;
        this.deliveries = deliveries;
        this.rejections = rejections;
        this.availability = availability;
        this.orderStatus = orderStatus;
        this.restaurants = restaurants;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    /** Entregas activas propias (Asignada o Recogida): a lo sumo una (FR-005). */
    @Transactional(readOnly = true)
    public List<CourierDeliveryResponse> listActive(UUID courierId) {
        return deliveries.findActiveByCourier(courierId, ACTIVE).stream()
                .map(d -> orders.findById(d.getOrderId()).map(o -> CourierDeliveryResponse.of(o, d, place(o))).orElse(null))
                .filter(r -> r != null).toList();
    }

    /** ASSIGNED a PICKED_UP: el repartidor acepta y registra que retiró el pedido (FR-006). */
    @Transactional
    public CourierDeliveryResponse pickup(UUID courierId, UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
        Delivery delivery = ownDelivery(courierId, orderId);
        orderStatus.changeStatus(order, OrderStatus.PICKED_UP, courierId, ActorRole.COURIER, null);
        return CourierDeliveryResponse.of(order, delivery, place(order));
    }

    /** PICKED_UP a DELIVERED; el repartidor queda libre y se intenta asignarle la siguiente pendiente (FR-007). */
    @Transactional
    public CourierDeliveryResponse complete(UUID courierId, UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
        Delivery delivery = ownDelivery(courierId, orderId);
        orderStatus.changeStatus(order, OrderStatus.DELIVERED, courierId, ActorRole.COURIER, null);
        // Spec 014: el efectivo se cobra en la entrega (orden de bloqueo pedido → pago → repartidor).
        paymentService.collectCash(orderId);
        availability.findByIdForUpdate(courierId).ifPresent(row -> {
            row.markFree(Instant.now(clock));
            availability.save(row);
        });
        AfterCommit.run(() -> dispatcher.courierBecameFree(courierId));
        return CourierDeliveryResponse.of(order, delivery, place(order));
    }

    /**
     * ASSIGNED a READY por rechazo (FR-008): queda constancia del rechazo, la entrega pierde al repartidor y, tras
     * el commit, se reasigna a otro candidato sin volver a ofrecérsela a quien la rechazó. El pedido sigue en curso.
     */
    @Transactional
    public CourierDeliveryResponse reject(UUID courierId, UUID orderId, String reason) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
        Delivery delivery = ownDelivery(courierId, orderId);
        orderStatus.changeStatus(order, OrderStatus.READY, courierId, ActorRole.COURIER, reason);
        rejections.save(new DeliveryRejection(delivery.getId(), courierId, Instant.now(clock)));
        delivery.release();
        AfterCommit.run(() -> {
            dispatcher.dispatch(orderId);
            // Quien rechaza queda libre: se le ofrece otra pendiente (nunca la que rechazó).
            dispatcher.courierBecameFree(courierId);
        });
        return CourierDeliveryResponse.of(order, delivery, place(order));
    }

    /** Datos del local para recoger el pedido; sin ellos (restaurante borrado) la entrega sigue con solo el nombre. */
    private Restaurant place(Order order) {
        return restaurants.findById(order.getRestaurantId()).orElse(null);
    }

    private Delivery ownDelivery(UUID courierId, UUID orderId) {
        return deliveries.findByOrderId(orderId).filter(d -> courierId.equals(d.getCourierId()))
                .orElseThrow(OrderNotFoundException::new);
    }
}
