package com.mikuna.backend.payment.service;

/** Resultado de intentar cobrar la reserva de un pedido. */
public enum CaptureResult {
    /** El monto reservado se cobró por completo. */
    CAPTURED,
    /** La reserva venció o ya no existe en la pasarela: no hubo cobro y el pago quedó liberado. */
    RESERVATION_EXPIRED
}
