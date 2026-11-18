package com.mikuna.backend.common.exception;

/** El estado actual del pedido no admite la acción pedida (409, {@code INVALID_TRANSITION}). */
public class InvalidOrderTransitionException extends OrderConflictException {

    public static final String CODE = "INVALID_TRANSITION";
    public static final String GENERIC = "El pedido ya no admite esa acción porque su estado cambió";
    public static final String NO_REFUND =
            "El pedido ya fue aceptado y cobrado, por lo que no se puede cancelar ni hay reembolsos";

    public InvalidOrderTransitionException() {
        this(GENERIC);
    }

    public InvalidOrderTransitionException(String message) {
        super(CODE, message);
    }
}
