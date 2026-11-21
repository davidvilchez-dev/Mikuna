package com.mikuna.backend.order.model;

/** Método de pago ofrecido al confirmar (RF-15). Tarjeta (Stripe en pruebas) o efectivo contra entrega. */
public enum PaymentMethod {
    CARD("Tarjeta de crédito o débito"),
    CASH("Efectivo");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
