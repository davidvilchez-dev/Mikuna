package com.mikuna.backend.common.exception;

/** Demasiados intentos fallidos de inicio de sesión para esa cuenta o ese origen (429 con Retry-After). */
public class LoginRateLimitedException extends RuntimeException {

    public static final String CODE = "LOGIN_RATE_LIMITED";

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Demasiados intentos fallidos. Intenta de nuevo en " + minutes(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String minutes(long seconds) {
        long min = Math.max(1, (seconds + 59) / 60);
        return min == 1 ? "1 minuto" : min + " minutos";
    }
}
