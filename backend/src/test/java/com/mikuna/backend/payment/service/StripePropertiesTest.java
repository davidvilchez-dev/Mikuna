package com.mikuna.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Constitución III: solo claves de prueba; el mensaje de un rechazo nunca contiene la clave. */
class StripePropertiesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<ConstraintViolation<StripeProperties>> validate(String secret, String publishable,
            String currency) {
        return VALIDATOR.validate(new StripeProperties(secret, publishable, currency));
    }

    @Test
    void aceptaClavesDePruebaYMonedaDeTresLetras() {
        assertThat(validate("sk_test_abc123", "pk_test_abc123", "pen")).isEmpty();
    }

    @Test
    void rechazaClavesRealesVaciasOCruzadas() {
        assertThat(validate("sk_live_abc123", "pk_test_abc", "pen")).hasSize(1);
        assertThat(validate("sk_test_abc", "pk_live_abc", "pen")).hasSize(1);
        assertThat(validate("pk_test_abc", "pk_test_abc", "pen")).hasSize(1);
        assertThat(validate("", "pk_test_abc", "pen")).isNotEmpty();
        assertThat(validate("sk_test_abc", "", "pen")).isNotEmpty();
        assertThat(validate(null, null, null)).hasSize(3);
    }

    @Test
    void rechazaUnaMonedaInvalida() {
        assertThat(validate("sk_test_abc", "pk_test_abc", "PEN")).hasSize(1);
        assertThat(validate("sk_test_abc", "pk_test_abc", "soles")).hasSize(1);
    }

    @Test
    void losMensajesDeValidacionNoContienenLaClave() {
        String secret = "sk_live_clave_super_secreta";
        validate(secret, "pk_test_abc", "pen").forEach(violation ->
                assertThat(violation.getMessage()).doesNotContain(secret));
    }

    @Test
    void toStringNoExponeLasClaves() {
        String text = new StripeProperties("sk_test_secreto", "pk_test_publica", "pen").toString();
        assertThat(text).doesNotContain("sk_test_secreto").doesNotContain("pk_test_publica").contains("pen");
    }
}
