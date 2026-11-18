package com.mikuna.backend.payment.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Credenciales de Stripe, solo en modo de pruebas (constitución III): la clave secreta DEBE empezar con
 * {@code sk_test_} y la publicable con {@code pk_test_}; si no, el arranque falla sin mostrar la clave.
 */
@Validated
@ConfigurationProperties("stripe")
public record StripeProperties(
        @NotBlank(message = "stripe.secret-key es obligatoria (clave sk_test_ en backend/.env)")
        @Pattern(regexp = "^sk_test_.+$", message = "stripe.secret-key debe ser una clave de pruebas (sk_test_...)")
        String secretKey,

        @NotBlank(message = "stripe.publishable-key es obligatoria (clave pk_test_ en backend/.env)")
        @Pattern(regexp = "^pk_test_.+$", message = "stripe.publishable-key debe ser una clave de pruebas (pk_test_...)")
        String publishableKey,

        @NotBlank(message = "stripe.currency es obligatoria")
        @Pattern(regexp = "^[a-z]{3}$", message = "stripe.currency debe ser un código de 3 letras minúsculas")
        String currency) {

    @Override
    public String toString() {
        return "StripeProperties[currency=" + currency + "]";
    }
}
