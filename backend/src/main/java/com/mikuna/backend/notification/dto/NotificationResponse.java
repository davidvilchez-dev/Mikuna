package com.mikuna.backend.notification.dto;

import com.mikuna.backend.notification.model.Notification;
import java.time.Instant;
import java.util.UUID;

/** Aviso tal como lo ve su destinatario. */
public record NotificationResponse(UUID id, UUID orderId, String type, String message, Instant createdAt,
        boolean read) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getOrderId(),
                notification.getType().name(), notification.getMessage(), notification.getCreatedAt(),
                notification.getReadAt() != null);
    }
}
