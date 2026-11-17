package com.mikuna.backend.address.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.common.config.DeliveryProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RF-12: la zona cubierta es una lista de distritos, sin distinguir mayúsculas, tildes ni espacios. */
class DeliveryZoneTest {

    private final DeliveryZone zone = new DeliveryZone(new DeliveryProperties(
            List.of("Ayacucho", "San Juan Bautista", "Jesús Nazareno"), BigDecimal.ONE, BigDecimal.ONE));

    @Test
    void aceptaUnDistritoDeLaLista() {
        assertThat(zone.covers("Ayacucho")).isTrue();
    }

    @Test
    void ignoraMayusculasTildesYEspacios() {
        assertThat(zone.covers("  AYACUCHO ")).isTrue();
        assertThat(zone.covers("jesus nazareno")).isTrue();
        assertThat(zone.covers("San   Juan  Bautista")).isTrue();
    }

    @Test
    void rechazaDistritosAusentesVaciosONulos() {
        assertThat(zone.covers("Huanta")).isFalse();
        assertThat(zone.covers("")).isFalse();
        assertThat(zone.covers("   ")).isFalse();
        assertThat(zone.covers(null)).isFalse();
    }
}
