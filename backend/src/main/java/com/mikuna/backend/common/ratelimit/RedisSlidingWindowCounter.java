package com.mikuna.backend.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Ventana deslizante compartida entre instancias (spec 015, FR-009): un conjunto ordenado por clave cuya
 * puntuación es la marca del evento en milisegundos. Un script Lua poda, cuenta, comprueba y agrega de forma
 * atómica, así que dos instancias no pueden pasarse del máximo por carrera. La hora sale del {@link Clock} de la
 * aplicación, como en la versión en memoria. Si Redis no responde, se sigue contando en la memoria de esta
 * instancia (FR-010a): el límite se vuelve menos estricto, pero nunca desaparece ni bloquea a todos.
 */
public class RedisSlidingWindowCounter implements SlidingWindowCounter {

    static final String PREFIX = "mikuna:";
    private static final long WARN_EVERY_MS = 60_000;
    private static final Logger log = LoggerFactory.getLogger(RedisSlidingWindowCounter.class);

    /**
     * ARGV: ahora (ms), ventana (ms), máximo, miembro único, modo (check | acquire | record). Devuelve -1 si hay
     * cupo (y en acquire o record registra el evento), o los milisegundos que faltan para que se libere.
     */
    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local max = tonumber(ARGV[3])
            local mode = ARGV[5]
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            if mode ~= 'record' then
              local count = redis.call('ZCARD', KEYS[1])
              if count >= max then
                local completing = redis.call('ZRANGE', KEYS[1], count - max, count - max, 'WITHSCORES')
                return tonumber(completing[2]) + window - now
              end
              if mode == 'check' then
                return -1
              end
            end
            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('PEXPIRE', KEYS[1], window)
            return -1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final InMemorySlidingWindowCounter fallback;
    private volatile long lastWarning;

    public RedisSlidingWindowCounter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
        this.fallback = new InMemorySlidingWindowCounter(clock);
    }

    @Override
    public long waitSeconds(String key, int max, Duration window) {
        return orFallback(() -> toWait(run(key, max, window, "check")), () -> fallback.waitSeconds(key, max, window));
    }

    @Override
    public void record(String key, Duration window) {
        orFallback(() -> run(key, Integer.MAX_VALUE, window, "record"), () -> {
            fallback.record(key, window);
            return null;
        });
    }

    @Override
    public long tryAcquire(String key, int max, Duration window) {
        return orFallback(() -> toWait(run(key, max, window, "acquire")), () -> fallback.tryAcquire(key, max, window));
    }

    @Override
    public void clear(String key) {
        fallback.clear(key);
        orFallback(() -> redis.delete(PREFIX + key), () -> null);
    }

    @Override
    public void clearAll() {
        fallback.clearAll();
        orFallback(() -> {
            Set<String> keys = redis.keys(PREFIX + "*");
            return keys == null || keys.isEmpty() ? 0L : redis.delete(keys);
        }, () -> null);
    }

    private Long run(String key, int max, Duration window, String mode) {
        Instant now = clock.instant();
        return redis.execute(SCRIPT, List.of(PREFIX + key), String.valueOf(now.toEpochMilli()),
                String.valueOf(window.toMillis()), String.valueOf(max), now.toEpochMilli() + "-" + UUID.randomUUID(),
                mode);
    }

    private static long toWait(Long millisUntilFree) {
        return millisUntilFree == null || millisUntilFree < 0 ? 0 : SlidingWindowCounter.waitSeconds(millisUntilFree);
    }

    private <T> T orFallback(Supplier<T> onRedis, Supplier<T> inMemory) {
        try {
            return onRedis.get();
        } catch (RuntimeException unavailable) {
            long now = System.currentTimeMillis();
            if (now - lastWarning > WARN_EVERY_MS) {
                lastWarning = now;
                log.warn("Redis no disponible para los límites compartidos; se cuenta en memoria: {}",
                        unavailable.toString());
            }
            return inMemory.get();
        }
    }
}
