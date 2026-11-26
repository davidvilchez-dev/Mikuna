package com.mikuna.backend.common.exception;

/** La pasarela no reembolsó el pago (rechazo o sin respuesta); el pedido no cambió (spec 014, 503). */
public class RefundFailedException extends RuntimeException {

    public static final String CODE = "REFUND_FAILED";

    public RefundFailedException() {
        super("No se pudo reembolsar el pago. El pedido no cambió; inténtalo de nuevo.");
    }
}
