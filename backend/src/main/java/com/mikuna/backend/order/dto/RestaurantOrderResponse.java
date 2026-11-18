package com.mikuna.backend.order.dto;

import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.payment.model.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedido tal como lo ve el restaurante propietario: estado, líneas, entrega, importes y estado del pago. */
public record RestaurantOrderResponse(UUID id, String status, List<OrderResponse.Line> lines,
        OrderResponse.Delivery delivery, OrderResponse.Totals totals, PaymentInfo payment, Instant createdAt) {

    public record PaymentInfo(String status) {
    }

    public static RestaurantOrderResponse of(Order order, PaymentStatus paymentStatus) {
        OrderResponse full = OrderResponse.of(order, paymentStatus);
        return new RestaurantOrderResponse(full.id(), full.status(), full.lines(), full.delivery(), full.totals(),
                new PaymentInfo(paymentStatus.name()), full.createdAt());
    }
}
