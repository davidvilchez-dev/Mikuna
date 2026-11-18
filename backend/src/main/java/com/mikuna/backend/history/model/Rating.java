package com.mikuna.backend.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Check;

/**
 * Calificación de 1 a 5 de un pedido entregado (RF-31). Una por pedido: la unicidad de {@code order_id} en
 * PostgreSQL es la garantía ante solicitudes simultáneas. Es inmutable: ninguna columna es actualizable.
 */
@Entity
@Table(name = "calificaciones",
        uniqueConstraints = @UniqueConstraint(name = "uk_calificaciones_pedido", columnNames = "order_id"))
@Check(constraints = "value between 1 and 5")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "value", nullable = false, updatable = false)
    private int value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Rating() {
        // JPA
    }

    public Rating(UUID orderId, UUID customerId, int value, Instant createdAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.value = value;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public int getValue() { return value; }
    public Instant getCreatedAt() { return createdAt; }
}
