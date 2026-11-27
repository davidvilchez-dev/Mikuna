package com.mikuna.backend.assistant.service;

import com.mikuna.backend.common.exception.AssistantRateLimitedException;
import com.mikuna.backend.common.ratelimit.InMemorySlidingWindowCounter;
import com.mikuna.backend.common.ratelimit.SlidingWindowCounter;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Límite de mensajes por cliente en una ventana deslizante de 60 s (research §4). Protege la cuota gratuita del
 * proveedor. Con el perfil "redis" se cuenta de forma compartida y atómica entre instancias (spec 015); si no, en
 * la memoria de la instancia.
 */
@Component
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantUsageLimiter {

    static final Duration WINDOW = Duration.ofSeconds(60);

    private final AssistantProperties properties;
    private final SlidingWindowCounter counter;

    @Autowired
    public AssistantUsageLimiter(AssistantProperties properties, SlidingWindowCounter counter) {
        this.properties = properties;
        this.counter = counter;
    }

    /** Una sola instancia, en memoria (pruebas unitarias). */
    public AssistantUsageLimiter(Clock clock, AssistantProperties properties) {
        this(properties, new InMemorySlidingWindowCounter(clock));
    }

    /** Registra un mensaje o lanza {@link AssistantRateLimitedException} con los segundos de espera. */
    public void acquire(UUID accountId) {
        long wait = counter.tryAcquire("assistant:" + accountId, properties.rateLimitPerMinute(), WINDOW);
        if (wait > 0) {
            throw new AssistantRateLimitedException(wait);
        }
    }
}
