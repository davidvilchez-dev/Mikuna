package com.mikuna.backend.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de un cambio de estado de un pedido (FR-012): solo se inserta junto con un cambio efectivo y no se
 * modifica. {@code actorId} es nulo cuando el responsable es el sistema.
 */
@Entity
@Table(name = "cambios_estado_pedido",
        indexes = @Index(name = "ix_cambios_pedido_fecha", columnList = "order_id, changed_at"))
public class OrderStatusChange {

    public static final int REASON_MAX = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 16)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 16)
    private OrderStatus toStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, updatable = false, length = 16)
    private ActorRole actorRole;

    @Column(updatable = false, length = REASON_MAX)
    private String reason;

    protected OrderStatusChange() {
        // JPA
    }

    public OrderStatusChange(UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, Instant changedAt,
            UUID actorId, ActorRole actorRole, String reason) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
    public UUID getActorId() { return actorId; }
    public ActorRole getActorRole() { return actorRole; }
    public String getReason() { return reason; }
}
