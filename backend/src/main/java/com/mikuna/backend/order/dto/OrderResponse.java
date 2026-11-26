package com.mikuna.backend.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderLine;
import com.mikuna.backend.order.service.OrderExpiryPolicy;
import com.mikuna.backend.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedido con sus datos fijados al confirmar y el estado de su pago. No expone referencias de la pasarela. */
public record OrderResponse(UUID id, String status, RestaurantInfo restaurant, List<Line> lines, Delivery delivery,
        Totals totals, PaymentInfo payment, Instant createdAt,
        Options options, Instant expiresAt, Long secondsToExpire,
        @JsonInclude(JsonInclude.Include.NON_NULL) Cancellation cancellation) {

    /** Quién canceló el pedido ({@code CUSTOMER}, {@code RESTAURANT}, {@code SYSTEM} o {@code ADMIN}) y su motivo. */
    public record Cancellation(String by, String reason) {
    }

    public record RestaurantInfo(UUID id, String name) {
    }

    public record Line(UUID productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record Delivery(String label, String street, String district, String reference, String contactPhone) {
    }

    public record Totals(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal commission, BigDecimal total) {
    }

    /** Nota para la tienda, horario elegido ({@code null} es inmediato) y receptor ({@code null} es el cliente). */
    public record Options(String storeNote, Instant scheduledFor, Receiver receiver) {
    }

    public record Receiver(String name, String phone, String note) {
    }

    public record PaymentInfo(String method, String status) {
    }

    /**
     * {@code expiresAt} es el vencimiento fijado al crear; {@code secondsToExpire} son los segundos restantes en
     * {@code now}, solo mientras está Pendiente (spec 012), para que la interfaz no dependa de su propio reloj.
     */
    public static OrderResponse of(Order order, PaymentStatus paymentStatus, Instant now) {
        return of(order, paymentStatus, now, null);
    }

    /** Con la cancelación (spec 014): solo se informa si el pedido está Cancelado. */
    public static OrderResponse of(Order order, PaymentStatus paymentStatus, Instant now, Cancellation cancellation) {
        List<Line> lines = order.getLines().stream().map(OrderResponse::line).toList();
        return new OrderResponse(order.getId(), order.getStatus().name(),
                new RestaurantInfo(order.getRestaurantId(), order.getRestaurantName()), lines,
                new Delivery(order.getDeliveryLabel(), order.getDeliveryStreet(), order.getDeliveryDistrict(),
                        order.getDeliveryReference(), order.getDeliveryPhone()),
                new Totals(order.getSubtotal(), order.getDeliveryFee(), order.getCommission(), order.getTotal()),
                new PaymentInfo(order.getPaymentMethod().name(), paymentStatus.name()), order.getCreatedAt(),
                options(order), order.getExpiresAt(), OrderExpiryPolicy.secondsToExpire(order, now),
                order.getStatus() == OrderStatus.CANCELLED ? cancellation : null);
    }

    private static Options options(Order order) {
        Receiver receiver = order.getReceiverName() == null ? null
                : new Receiver(order.getReceiverName(), order.getReceiverPhone(), order.getReceiverNote());
        return new Options(order.getStoreNote(), order.getScheduledFor(), receiver);
    }

    private static Line line(OrderLine line) {
        return new Line(line.getProductId(), line.getProductName(), line.getUnitPrice(), line.getQuantity(),
                line.getLineTotal());
    }
}
