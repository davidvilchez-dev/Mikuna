package com.mikuna.backend.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.common.config.DeliveryProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Las reglas de reparto no tienen valores por defecto: lo faltante o inválido se rechaza. */
class DeliveryPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(List<String> districts, String fee, String rate) {
        DeliveryProperties props = new DeliveryProperties(districts,
                fee == null ? null : new BigDecimal(fee), rate == null ? null : new BigDecimal(rate));
        return validator.validate(props).isEmpty();
    }

    @Test
    void aceptaValoresValidos() {
        assertThat(valid(List.of("Ayacucho"), "5.00", "5")).isTrue();
        assertThat(valid(List.of("Ayacucho"), "0", "0")).isTrue();
        assertThat(valid(List.of("Ayacucho"), "0.00", "100")).isTrue();
    }

    @Test
    void rechazaListaVaciaONula() {
        assertThat(valid(List.of(), "5.00", "5")).isFalse();
        assertThat(valid(null, "5.00", "5")).isFalse();
        assertThat(valid(List.of(" "), "5.00", "5")).isFalse();
    }

    @Test
    void rechazaTarifaInvalida() {
        assertThat(valid(List.of("A"), "-0.01", "5")).isFalse();
        assertThat(valid(List.of("A"), "5.001", "5")).isFalse();
        assertThat(valid(List.of("A"), null, "5")).isFalse();
    }

    @Test
    void rechazaComisionFueraDeRango() {
        assertThat(valid(List.of("A"), "5", "-1")).isFalse();
        assertThat(valid(List.of("A"), "5", "100.01")).isFalse();
        assertThat(valid(List.of("A"), "5", null)).isFalse();
    }
}
