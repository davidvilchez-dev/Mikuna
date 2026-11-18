package com.mikuna.backend.order.dto;

import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderLine;
import com.mikuna.backend.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedido con sus datos fijados al confirmar y el estado de su pago. No expone referencias de la pasarela. */
public record OrderResponse(UUID id, String status, RestaurantInfo restaurant, List<Line> lines, Delivery delivery,
        Totals totals, PaymentInfo payment, Instant createdAt) {

    public record RestaurantInfo(UUID id, String name) {
    }

    public record Line(UUID productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record Delivery(String label, String street, String district, String reference, String contactPhone) {
    }

    public record Totals(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal commission, BigDecimal total) {
    }

    public record PaymentInfo(String method, String status) {
    }

    public static OrderResponse of(Order order, PaymentStatus paymentStatus) {
        List<Line> lines = order.getLines().stream().map(OrderResponse::line).toList();
        return new OrderResponse(order.getId(), order.getStatus().name(),
                new RestaurantInfo(order.getRestaurantId(), order.getRestaurantName()), lines,
                new Delivery(order.getDeliveryLabel(), order.getDeliveryStreet(), order.getDeliveryDistrict(),
                        order.getDeliveryReference(), order.getDeliveryPhone()),
                new Totals(order.getSubtotal(), order.getDeliveryFee(), order.getCommission(), order.getTotal()),
                new PaymentInfo(order.getPaymentMethod().name(), paymentStatus.name()), order.getCreatedAt());
    }

    private static Line line(OrderLine line) {
        return new Line(line.getProductId(), line.getProductName(), line.getUnitPrice(), line.getQuantity(),
                line.getLineTotal());
    }
}
