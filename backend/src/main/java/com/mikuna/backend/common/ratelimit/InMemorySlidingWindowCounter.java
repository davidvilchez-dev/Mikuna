package com.mikuna.backend.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante en la memoria de una instancia: la lógica que tenían el freno del inicio de sesión
 * (spec 002) y el límite del asistente (spec 013), extraída sin cambios. También es el respaldo del contador en
 * Redis cuando este no responde (FR-010a).
 */
public class InMemorySlidingWindowCounter implements SlidingWindowCounter {

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public InMemorySlidingWindowCounter(Clock clock) {
        this.clock = clock;
    }

    /** Marcas de los eventos y la ventana con la que se registraron (cada uso tiene la suya). */
    private record Window(Deque<Instant> stamps, Duration length) {
    }

    @Override
    public long waitSeconds(String key, int max, Duration window) {
        Instant now = clock.instant();
        long[] wait = {0};
        windows.computeIfPresent(key, (k, current) -> {
            prune(current.stamps(), now.minus(window));
            if (current.stamps().size() >= max) {
                wait[0] = waitFor(current.stamps(), max, window, now);
            }
            return current.stamps().isEmpty() ? null : current;
        });
        return wait[0];
    }

    @Override
    public void record(String key, Duration window) {
        Instant now = clock.instant();
        windows.compute(key, (k, current) -> {
            Window target = current == null ? new Window(new ArrayDeque<>(), window) : current;
            target.stamps().addLast(now);
            return target;
        });
        purge(now);
    }

    @Override
    public long tryAcquire(String key, int max, Duration window) {
        Instant now = clock.instant();
        long[] wait = {0};
        windows.compute(key, (k, current) -> {
            Window target = current == null ? new Window(new ArrayDeque<>(), window) : current;
            prune(target.stamps(), now.minus(window));
            if (target.stamps().size() >= max) {
                wait[0] = waitFor(target.stamps(), max, window, now);
            } else {
                target.stamps().addLast(now);
            }
            return target.stamps().isEmpty() ? null : target;
        });
        purge(now);
        return wait[0];
    }

    @Override
    public void clear(String key) {
        windows.remove(key);
    }

    @Override
    public void clearAll() {
        windows.clear();
    }

    private static long waitFor(Deque<Instant> stamps, int max, Duration window, Instant now) {
        // Se libera cuando el evento que completa el máximo sale de la ventana.
        Instant oldestCounted = stamps.stream().skip(stamps.size() - max).findFirst().orElseThrow();
        return SlidingWindowCounter.waitSeconds(Duration.between(now, oldestCounted.plus(window)).toMillis());
    }

    private static void prune(Deque<Instant> stamps, Instant limit) {
        while (!stamps.isEmpty() && !stamps.peekFirst().isAfter(limit)) {
            stamps.pollFirst();
        }
    }

    /** Las claves sin eventos recientes no se acumulan en memoria. */
    private void purge(Instant now) {
        // Una purga que coincida con un alta concurrente solo puede perder un conteo: aceptable (research §4, 013).
        windows.values().removeIf(w -> w.stamps().isEmpty() || !w.stamps().peekLast().isAfter(now.minus(w.length())));
    }
}
