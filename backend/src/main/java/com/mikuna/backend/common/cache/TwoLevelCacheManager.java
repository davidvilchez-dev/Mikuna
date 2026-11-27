package com.mikuna.backend.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Gestor de las cachés de dos niveles (perfil "redis", spec 015). Envuelve el gestor de Redis (nivel 2) con un
 * Caffeine por caché (nivel 1). Los descartes se anuncian por el canal {@value #CHANNEL} con el formato
 * {@code <caché>|<clave>}; {@link #onInvalidation(String)} los aplica al nivel 1 de esta instancia.
 */
public class TwoLevelCacheManager implements CacheManager {

    public static final String CHANNEL = "mikuna:cache:invalidate";
    private static final String SEPARATOR = "|";

    private final Map<String, TwoLevelCache> caches = new LinkedHashMap<>();

    /**
     * @param shared   gestor de Redis ya inicializado (nivel 2).
     * @param names    cachés a crear.
     * @param localTtl vida del nivel 1: respaldo si un aviso de descarte se pierde (Redis caído un momento).
     * @param publish  publica {@code (canal, mensaje)}; en Redis, {@code StringRedisTemplate::convertAndSend}.
     */
    public TwoLevelCacheManager(CacheManager shared, Collection<String> names, Duration localTtl,
            BiConsumer<String, String> publish) {
        for (String name : names) {
            Cache sharedCache = shared.getCache(name);
            if (sharedCache == null) {
                throw new IllegalStateException("El gestor de Redis no define la caché " + name);
            }
            com.github.benmanes.caffeine.cache.Cache<String, Object> local = Caffeine.newBuilder()
                    .expireAfterWrite(localTtl)
                    .maximumSize(1_000)
                    .recordStats()
                    .build();
            caches.put(name, new TwoLevelCache(name, local, sharedCache,
                    key -> publish.accept(CHANNEL, name + SEPARATOR + key)));
        }
    }

    @Override
    public Cache getCache(String name) {
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return List.copyOf(caches.keySet());
    }

    public Collection<TwoLevelCache> caches() {
        return List.copyOf(caches.values());
    }

    /** Aviso recibido por pub/sub (también los propios: borrar dos veces el nivel 1 es inocuo). */
    public void onInvalidation(String message) {
        int separator = message.indexOf(SEPARATOR);
        if (separator <= 0) {
            return;
        }
        TwoLevelCache cache = caches.get(message.substring(0, separator));
        if (cache != null) {
            cache.evictLocal(message.substring(separator + 1));
        }
    }
}
