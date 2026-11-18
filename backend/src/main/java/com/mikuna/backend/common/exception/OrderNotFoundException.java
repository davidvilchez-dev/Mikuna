package com.mikuna.backend.common.exception;

/** Pedido inexistente o de otro cliente (404): no se revela su existencia. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("Pedido no encontrado");
    }
}
