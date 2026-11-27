package com.mikuna.backend.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * FR-006 (spec 015): si el almacén de caché falla, la consulta sigue contra la base de datos. Un fallo al leer
 * cuenta como ausencia y un fallo al guardar o descartar se ignora; solo se registra. El dato que un descarte
 * fallido deje vivo lo acota el tiempo de vida de respaldo (research §4).
 */
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        warn("leer", cache, key, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        warn("guardar", cache, key, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        warn("descartar", cache, key, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        warn("vaciar", cache, "*", exception);
    }

    private static void warn(String action, Cache cache, Object key, RuntimeException exception) {
        log.warn("Caché no disponible al {} {}[{}]; se sigue con la base de datos: {}", action, cache.getName(), key,
                exception.toString());
    }
}
