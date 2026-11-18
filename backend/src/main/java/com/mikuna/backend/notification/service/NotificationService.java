package com.mikuna.backend.notification.service;

import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.exception.NotificationNotFoundException;
import com.mikuna.backend.notification.dto.NotificationResponse;
import com.mikuna.backend.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta y lectura de los avisos propios (RF-29); el destinatario sale siempre del token. */
@Service
public class NotificationService {

    public static final int MAX_SIZE = 50;

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Avisos propios, del más reciente al más antiguo; {@code size} se limita a {@link #MAX_SIZE}. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID accountId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
        Page<com.mikuna.backend.notification.model.Notification> result = unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(accountId, pageable)
                : notifications.findByRecipientIdOrderByCreatedAtDesc(accountId, pageable);
        return PageResponse.from(result, NotificationResponse::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID accountId) {
        return notifications.countByRecipientIdAndReadAtIsNull(accountId);
    }

    /** Marca leído un aviso propio; repetirlo no tiene efecto. Ajeno o inexistente es 404. */
    @Transactional
    public void markRead(UUID accountId, UUID id) {
        notifications.findByIdAndRecipientId(id, accountId).orElseThrow(NotificationNotFoundException::new);
        notifications.markRead(id, accountId, Instant.now(clock));
    }

    @Transactional
    public void markAllRead(UUID accountId) {
        notifications.markAllRead(accountId, Instant.now(clock));
    }
}
