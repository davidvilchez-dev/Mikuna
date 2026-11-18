package com.mikuna.backend.common.exception;

/** La pasarela rechazó la autorización o exige una verificación adicional no soportada (402). */
public class PaymentDeclinedException extends RuntimeException {

    public static final String DECLINED = "PAYMENT_DECLINED";
    public static final String ACTION_REQUIRED = "PAYMENT_ACTION_REQUIRED";

    private final String code;

    public PaymentDeclinedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
