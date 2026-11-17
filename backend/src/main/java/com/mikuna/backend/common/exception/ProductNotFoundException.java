package com.mikuna.backend.common.exception;

/** Producto inexistente, retirado o de otro restaurante; se responde 404 sin distinguir los casos. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException() {
        super("Producto no encontrado");
    }
}
