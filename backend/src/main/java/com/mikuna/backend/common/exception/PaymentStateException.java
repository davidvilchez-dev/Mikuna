package com.mikuna.backend.common.exception;

/** El pago no está en un estado que admita la operación (p. ej. ya cobrado o liberado) (409). */
public class PaymentStateException extends RuntimeException {

    public PaymentStateException() {
        super("El pago ya no está disponible para esa operación");
    }
}
