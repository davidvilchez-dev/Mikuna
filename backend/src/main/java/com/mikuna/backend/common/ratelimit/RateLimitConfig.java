package com.mikuna.backend.common.ratelimit;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Contador de los límites (spec 015): en memoria por defecto; compartido en Redis con el perfil "redis". */
@Configuration
public class RateLimitConfig {

    @Bean
    @Profile("!redis")
    SlidingWindowCounter inMemorySlidingWindowCounter(Clock clock) {
        return new InMemorySlidingWindowCounter(clock);
    }

    @Bean
    @Profile("redis")
    SlidingWindowCounter redisSlidingWindowCounter(StringRedisTemplate redis, Clock clock) {
        return new RedisSlidingWindowCounter(redis, clock);
    }
}
