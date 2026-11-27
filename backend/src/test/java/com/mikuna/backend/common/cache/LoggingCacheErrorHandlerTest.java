package com.mikuna.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

/** Spec 015, FR-006: ninguna falla del almacén se propaga; solo se registra. */
class LoggingCacheErrorHandlerTest {

    private final LoggingCacheErrorHandler handler = new LoggingCacheErrorHandler();
    private final ConcurrentMapCache cache = new ConcurrentMapCache(CacheNames.PUBLIC_RESTAURANTS);
    private final RuntimeException down = new IllegalStateException("redis caído");

    @Test
    void lasFallasDeLeerGuardarDescartarYVaciarNoSePropagan() {
        assertThatCode(() -> {
            handler.handleCacheGetError(down, cache, "all");
            handler.handleCachePutError(down, cache, "all", "valor");
            handler.handleCacheEvictError(down, cache, "all");
            handler.handleCacheClearError(down, cache);
        }).doesNotThrowAnyException();
    }
}
