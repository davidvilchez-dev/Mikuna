package com.mikuna.backend.delivery.dto;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.restaurant.model.Restaurant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entrega tal como la ve el repartidor al que está asignada: restaurante, destino, productos, quién recibe y
 * cómo se paga (en efectivo debe cobrar el total al entregar).
 */
public record CourierDeliveryResponse(UUID orderId, String status, Pickup restaurant, Destination delivery,
        List<Line> lines, Instant assignedAt, Payment payment, Receiver receiver) {

    public record Pickup(String name, String address, String zone, String phone, Double latitude, Double longitude) {
    }

    public record Destination(String label, String street, String district, String reference, String phone,
            Double latitude, Double longitude) {
    }

    public record Line(String productName, int quantity) {
    }

    /** Método de pago y monto total del pedido: con CASH es lo que el repartidor cobra al entregar. */
    public record Payment(String method, BigDecimal total) {
    }

    /** Otra persona que recibe el pedido, si el cliente la indicó. */
    public record Receiver(String name, String phone, String note) {
    }

    public static CourierDeliveryResponse of(Order order, Delivery delivery, Restaurant place) {
        Receiver receiver = order.getReceiverName() == null ? null
                : new Receiver(order.getReceiverName(), order.getReceiverPhone(), order.getReceiverNote());
        return new CourierDeliveryResponse(order.getId(), order.getStatus().name(),
                new Pickup(order.getRestaurantName(), place == null ? null : place.getAddress(),
                        place == null ? null : place.getZone(), place == null ? null : place.getPhone(),
                        place == null ? null : place.getLatitude(), place == null ? null : place.getLongitude()),
                new Destination(order.getDeliveryLabel(), order.getDeliveryStreet(), order.getDeliveryDistrict(),
                        order.getDeliveryReference(), order.getDeliveryPhone(), order.getDeliveryLatitude(),
                        order.getDeliveryLongitude()),
                order.getLines().stream().map(l -> new Line(l.getProductName(), l.getQuantity())).toList(),
                delivery.getAssignedAt(), new Payment(order.getPaymentMethod().name(), order.getTotal()), receiver);
    }
}
