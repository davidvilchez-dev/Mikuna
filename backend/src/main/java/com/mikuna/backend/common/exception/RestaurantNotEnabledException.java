package com.mikuna.backend.common.exception;

/** El restaurante del token ya no está aprobado para operar pedidos (403, {@code RESTAURANT_NOT_ENABLED}). */
public class RestaurantNotEnabledException extends RuntimeException {

    public static final String CODE = "RESTAURANT_NOT_ENABLED";

    public RestaurantNotEnabledException() {
        super("Tu restaurante no está habilitado para gestionar pedidos");
    }
}
