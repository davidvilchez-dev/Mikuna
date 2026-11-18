package com.mikuna.backend.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Entrega de un pedido Listo para recoger: una por pedido y con a lo sumo un repartidor (RNF-05). Sin repartidor
 * está pendiente de asignación; el estado visible es el del pedido. La asignación se hace con una actualización
 * condicional ({@code courier_id IS NULL}), nunca con un setter.
 */
@Entity
@Table(name = "entregas",
        uniqueConstraints = @UniqueConstraint(name = "uk_entregas_pedido", columnNames = "order_id"),
        indexes = {@Index(name = "ix_entregas_repartidor", columnList = "courier_id"),
                @Index(name = "ix_entregas_lista", columnList = "ready_at")})
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "ready_at", nullable = false, updatable = false)
    private Instant readyAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    protected Delivery() {
        // JPA
    }

    public Delivery(UUID orderId, Instant readyAt) {
        this.orderId = orderId;
        this.readyAt = readyAt;
    }

    /** Deja la entrega sin repartidor (rechazo o liberación); el pedido ya está bloqueado por quien llama. */
    public void release() {
        this.courierId = null;
        this.assignedAt = null;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCourierId() { return courierId; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getAssignedAt() { return assignedAt; }
}
