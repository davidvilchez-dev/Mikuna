package com.mikuna.backend.delivery.dto;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.restaurant.model.Restaurant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Entrega tal como la ve el repartidor al que está asignada: restaurante, destino y productos. */
public record CourierDeliveryResponse(UUID orderId, String status, Pickup restaurant, Destination delivery,
        List<Line> lines, Instant assignedAt) {

    public record Pickup(String name, String address, String zone, String phone) {
    }

    public record Destination(String label, String street, String district, String reference, String phone,
            Double latitude, Double longitude) {
    }

    public record Line(String productName, int quantity) {
    }

    public static CourierDeliveryResponse of(Order order, Delivery delivery, Restaurant place) {
        return new CourierDeliveryResponse(order.getId(), order.getStatus().name(),
                new Pickup(order.getRestaurantName(), place == null ? null : place.getAddress(),
                        place == null ? null : place.getZone(), place == null ? null : place.getPhone()),
                new Destination(order.getDeliveryLabel(), order.getDeliveryStreet(), order.getDeliveryDistrict(),
                        order.getDeliveryReference(), order.getDeliveryPhone(), order.getDeliveryLatitude(),
                        order.getDeliveryLongitude()),
                order.getLines().stream().map(l -> new Line(l.getProductName(), l.getQuantity())).toList(),
                delivery.getAssignedAt());
    }
}
