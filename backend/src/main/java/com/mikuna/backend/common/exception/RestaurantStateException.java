package com.mikuna.backend.common.exception;

/** El estado del restaurante no permite la operación (RF-04); se responde 403. */
public class RestaurantStateException extends RuntimeException {

    public RestaurantStateException(String message) {
        super(message);
    }
}
