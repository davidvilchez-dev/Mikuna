package com.mikuna.backend.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spec 015, historia 3 (FR-009): dos contadores sobre el mismo Redis se comportan como uno solo. Requiere Redis en
 * localhost:6379; corre siempre en CI (MIKUNA_TEST_REDIS=true) y en local solo si se levantó Redis.
 */
@EnabledIfEnvironmentVariable(named = "MIKUNA_TEST_REDIS", matches = "true")
class RedisSlidingWindowCounterIntegrationTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private RedisSlidingWindowCounter instanceA;
    private RedisSlidingWindowCounter instanceB;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        instanceA = new RedisSlidingWindowCounter(redis, Clock.systemUTC());
        instanceB = new RedisSlidingWindowCounter(redis, Clock.systemUTC());
        instanceA.clearAll();
    }

    @AfterEach
    void tearDown() {
        instanceA.clearAll();
        factory.destroy();
    }

    @Test
    void losFallosRepartidosEntreInstanciasLleganAlLimiteCombinado() {
        for (int i = 0; i < 5; i++) {
            (i % 2 == 0 ? instanceA : instanceB).record("login:acct:x", WINDOW);
        }

        assertThat(instanceA.waitSeconds("login:acct:x", 5, WINDOW)).isPositive();
        assertThat(instanceB.waitSeconds("login:acct:x", 5, WINDOW)).isPositive();
        assertThat(instanceA.waitSeconds("login:acct:x", 6, WINDOW)).isZero();

        instanceB.clear("login:acct:x");
        assertThat(instanceA.waitSeconds("login:acct:x", 5, WINDOW)).isZero();
    }

    @Test
    void tryAcquireConcurrenteNuncaSuperaElMaximo() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                RedisSlidingWindowCounter counter = i % 2 == 0 ? instanceA : instanceB;
                tasks.add(() -> counter.tryAcquire("assistant:y", 10, Duration.ofSeconds(60)));
            }
            long admitted = 0;
            for (Future<Long> result : pool.invokeAll(tasks)) {
                if (result.get() == 0) {
                    admitted++;
                }
            }
            assertThat(admitted).isEqualTo(10);
            assertThat(redis.opsForZSet().zCard(RedisSlidingWindowCounter.PREFIX + "assistant:y")).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void laClaveExpiraConLaVentana() {
        instanceA.record("login:ip:z", Duration.ofSeconds(30));

        Long ttl = redis.getExpire(RedisSlidingWindowCounter.PREFIX + "login:ip:z");
        assertThat(ttl).isBetween(1L, 30L);
    }
}
