package com.mikuna.backend.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de un intento de pago (RF-17): autorización, cobro o liberación, exitoso o fallido. Guarda solo
 * códigos y mensajes seguros y la referencia de la pasarela; nunca datos de tarjeta.
 */
@Entity
@Table(name = "transacciones_pago", indexes = {
        @Index(name = "ix_transacciones_pedido", columnList = "order_id"),
        @Index(name = "ix_transacciones_clave", columnList = "customer_id, confirmation_key")})
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nulo si la autorización falló y no hubo pedido. */
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "confirmation_key", length = 64)
    private String confirmationKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "result_message")
    private String resultMessage;

    @Column(name = "gateway_ref")
    private String gatewayRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentTransaction() {
        // JPA
    }

    public PaymentTransaction(UUID orderId, UUID customerId, String confirmationKey, TransactionType type,
            TransactionStatus status, BigDecimal amount, String resultCode, String resultMessage,
            String gatewayRef, Instant createdAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.confirmationKey = confirmationKey;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.gatewayRef = gatewayRef;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public String getConfirmationKey() { return confirmationKey; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getResultCode() { return resultCode; }
    public String getResultMessage() { return resultMessage; }
    public String getGatewayRef() { return gatewayRef; }
    public Instant getCreatedAt() { return createdAt; }
}
