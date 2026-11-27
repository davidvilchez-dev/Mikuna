package com.mikuna.backend.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 015, research §5: la ventana deslizante extraída conserva la semántica de las specs 002 y 013. */
class InMemorySlidingWindowCounterTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private MovableClock clock;
    private InMemorySlidingWindowCounter counter;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-11-26T15:00:00Z"));
        counter = new InMemorySlidingWindowCounter(clock);
    }

    @Test
    void tryAcquireAdmiteHastaElMaximoYLuegoDevuelveLaEsperaSinRegistrar() {
        for (int i = 0; i < 3; i++) {
            assertThat(counter.tryAcquire("k", 3, WINDOW)).isZero();
        }
        clock.advance(Duration.ofSeconds(20));

        assertThat(counter.tryAcquire("k", 3, WINDOW)).isEqualTo(41); // 40 s restantes + 1
        clock.advance(Duration.ofSeconds(41));
        assertThat(counter.tryAcquire("k", 3, WINDOW)).isZero();
    }

    @Test
    void recordYWaitSecondsCuentanLosFallosDeLaVentana() {
        assertThat(counter.waitSeconds("k", 2, WINDOW)).isZero();
        counter.record("k", WINDOW);
        assertThat(counter.waitSeconds("k", 2, WINDOW)).isZero();
        counter.record("k", WINDOW);
        assertThat(counter.waitSeconds("k", 2, WINDOW)).isEqualTo(61);

        clock.advance(WINDOW);
        assertThat(counter.waitSeconds("k", 2, WINDOW)).isZero();
    }

    @Test
    void laEsperaLaFijaElEventoQueCompletaElMaximo() {
        counter.record("k", WINDOW);
        clock.advance(Duration.ofSeconds(30));
        counter.record("k", WINDOW);
        counter.record("k", WINDOW);

        // Con máximo 2 cuentan los dos últimos (t=30 s): faltan 60 s, no los 30 s del primero.
        assertThat(counter.waitSeconds("k", 2, WINDOW)).isEqualTo(61);
    }

    @Test
    void clearYClearAllOlvidanLosEventos() {
        counter.record("a", WINDOW);
        counter.record("b", WINDOW);

        counter.clear("a");
        assertThat(counter.waitSeconds("a", 1, WINDOW)).isZero();
        assertThat(counter.waitSeconds("b", 1, WINDOW)).isPositive();

        counter.clearAll();
        assertThat(counter.waitSeconds("b", 1, WINDOW)).isZero();
    }

    @Test
    void lasClavesSonIndependientesYCadaUnaUsaSuVentana() {
        counter.record("corta", Duration.ofSeconds(10));
        counter.record("larga", Duration.ofMinutes(15));
        clock.advance(Duration.ofSeconds(11));

        assertThat(counter.waitSeconds("corta", 1, Duration.ofSeconds(10))).isZero();
        assertThat(counter.waitSeconds("larga", 1, Duration.ofMinutes(15))).isPositive();
    }

    @Test
    void laEsperaMinimaEsUnSegundo() {
        assertThat(SlidingWindowCounter.waitSeconds(0)).isEqualTo(1);
        assertThat(SlidingWindowCounter.waitSeconds(999)).isEqualTo(1);
        assertThat(SlidingWindowCounter.waitSeconds(1_000)).isEqualTo(2);
    }

    static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
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
