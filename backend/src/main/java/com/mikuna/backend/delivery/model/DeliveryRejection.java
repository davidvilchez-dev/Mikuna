package com.mikuna.backend.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** Rechazo de una entrega por un repartidor: evita volver a ofrecérsela, automática o manualmente (RF-23). */
@Entity
@Table(name = "rechazos_entrega",
        uniqueConstraints = @UniqueConstraint(name = "uk_rechazos_entrega_repartidor",
                columnNames = {"delivery_id", "courier_id"}))
public class DeliveryRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Column(name = "rejected_at", nullable = false, updatable = false)
    private Instant rejectedAt;

    protected DeliveryRejection() {
        // JPA
    }

    public DeliveryRejection(UUID deliveryId, UUID courierId, Instant rejectedAt) {
        this.deliveryId = deliveryId;
        this.courierId = courierId;
        this.rejectedAt = rejectedAt;
    }

    public UUID getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getCourierId() { return courierId; }
    public Instant getRejectedAt() { return rejectedAt; }
}
