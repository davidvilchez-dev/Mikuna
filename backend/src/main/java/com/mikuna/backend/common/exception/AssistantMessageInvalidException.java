package com.mikuna.backend.common.exception;

/** Un mensaje del chat no cumple las reglas (vacío, demasiado largo o el último no es del cliente). */
public class AssistantMessageInvalidException extends RuntimeException {

    public AssistantMessageInvalidException(String message) {
        super(message);
    }
}
