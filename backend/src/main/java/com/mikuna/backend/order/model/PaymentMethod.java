package com.mikuna.backend.order.model;

/** Método de pago ofrecido al confirmar (RF-15). Solo tarjeta, procesada por Stripe en modo de pruebas. */
public enum PaymentMethod {
    CARD("Tarjeta de crédito o débito");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
