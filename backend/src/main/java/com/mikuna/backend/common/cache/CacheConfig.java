package com.mikuna.backend.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.mikuna.backend.catalog.dto.PublicProductList;
import com.mikuna.backend.catalog.dto.RestaurantSnapshotList;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Caché de consultas públicas (spec 015, research §1-§2). El proveedor lo elige spring.cache.type:
 * <ul>
 * <li>{@code caffeine} (por defecto): en la memoria de la instancia, sin Redis ni Docker.</li>
 * <li>{@code redis} (perfil "redis"): dos niveles, Caffeine local delante de Redis compartido, con descarte entre
 * instancias por pub/sub ({@link TwoLevelCacheManager}). Leer la instantánea de Redis en cada consulta (174 KB de
 * JSON) saturaba la CPU con carga (load-tests/reports/RESUMEN.md §3); el nivel local lo evita.</li>
 * <li>{@code none}: apagada (solo para comparar en pruebas de carga).</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final List<String> NAMES = List.of(CacheNames.PUBLIC_RESTAURANTS, CacheNames.PUBLIC_CATALOG);

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine")
    CacheManagerCustomizer<CaffeineCacheManager> caffeineTtl(@Value("${mikuna.cache.ttl}") Duration ttl) {
        return manager -> manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(1_000)
                .recordStats());
    }

    /** Nivel 2 en Redis con un serializador JSON tipado por caché, envuelto por el nivel 1 local. */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    TwoLevelCacheManager cacheManager(RedisConnectionFactory connections, StringRedisTemplate redis,
            @Value("${mikuna.cache.ttl}") Duration ttl, @Value("${mikuna.cache.local-ttl:PT1M}") Duration localTtl) {
        RedisCacheManager shared = RedisCacheManager.builder(connections)
                .withCacheConfiguration(CacheNames.PUBLIC_RESTAURANTS, redisConfig(ttl, RestaurantSnapshotList.class))
                .withCacheConfiguration(CacheNames.PUBLIC_CATALOG, redisConfig(ttl, PublicProductList.class))
                .build();
        shared.afterPropertiesSet();
        return new TwoLevelCacheManager(shared, NAMES, localTtl, redis::convertAndSend);
    }

    /** Escucha los descartes de las demás instancias, sin bloquear el arranque si Redis no responde. */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    InvalidationSubscriptionStarter invalidationSubscriptionStarter(RedisConnectionFactory connections,
            TwoLevelCacheManager cacheManager) {
        return new InvalidationSubscriptionStarter(connections, cacheManager);
    }

    /** Aciertos del nivel 1 (donde se resuelven las lecturas) como cache.gets, igual que con Caffeine (FR-020). */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    MeterBinder twoLevelCacheMetrics(TwoLevelCacheManager cacheManager) {
        return registry -> cacheManager.caches()
                .forEach(cache -> CaffeineCacheMetrics.monitor(registry, cache.localCache(), cache.getName()));
    }

    private static <T> RedisCacheConfiguration redisConfig(Duration ttl, Class<T> type) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("mikuna:cache:")
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(type)));
    }
}
