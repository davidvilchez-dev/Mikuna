package com.mikuna.backend.common.exception;

/**
 * Conflicto al confirmar un pedido (409). El {@code code} permite a la interfaz distinguir el caso; en
 * {@code AMOUNTS_CHANGED} lleva el resumen vigente para mostrarlo de nuevo.
 */
public class OrderConflictException extends RuntimeException {

    public static final String CART_EMPTY = "CART_EMPTY";
    public static final String NO_ADDRESS = "NO_ADDRESS";
    public static final String NOT_ORDERABLE = "NOT_ORDERABLE";
    public static final String AMOUNTS_CHANGED = "AMOUNTS_CHANGED";
    public static final String RESERVATION_EXPIRED = "RESERVATION_EXPIRED";
    /** El pedido venció sin respuesta del restaurante (spec 012). */
    public static final String ORDER_EXPIRED = "ORDER_EXPIRED";
    public static final String EXPIRED_MESSAGE = "El pedido venció porque no se respondió a tiempo";

    private final String code;
    private final transient Object checkout;

    public OrderConflictException(String code, String message) {
        this(code, message, null);
    }

    public OrderConflictException(String code, String message, Object checkout) {
        super(message);
        this.code = code;
        this.checkout = checkout;
    }

    public String getCode() {
        return code;
    }

    public Object getCheckout() {
        return checkout;
    }
}
