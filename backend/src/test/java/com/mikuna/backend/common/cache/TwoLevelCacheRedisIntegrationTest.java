package com.mikuna.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.catalog.dto.PublicProductList;
import com.mikuna.backend.catalog.dto.PublicProductResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Spec 015 con Redis real: dos "instancias" (dos gestores de dos niveles con su propio oyente pub/sub) comparten el
 * nivel 2; un descarte en una borra el nivel 1 de la otra en menos de 1 s (SC-004). Requiere Redis en
 * localhost:6379 (CI: MIKUNA_TEST_REDIS=true).
 */
@EnabledIfEnvironmentVariable(named = "MIKUNA_TEST_REDIS", matches = "true")
class TwoLevelCacheRedisIntegrationTest {

    private static final UUID RESTAURANT = UUID.randomUUID();

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private RedisMessageListenerContainer listenerA;
    private RedisMessageListenerContainer listenerB;
    private TwoLevelCacheManager instanceA;
    private TwoLevelCacheManager instanceB;

    @BeforeEach
    void setUp() throws Exception {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        instanceA = manager();
        instanceB = manager();
        listenerA = listener(instanceA);
        listenerB = listener(instanceB);
        Thread.sleep(300); // suscripciones activas
        instanceA.getCache(CacheNames.PUBLIC_CATALOG).evict(RESTAURANT);
    }

    @AfterEach
    void tearDown() throws Exception {
        listenerA.destroy();
        listenerB.destroy();
        factory.destroy();
    }

    private TwoLevelCacheManager manager() {
        RedisCacheManager shared = RedisCacheManager.builder(factory)
                .withCacheConfiguration(CacheNames.PUBLIC_CATALOG, RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(1))
                        .prefixCacheNameWith("mikuna:test:")
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(PublicProductList.class))))
                .build();
        shared.afterPropertiesSet();
        return new TwoLevelCacheManager(shared, List.of(CacheNames.PUBLIC_CATALOG), Duration.ofMinutes(1),
                redis::convertAndSend);
    }

    private RedisMessageListenerContainer listener(TwoLevelCacheManager manager) throws Exception {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) ->
                manager.onInvalidation(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(TwoLevelCacheManager.CHANNEL));
        container.afterPropertiesSet();
        container.start();
        return container;
    }

    @Test
    void unDescarteEnUnaInstanciaLlegaAlNivelLocalDeLaOtraEnMenosDeUnSegundo() throws Exception {
        PublicProductList carta = new PublicProductList(List.of(new PublicProductResponse(UUID.randomUUID(), "Lomo",
                "Casero", "Fondos", new BigDecimal("22.50"), "https://img.test/p.jpg")));
        Cache cacheA = instanceA.getCache(CacheNames.PUBLIC_CATALOG);
        Cache cacheB = instanceB.getCache(CacheNames.PUBLIC_CATALOG);

        cacheA.put(RESTAURANT, carta);
        String sharedKey = "mikuna:test:" + CacheNames.PUBLIC_CATALOG + "::" + RESTAURANT;
        long visible = System.currentTimeMillis() + 2_000;
        while (!Boolean.TRUE.equals(redis.hasKey(sharedKey)) && System.currentTimeMillis() < visible) {
            Thread.sleep(20);
        }
        assertThat(redis.hasKey(sharedKey)).as("la carta llegó al nivel compartido").isTrue();
        assertThat(cacheB.get(RESTAURANT, PublicProductList.class)).isEqualTo(carta); // desde Redis, ida y vuelta

        cacheA.evict(RESTAURANT);

        long deadline = System.currentTimeMillis() + 1_000;
        while (((TwoLevelCache) cacheB).localCache().getIfPresent(RESTAURANT.toString()) != null
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(((TwoLevelCache) cacheB).localCache().getIfPresent(RESTAURANT.toString())).isNull();
        assertThat(cacheB.get(RESTAURANT)).isNull();
    }
}
