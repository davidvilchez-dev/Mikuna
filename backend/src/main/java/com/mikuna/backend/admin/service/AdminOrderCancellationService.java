package com.mikuna.backend.admin.service;

import com.mikuna.backend.admin.dto.AdminOrderDetail;
import com.mikuna.backend.common.exception.InvalidOrderTransitionException;
import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.delivery.service.DeliveryAdminService;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.service.OrderStatusService;
import com.mikuna.backend.payment.service.PaymentService;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancelación administrativa de un pedido aceptado y no entregado (spec 014). Una sola transacción con orden de
 * bloqueo fijo pedido → pago → repartidor (research §2): si la pasarela no reembolsa, la excepción revierte todo y el
 * pedido conserva su estado. Avanzar el pedido (restaurante o repartidor) bloquea la misma fila primero, así que
 * solo una de las dos acciones prevalece.
 */
@Service
public class AdminOrderCancellationService {

    static final Set<OrderStatus> CANCELLABLE = EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING,
            OrderStatus.READY, OrderStatus.ASSIGNED, OrderStatus.PICKED_UP);
    private static final Set<OrderStatus> WITH_COURIER = EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP);

    private final OrderRepository orders;
    private final PaymentService payments;
    private final OrderStatusService orderStatus;
    private final DeliveryAdminService deliveries;
    private final AdminOrderService queries;

    public AdminOrderCancellationService(OrderRepository orders, PaymentService payments,
            OrderStatusService orderStatus, DeliveryAdminService deliveries, AdminOrderService queries) {
        this.orders = orders;
        this.payments = payments;
        this.orderStatus = orderStatus;
        this.deliveries = deliveries;
        this.queries = queries;
    }

    @Transactional
    public AdminOrderDetail cancel(UUID adminId, UUID orderId, String reason) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
        OrderStatus from = order.getStatus();
        if (!CANCELLABLE.contains(from)) {
            throw new InvalidOrderTransitionException();
        }
        payments.refundOrVoid(orderId);
        orderStatus.changeStatus(order, OrderStatus.CANCELLED, adminId, ActorRole.ADMIN, reason.strip());
        if (WITH_COURIER.contains(from)) {
            deliveries.freeCourierOfCancelledOrder(orderId);
        }
        return queries.orderDetail(orderId);
    }
}
