package com.mikuna.backend.cart.dto;

import com.mikuna.backend.restaurant.model.AttentionStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Carrito del cliente con sus importes ya recalculados con los precios vigentes. */
public record CartResponse(RestaurantInfo restaurant, List<Line> lines, AddressInfo address, TotalsResponse totals,
        List<String> notices) {

    public record RestaurantInfo(UUID id, String name, AttentionStatus attention) {
    }

    public record AddressInfo(UUID id, String street, String district, Double latitude, Double longitude) {
    }

    /** {@code lineTotal} es {@code null} si la línea no es elegible; {@code reason} explica por qué. */
    public record Line(UUID id, UUID productId, String name, String imageUrl, BigDecimal unitPrice, int quantity,
            BigDecimal lineTotal, boolean available, String reason) {
    }
}
