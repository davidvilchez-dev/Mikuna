package com.mikuna.backend.cart.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importes del carrito en soles, siempre con dos decimales. {@code pending} es {@code null} si todo está
 * calculado, {@code ADDRESS} si falta la dirección (envío, comisión y total quedan en {@code null}) o
 * {@code EMPTY} si no hay líneas elegibles (todo es 0.00).
 */
public record TotalsResponse(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal commission, BigDecimal total,
        String pending) {

    public static final String PENDING_ADDRESS = "ADDRESS";
    public static final String PENDING_EMPTY = "EMPTY";

    public TotalsResponse {
        subtotal = scale(subtotal);
        deliveryFee = scale(deliveryFee);
        commission = scale(commission);
        total = scale(total);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
