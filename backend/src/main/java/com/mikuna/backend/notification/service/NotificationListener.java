package com.mikuna.backend.notification.service;

import com.mikuna.backend.order.model.OrderPlacedEvent;
import com.mikuna.backend.order.model.OrderStatusChangedEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Registra los avisos de cada cambio una vez confirmado (RF-29). Nunca propaga un error: si un aviso no se puede
 * guardar tras {@link #ATTEMPTS} intentos se descarta dejando constancia, y el cambio de estado ya está
 * confirmado (FR-013).
 */
@Component
public class NotificationListener {

    static final int ATTEMPTS = 3;
    private static final Logger LOG = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRecipients recipients;
    private final NotificationWriter writer;

    public NotificationListener(NotificationRecipients recipients, NotificationWriter writer) {
        this.recipients = recipients;
        this.writer = writer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        String key = event.changeId() == null ? UUID.randomUUID().toString() : event.changeId().toString();
        register(event.orderId(), key, () -> recipients.forStatusChange(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlaced(OrderPlacedEvent event) {
        register(event.orderId(), "PLACED:" + event.orderId(), () -> recipients.forPlaced(event));
    }

    private void register(UUID orderId, String sourceKey, Supplier<List<NotificationRecipients.Draft>> plan) {
        List<NotificationRecipients.Draft> drafts;
        try {
            drafts = plan.get();
        } catch (RuntimeException e) {
            LOG.warn("No se pudieron determinar los avisos del pedido {} ({})", orderId, e.getClass().getSimpleName());
            return;
        }
        for (NotificationRecipients.Draft draft : drafts) {
            store(draft, orderId, sourceKey);
        }
    }

    private void store(NotificationRecipients.Draft draft, UUID orderId, String sourceKey) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                writer.save(draft, orderId, sourceKey);
                return;
            } catch (DataIntegrityViolationException duplicate) {
                return; // otro proceso ya registró este aviso (FR-014)
            } catch (RuntimeException e) {
                if (attempt == ATTEMPTS) {
                    LOG.warn("Aviso {} del pedido {} descartado tras {} intentos ({})", draft.type(), orderId,
                            ATTEMPTS, e.getClass().getSimpleName());
                }
            }
        }
    }
}
