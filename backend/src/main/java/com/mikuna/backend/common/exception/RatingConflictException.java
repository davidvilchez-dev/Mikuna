package com.mikuna.backend.common.exception;

/** Calificación no admitida (409): el pedido no está entregado o ya tiene su única calificación (RF-31). */
public class RatingConflictException extends RuntimeException {

    public static final String ORDER_NOT_DELIVERED = "ORDER_NOT_DELIVERED";
    public static final String ALREADY_EXISTS = "RATING_ALREADY_EXISTS";

    private final String code;

    public RatingConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
