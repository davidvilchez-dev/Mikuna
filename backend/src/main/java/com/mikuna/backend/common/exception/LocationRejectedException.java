package com.mikuna.backend.common.exception;

/** El repartidor envió su ubicación sin tener una entrega en curso (409, spec 009). */
public class LocationRejectedException extends RuntimeException {

    public static final String LOCATION_REJECTED = "LOCATION_REJECTED";

    public LocationRejectedException() {
        super("No tienes una entrega en curso");
    }

    public String getCode() {
        return LOCATION_REJECTED;
    }
}
