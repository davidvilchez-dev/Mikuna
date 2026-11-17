package com.mikuna.backend.common.exception;

/** Línea inexistente o de otro carrito; se responde 404 sin distinguir los casos. */
public class CartLineNotFoundException extends RuntimeException {

    public CartLineNotFoundException() {
        super("Línea no encontrada");
    }
}
