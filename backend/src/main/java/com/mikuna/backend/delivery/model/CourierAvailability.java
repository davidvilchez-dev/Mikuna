package com.mikuna.backend.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Disponibilidad de un repartidor para recibir entregas (RF-21). La ausencia de fila equivale a no disponible.
 * {@code freeSince} indica desde cuándo está libre y ordena a los candidatos (el que lleva más tiempo libre primero).
 */
@Entity
@Table(name = "repartidores_disponibilidad")
public class CourierAvailability {

    @Id
    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "free_since", nullable = false)
    private Instant freeSince;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourierAvailability() {
        // JPA
    }

    public CourierAvailability(UUID courierId, Instant now) {
        this.courierId = courierId;
        this.available = false;
        this.freeSince = now;
        this.updatedAt = now;
    }

    /** Pasa a disponible; «libre desde» solo se reinicia si antes no lo estaba. */
    public void setAvailable(boolean value, Instant now) {
        if (value && !available) {
            this.freeSince = now;
        }
        this.available = value;
        this.updatedAt = now;
    }

    /** Termina una entrega: vuelve a contar como libre desde ahora. */
    public void markFree(Instant now) {
        this.freeSince = now;
        this.updatedAt = now;
    }

    public UUID getCourierId() { return courierId; }
    public boolean isAvailable() { return available; }
    public Instant getFreeSince() { return freeSince; }
    public Instant getUpdatedAt() { return updatedAt; }
}
