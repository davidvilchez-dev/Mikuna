package com.mikuna.backend.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Ubicación permanente del repartidor al recoger y al entregar el pedido (FR-006). Solo se inserta, una vez por
 * pedido y tipo.
 */
@Entity
@Table(name = "hitos_entrega",
        uniqueConstraints = @UniqueConstraint(name = "uk_hitos_pedido_tipo", columnNames = {"order_id", "type"}))
public class DeliveryMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private MilestoneType type;

    @Column(nullable = false, updatable = false)
    private double latitude;

    @Column(nullable = false, updatable = false)
    private double longitude;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DeliveryMilestone() {
        // JPA
    }

    public DeliveryMilestone(UUID orderId, MilestoneType type, double latitude, double longitude,
            Instant recordedAt, Instant createdAt) {
        this.orderId = orderId;
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public MilestoneType getType() { return type; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
