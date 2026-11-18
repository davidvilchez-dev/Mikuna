package com.mikuna.backend.history.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una operación del historial. Los campos que el rol no debe ver quedan nulos y no se serializan (FR-016, FR-017):
 * el restaurante no recibe {@code restaurant}; el repartidor no recibe {@code payment}, {@code totals} ni los precios
 * de las líneas, y sí {@code deliveryAddress} y {@code deliveryFee}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryItem(UUID orderId, String status, Instant createdAt, RestaurantInfo restaurant,
        String customerName, DeliveryAddress deliveryAddress, List<Line> lines, Totals totals,
        BigDecimal deliveryFee, PaymentInfo payment, Integer rating, boolean canRate) {

    public record RestaurantInfo(UUID id, String name) {
    }

    /** Dirección de entrega sin teléfono ni datos de contacto. */
    public record DeliveryAddress(String street, String district, String reference) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(UUID productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record Totals(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal commission, BigDecimal total) {
    }

    public record PaymentInfo(String method, String status) {
    }
}
