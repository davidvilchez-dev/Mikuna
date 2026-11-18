package com.mikuna.backend.notification.service;

import com.mikuna.backend.notification.model.Notification;
import com.mikuna.backend.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Guarda un aviso en su propia transacción, separada de la que originó el cambio (FR-013). */
@Component
public class NotificationWriter {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationWriter(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Guarda el aviso; devuelve {@code false} si ya existía para ese origen y destinatario (FR-014). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean save(NotificationRecipients.Draft draft, UUID orderId, String sourceKey) {
        if (notifications.existsBySourceKeyAndRecipientId(sourceKey, draft.recipientId())) {
            return false;
        }
        notifications.saveAndFlush(new Notification(draft.recipientId(), orderId, draft.type(), draft.message(),
                sourceKey, Instant.now(clock)));
        return true;
    }
}
