package com.mikuna.backend.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spec 015, FR-010a: si Redis no responde, los límites siguen aplicándose en la memoria de la instancia, sin
 * bloquear a todos ni quedar sin límite. Apunta a un puerto sin servicio: corre siempre, sin Redis.
 */
class RedisSlidingWindowCounterFallbackTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private LettuceConnectionFactory factory;
    private RedisSlidingWindowCounter counter;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).build());
        factory.afterPropertiesSet();
        counter = new RedisSlidingWindowCounter(new StringRedisTemplate(factory), Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        factory.destroy();
    }

    @Test
    void sinRedisElFrenoSigueContandoEnMemoria() {
        assertThat(counter.waitSeconds("login:acct:x", 2, WINDOW)).isZero();
        counter.record("login:acct:x", WINDOW);
        counter.record("login:acct:x", WINDOW);

        assertThat(counter.waitSeconds("login:acct:x", 2, WINDOW)).isPositive();

        counter.clear("login:acct:x");
        assertThat(counter.waitSeconds("login:acct:x", 2, WINDOW)).isZero();
    }

    @Test
    void sinRedisElLimiteDelAsistenteSigueAplicandose() {
        for (int i = 0; i < 3; i++) {
            assertThat(counter.tryAcquire("assistant:y", 3, Duration.ofSeconds(60))).isZero();
        }
        assertThat(counter.tryAcquire("assistant:y", 3, Duration.ofSeconds(60))).isPositive();

        counter.clearAll();
        assertThat(counter.tryAcquire("assistant:y", 3, Duration.ofSeconds(60))).isZero();
    }
}
