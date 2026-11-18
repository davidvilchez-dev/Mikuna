package com.mikuna.backend.common.exception;

/** El repartidor elegido no cumple las condiciones de candidato para esa entrega (409). */
public class CourierNotCandidateException extends DeliveryConflictException {

    public static final String CODE = "COURIER_NOT_CANDIDATE";

    public CourierNotCandidateException() {
        super(CODE, "El repartidor no está disponible para esta entrega");
    }
}
