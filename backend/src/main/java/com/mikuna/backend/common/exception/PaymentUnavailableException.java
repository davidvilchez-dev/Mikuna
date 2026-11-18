package com.mikuna.backend.common.exception;

/** La pasarela no respondió o falló; no se hizo ningún cobro ni queda reserva activa (503). */
public class PaymentUnavailableException extends RuntimeException {

    public static final String CODE = "PAYMENT_UNAVAILABLE";
    public static final String MESSAGE =
            "No pudimos procesar el pago ahora; no se hizo ningún cobro, intenta de nuevo";

    public PaymentUnavailableException() {
        super(MESSAGE);
    }
}
