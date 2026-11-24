package com.mikuna.backend.common.exception;

/** El proveedor de IA agotó su cuota o está saturado (429 del proveedor). */
public class AssistantBusyException extends RuntimeException {

    public static final String CODE = "ASSISTANT_BUSY";
    public static final String MESSAGE = "El asistente está ocupado. Intenta de nuevo en un minuto.";

    public AssistantBusyException() {
        super(MESSAGE);
    }
}
