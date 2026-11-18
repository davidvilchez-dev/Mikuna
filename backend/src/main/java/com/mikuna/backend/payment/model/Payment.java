package com.mikuna.backend.payment.model;

import com.mikuna.backend.common.exception.PaymentStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Pago de un pedido (RF-16): nace {@code AUTHORIZED} (monto reservado) y termina {@code CAPTURED} o
 * {@code RELEASED}, estados terminales y excluyentes. No existe estado de reembolso.
 */
@Entity
@Table(name = "pagos", uniqueConstraints = @UniqueConstraint(name = "uk_pagos_pedido", columnNames = "order_id"))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "gateway_ref", nullable = false, updatable = false)
    private String gatewayRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // JPA
    }

    public static Payment authorized(UUID orderId, UUID customerId, BigDecimal amount, String currency,
            String gatewayRef, Instant now) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.customerId = customerId;
        payment.status = PaymentStatus.AUTHORIZED;
        payment.amount = amount;
        payment.currency = currency;
        payment.gatewayRef = gatewayRef;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /** Pasa de AUTHORIZED a CAPTURED; cualquier otro estado de origen se rechaza. */
    public void capture(Instant now) {
        transition(PaymentStatus.CAPTURED, now);
    }

    /** Pasa de AUTHORIZED a RELEASED; cualquier otro estado de origen se rechaza. */
    public void release(Instant now) {
        transition(PaymentStatus.RELEASED, now);
    }

    private void transition(PaymentStatus target, Instant now) {
        if (status != PaymentStatus.AUTHORIZED) {
            throw new PaymentStateException();
        }
        status = target;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getGatewayRef() { return gatewayRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
