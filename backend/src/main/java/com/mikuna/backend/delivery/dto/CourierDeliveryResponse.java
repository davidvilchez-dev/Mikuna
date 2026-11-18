package com.mikuna.backend.delivery.dto;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.order.model.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Entrega tal como la ve el repartidor al que está asignada: restaurante, destino y productos. */
public record CourierDeliveryResponse(UUID orderId, String status, Restaurant restaurant, Destination delivery,
        List<Line> lines, Instant assignedAt) {

    public record Restaurant(String name) {
    }

    public record Destination(String label, String street, String district, String reference, String phone) {
    }

    public record Line(String productName, int quantity) {
    }

    public static CourierDeliveryResponse of(Order order, Delivery delivery) {
        return new CourierDeliveryResponse(order.getId(), order.getStatus().name(),
                new Restaurant(order.getRestaurantName()),
                new Destination(order.getDeliveryLabel(), order.getDeliveryStreet(), order.getDeliveryDistrict(),
                        order.getDeliveryReference(), order.getDeliveryPhone()),
                order.getLines().stream().map(l -> new Line(l.getProductName(), l.getQuantity())).toList(),
                delivery.getAssignedAt());
    }
}
