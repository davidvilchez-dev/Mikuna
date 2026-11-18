package com.mikuna.backend.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Ubicación vigente de un repartidor con una entrega en curso (RF-26): una sola fila por repartidor, reemplazada
 * en cada envío y sin historial (FR-006). Solo se escribe con el {@code upsert} condicional del repositorio, que
 * conserva la ubicación más reciente aunque lleguen peticiones desordenadas.
 */
@Entity
@Table(name = "ubicaciones_repartidor")
public class CourierLocation {

    @Id
    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected CourierLocation() {
        // JPA
    }

    public UUID getCourierId() { return courierId; }
    public UUID getOrderId() { return orderId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getRecordedAt() { return recordedAt; }
}
