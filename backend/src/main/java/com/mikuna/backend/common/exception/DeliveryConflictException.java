package com.mikuna.backend.common.exception;

/**
 * Conflicto en la asignación o liberación de una entrega (409): lleva el {@code code} que distingue el caso
 * (spec 008). La asignación condicional que no afecta filas usa {@link #DELIVERY_CONFLICT}.
 */
public class DeliveryConflictException extends RuntimeException {

    public static final String DELIVERY_CONFLICT = "DELIVERY_CONFLICT";

    private final String code;

    public DeliveryConflictException() {
        this(DELIVERY_CONFLICT, "La entrega ya fue asignada por otra solicitud");
    }

    protected DeliveryConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
