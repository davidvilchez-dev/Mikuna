package com.mikuna.backend.common.exception;

/** Duplicado de nombre y categoría o edición sobre una versión desactualizada; se responde 409. */
public class ProductConflictException extends RuntimeException {

    public static final String DUPLICATE = "Ya existe un producto con ese nombre en esa categoría";
    public static final String STALE = "El producto cambió; recarga e inténtalo de nuevo";

    public ProductConflictException(String message) {
        super(message);
    }
}
