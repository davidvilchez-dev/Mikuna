package com.mikuna.backend.common.exception;

/** El proveedor de IA no está configurado, no responde o falló; sin detalles técnicos. */
public class AssistantUnavailableException extends RuntimeException {

    public static final String CODE = "ASSISTANT_UNAVAILABLE";
    public static final String MESSAGE = "El asistente no está disponible por ahora. Intenta más tarde.";

    public AssistantUnavailableException() {
        super(MESSAGE);
    }
}
