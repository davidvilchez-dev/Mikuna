package com.mikuna.backend.auth.service;

import com.mikuna.backend.common.exception.LoginRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Freno a la fuerza bruta en el inicio de sesión. Cuenta solo los intentos fallidos, por correo normalizado y por
 * IP, en una ventana deslizante; al llegar al máximo se rechaza con 429 antes de comprobar la contraseña. Un
 * acceso correcto borra los fallos de esa cuenta. Vive en memoria (una sola instancia del backend) y se reinicia
 * con la aplicación.
 */
@Component
@EnableConfigurationProperties(LoginProperties.class)
public class LoginAttemptLimiter {

    private final Clock clock;
    private final LoginProperties properties;
    private final Map<String, Deque<Instant>> byAccount = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> byIp = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(Clock clock, LoginProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /** Lanza {@link LoginRateLimitedException} si la cuenta o la IP agotaron sus intentos. */
    public void check(String email, String ip) {
        Instant now = clock.instant();
        long wait = Math.max(waitSeconds(byAccount, email, properties.maxFailuresPerAccount(), now),
                waitSeconds(byIp, ip, properties.maxFailuresPerIp(), now));
        if (wait > 0) {
            throw new LoginRateLimitedException(wait);
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        add(byAccount, email, now);
        add(byIp, ip, now);
        purge(now.minus(properties.window()));
    }

    /** Acceso correcto: la cuenta empieza de cero; los fallos de la IP se conservan. */
    public void recordSuccess(String email) {
        byAccount.remove(email);
    }

    /** Solo para pruebas: olvida todos los intentos. */
    public void reset() {
        byAccount.clear();
        byIp.clear();
    }

    private long waitSeconds(Map<String, Deque<Instant>> map, String key, int max, Instant now) {
        if (key == null) {
            return 0;
        }
        Instant limit = now.minus(properties.window());
        long[] wait = {0};
        map.computeIfPresent(key, (k, stamps) -> {
            prune(stamps, limit);
            if (stamps.size() >= max) {
                // Se libera cuando el fallo que completa el máximo sale de la ventana.
                Instant oldestCounted = stamps.stream().skip(stamps.size() - max).findFirst().orElseThrow();
                wait[0] = Math.max(1, Duration.between(now, oldestCounted.plus(properties.window())).toSeconds() + 1);
            }
            return stamps.isEmpty() ? null : stamps;
        });
        return wait[0];
    }

    private void add(Map<String, Deque<Instant>> map, String key, Instant now) {
        if (key == null) {
            return;
        }
        map.compute(key, (k, stamps) -> {
            Deque<Instant> window = stamps == null ? new ArrayDeque<>() : stamps;
            window.addLast(now);
            return window;
        });
    }

    private static void prune(Deque<Instant> stamps, Instant limit) {
        while (!stamps.isEmpty() && !stamps.peekFirst().isAfter(limit)) {
            stamps.pollFirst();
        }
    }

    /** Se ejecuta en cada fallo para que las claves sin fallos recientes no se acumulen en memoria. */
    private void purge(Instant limit) {
        // Una purga que coincida con un alta concurrente solo puede perder un conteo: aceptable.
        byAccount.values().removeIf(stamps -> stamps.isEmpty() || !stamps.peekLast().isAfter(limit));
        byIp.values().removeIf(stamps -> stamps.isEmpty() || !stamps.peekLast().isAfter(limit));
    }
}
