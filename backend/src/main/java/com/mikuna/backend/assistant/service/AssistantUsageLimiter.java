package com.mikuna.backend.assistant.service;

import com.mikuna.backend.common.exception.AssistantRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Límite de mensajes por cliente en una ventana deslizante de 60 s (research §4). Protege la cuota gratuita del
 * proveedor; no es una regla de negocio crítica, por eso vive en memoria y se reinicia con la aplicación.
 */
@Component
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantUsageLimiter {

    static final Duration WINDOW = Duration.ofSeconds(60);

    private final Clock clock;
    private final AssistantProperties properties;
    private final ConcurrentHashMap<UUID, Deque<Instant>> usage = new ConcurrentHashMap<>();

    public AssistantUsageLimiter(Clock clock, AssistantProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /** Registra un mensaje o lanza {@link AssistantRateLimitedException} con los segundos de espera. */
    public void acquire(UUID accountId) {
        Instant now = clock.instant();
        Instant limit = now.minus(WINDOW);
        usage.compute(accountId, (id, stamps) -> {
            Deque<Instant> window = stamps == null ? new ArrayDeque<>() : stamps;
            while (!window.isEmpty() && !window.peekFirst().isAfter(limit)) {
                window.pollFirst();
            }
            if (window.size() >= properties.rateLimitPerMinute()) {
                long wait = Math.max(1, Duration.between(now, window.peekFirst().plus(WINDOW)).toSeconds() + 1);
                throw new AssistantRateLimitedException(wait);
            }
            window.addLast(now);
            return window;
        });
        purge(limit);
    }

    private void purge(Instant limit) {
        // Una purga que coincida con un alta concurrente solo puede perder un conteo: aceptable (research §4).
        usage.entrySet().removeIf(e -> e.getValue().isEmpty() || !e.getValue().peekLast().isAfter(limit));
    }
}
