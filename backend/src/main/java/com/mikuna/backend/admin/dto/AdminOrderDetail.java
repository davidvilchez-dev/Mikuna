package com.mikuna.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalle administrativo de un pedido: partes, productos con los precios fijados al confirmar, importes, pago,
 * dirección de entrega, cambios de estado y transacciones de pago en orden cronológico.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminOrderDetail(UUID id, String status, Instant createdAt, AdminOrderSummary.Party customer,
        AdminOrderSummary.Party restaurant, AdminOrderSummary.Party courier, UUID deliveryId, List<Line> lines,
        Amounts amounts, PaymentInfo payment, DeliveryAddress deliveryAddress,
        List<StatusChangeEntry> statusChanges, List<Transaction> transactions) {

    /** Transacción de pago del pedido (spec 014): reserva, cobro, liberación o reembolso, con su resultado. */
    public record Transaction(String type, String status, BigDecimal amount, String code, Instant at) {
    }

    public record Line(UUID productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record Amounts(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal commission, BigDecimal total) {
    }

    public record PaymentInfo(String method, String status, BigDecimal amount, String currency) {
    }

    public record DeliveryAddress(String street, String district, String reference, String phone) {
    }
}
