package com.mikuna.backend.common.exception;

/** La solicitud o el restaurante buscado no existe. */
public class RestaurantNotFoundException extends RuntimeException {

    public RestaurantNotFoundException(String message) {
        super(message);
    }
}
