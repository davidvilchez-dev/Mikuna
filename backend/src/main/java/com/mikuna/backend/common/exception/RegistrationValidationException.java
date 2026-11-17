package com.mikuna.backend.common.exception;

/** Regla de registro incumplida en un campo concreto; se responde como 400 con «errors». */
public class RegistrationValidationException extends RuntimeException {

    private final String field;

    public RegistrationValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
