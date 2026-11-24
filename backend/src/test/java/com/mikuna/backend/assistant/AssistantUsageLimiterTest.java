package com.mikuna.backend.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikuna.backend.assistant.service.AssistantProperties;
import com.mikuna.backend.assistant.service.AssistantUsageLimiter;
import com.mikuna.backend.common.exception.AssistantRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FR-011 / SC-005: ventana deslizante de 60 s por cliente. */
class AssistantUsageLimiterTest {

    private MutableClock clock;
    private AssistantUsageLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-24T15:00:00Z"));
        limiter = new AssistantUsageLimiter(clock, new AssistantProperties(10, 500));
    }

    @Test
    void aceptaDiezMensajesYRechazaElOnce() {
        UUID account = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            limiter.acquire(account);
        }

        assertThatThrownBy(() -> limiter.acquire(account))
                .isInstanceOfSatisfying(AssistantRateLimitedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 61L));
    }

    @Test
    void trasLaVentanaVuelveAAceptar() {
        UUID account = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            limiter.acquire(account);
        }
        clock.advance(Duration.ofSeconds(61));

        limiter.acquire(account);
    }

    @Test
    void elTiempoDeEsperaEsElQueFaltaParaQueSalgaElMasAntiguo() {
        UUID account = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            limiter.acquire(account);
        }
        clock.advance(Duration.ofSeconds(40));

        assertThatThrownBy(() -> limiter.acquire(account))
                .isInstanceOfSatisfying(AssistantRateLimitedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(20L, 22L));
    }

    @Test
    void cadaClienteTieneSuPropioCupo() {
        UUID a = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            limiter.acquire(a);
        }

        limiter.acquire(UUID.randomUUID());
    }

    @Test
    void elMensajeRechazadoNoConsumeCupo() {
        UUID account = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            limiter.acquire(account);
        }
        assertThatThrownBy(() -> limiter.acquire(account)).isInstanceOf(AssistantRateLimitedException.class);
        clock.advance(Duration.ofSeconds(61));

        for (int i = 0; i < 10; i++) {
            limiter.acquire(account);
        }
    }

    /** Reloj que avanza a mano. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
