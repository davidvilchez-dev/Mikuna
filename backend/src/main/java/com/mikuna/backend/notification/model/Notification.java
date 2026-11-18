package com.mikuna.backend.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Aviso dirigido a una cuenta por un cambio del pedido o la entrega (RF-29). {@code sourceKey} identifica el
 * cambio que lo originó: la restricción única con el destinatario impide duplicados (FR-014). {@code readAt}
 * nulo significa no leído; los avisos no se borran (FR-016).
 */
@Entity
@Table(name = "avisos",
        uniqueConstraints = @UniqueConstraint(name = "uk_avisos_origen_destinatario",
                columnNames = {"source_key", "recipient_id"}),
        indexes = {@Index(name = "ix_avisos_destinatario_fecha", columnList = "recipient_id, created_at DESC"),
                @Index(name = "ix_avisos_destinatario_leido", columnList = "recipient_id, read_at")})
public class Notification {

    public static final int MESSAGE_MAX = 200;
    public static final int SOURCE_KEY_MAX = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private NotificationType type;

    @Column(nullable = false, updatable = false, length = MESSAGE_MAX)
    private String message;

    @Column(name = "source_key", nullable = false, updatable = false, length = SOURCE_KEY_MAX)
    private String sourceKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // JPA
    }

    public Notification(UUID recipientId, UUID orderId, NotificationType type, String message, String sourceKey,
            Instant createdAt) {
        this.recipientId = recipientId;
        this.orderId = orderId;
        this.type = type;
        this.message = message;
        this.sourceKey = sourceKey;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRecipientId() { return recipientId; }
    public UUID getOrderId() { return orderId; }
    public NotificationType getType() { return type; }
    public String getMessage() { return message; }
    public String getSourceKey() { return sourceKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
