package com.mikuna.backend.common.exception;

/** Se intentó liberar una entrega cuyo repartidor sigue habilitado (409). */
public class CourierStillEnabledException extends DeliveryConflictException {

    public static final String CODE = "COURIER_STILL_ENABLED";

    public CourierStillEnabledException() {
        super(CODE, "El repartidor sigue habilitado: solo se liberan entregas de repartidores inhabilitados");
    }
}
