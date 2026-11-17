package com.mikuna.backend.common.exception;

/** Conflicto de concurrencia o de unicidad sobre una solicitud de restaurante; se responde 409. */
public class RestaurantConflictException extends RuntimeException {

    public RestaurantConflictException(String message) {
        super(message);
    }
}
