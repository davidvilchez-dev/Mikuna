package com.mikuna.backend.common.exception;

/**
 * Conflicto de negocio del carrito o de las direcciones (409). El {@code code} permite a la interfaz
 * distinguir el caso (p. ej. ofrecer vaciar el carrito ante {@link #OTHER_RESTAURANT}).
 */
public class CartConflictException extends RuntimeException {

    public static final String OTHER_RESTAURANT = "CART_OTHER_RESTAURANT";
    public static final String NOT_ORDERABLE = "PRODUCT_NOT_ORDERABLE";
    public static final String LIMIT = "CART_LIMIT";
    public static final String ADDRESS_LIMIT = "ADDRESS_LIMIT";

    private final String code;

    public CartConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
