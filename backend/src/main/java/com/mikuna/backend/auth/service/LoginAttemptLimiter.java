package com.mikuna.backend.auth.service;

import com.mikuna.backend.common.exception.LoginRateLimitedException;
import com.mikuna.backend.common.ratelimit.InMemorySlidingWindowCounter;
import com.mikuna.backend.common.ratelimit.SlidingWindowCounter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Freno a la fuerza bruta en el inicio de sesión. Cuenta solo los intentos fallidos, por correo normalizado y por
 * IP, en una ventana deslizante; al llegar al máximo se rechaza con 429 antes de comprobar la contraseña. Un
 * acceso correcto borra los fallos de esa cuenta. Con el perfil "redis" el conteo se comparte entre instancias
 * (spec 015); si no, vive en la memoria de la instancia. Las claves llevan un hash del correo y de la IP, nunca el
 * dato en claro.
 */
@Component
@EnableConfigurationProperties(LoginProperties.class)
public class LoginAttemptLimiter {

    private final LoginProperties properties;
    private final SlidingWindowCounter counter;

    @Autowired
    public LoginAttemptLimiter(LoginProperties properties, SlidingWindowCounter counter) {
        this.properties = properties;
        this.counter = counter;
    }

    /** Una sola instancia, en memoria (pruebas unitarias). */
    public LoginAttemptLimiter(Clock clock, LoginProperties properties) {
        this(properties, new InMemorySlidingWindowCounter(clock));
    }

    /** Lanza {@link LoginRateLimitedException} si la cuenta o la IP agotaron sus intentos. */
    public void check(String email, String ip) {
        long wait = Math.max(waitSeconds(accountKey(email), properties.maxFailuresPerAccount()),
                waitSeconds(ipKey(ip), properties.maxFailuresPerIp()));
        if (wait > 0) {
            throw new LoginRateLimitedException(wait);
        }
    }

    public void recordFailure(String email, String ip) {
        record(accountKey(email));
        record(ipKey(ip));
    }

    /** Acceso correcto: la cuenta empieza de cero; los fallos de la IP se conservan. */
    public void recordSuccess(String email) {
        String key = accountKey(email);
        if (key != null) {
            counter.clear(key);
        }
    }

    /** Solo para pruebas: olvida todos los intentos. */
    public void reset() {
        counter.clearAll();
    }

    private long waitSeconds(String key, int max) {
        return key == null ? 0 : counter.waitSeconds(key, max, properties.window());
    }

    private void record(String key) {
        if (key != null) {
            counter.record(key, properties.window());
        }
    }

    private static String accountKey(String email) {
        return email == null ? null : "login:acct:" + sha256(email);
    }

    private static String ipKey(String ip) {
        return ip == null ? null : "login:ip:" + sha256(ip);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }
}
