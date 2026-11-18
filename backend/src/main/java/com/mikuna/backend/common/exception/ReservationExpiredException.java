package com.mikuna.backend.common.exception;

/** La pasarela indica que la reserva ya no puede cobrarse porque venció o fue cancelada. */
public class ReservationExpiredException extends RuntimeException {

    public ReservationExpiredException() {
        super("La reserva del pago ya no está disponible");
    }
}
