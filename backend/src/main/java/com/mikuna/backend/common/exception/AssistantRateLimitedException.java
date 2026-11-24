package com.mikuna.backend.common.exception;

/** El cliente superó su límite de mensajes por minuto al asistente (429 con Retry-After). */
public class AssistantRateLimitedException extends RuntimeException {

    public static final String CODE = "ASSISTANT_RATE_LIMITED";

    private final long retryAfterSeconds;

    public AssistantRateLimitedException(long retryAfterSeconds) {
        super("Escribes muy rápido. Intenta de nuevo en " + retryAfterSeconds + " segundos.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
