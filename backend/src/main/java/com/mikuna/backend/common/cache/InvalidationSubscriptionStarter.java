package com.mikuna.backend.common.cache;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Inicia la suscripción a los avisos de descarte sin bloquear el arranque (FR-006): si Redis no responde, la
 * aplicación arranca igual y se reintenta cada {@value #RETRY_SECONDS} s. Mientras no haya suscripción, el
 * tiempo de vida corto del nivel local acota cuánto puede durar un dato viejo en esta instancia.
 */
public class InvalidationSubscriptionStarter implements DisposableBean {

    static final long RETRY_SECONDS = 5;
    private static final Logger log = LoggerFactory.getLogger(InvalidationSubscriptionStarter.class);

    private final RedisMessageListenerContainer container;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cache-invalidation-subscriber");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean warned;

    public InvalidationSubscriptionStarter(RedisConnectionFactory connections, TwoLevelCacheManager cacheManager) {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.setRecoveryInterval(RETRY_SECONDS * 1_000);
        container.addMessageListener((message, pattern) ->
                cacheManager.onInvalidation(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(TwoLevelCacheManager.CHANNEL));
        container.afterPropertiesSet();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() {
        scheduler.execute(this::tryStart);
    }

    void tryStart() {
        try {
            container.start();
            if (container.isRunning()) {
                log.info("Suscrito a los avisos de descarte de caché ({})", TwoLevelCacheManager.CHANNEL);
                return;
            }
        } catch (RuntimeException unavailable) {
            if (!warned) {
                warned = true;
                log.warn("Redis no disponible para los avisos de descarte; se reintenta cada {} s: {}", RETRY_SECONDS,
                        unavailable.toString());
            }
        }
        scheduler.schedule(this::tryStart, RETRY_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() throws Exception {
        scheduler.shutdownNow();
        container.destroy();
    }
}
