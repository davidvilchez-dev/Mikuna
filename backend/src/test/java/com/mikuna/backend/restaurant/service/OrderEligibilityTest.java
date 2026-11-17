package com.mikuna.backend.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.support.TestRestaurants;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/** RF-06: regla de «habilitado para recibir pedidos» con reloj fijo (America/Lima = UTC-5). */
class OrderEligibilityTest {

    private static final UUID OWNER = UUID.randomUUID();

    private static OrderEligibility at(String utcInstant) {
        return new OrderEligibility(Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC), "America/Lima");
    }

    /** Lunes 2026-11-16 15:00 en Lima. */
    private static OrderEligibility mondayAfternoon() {
        return at("2026-11-16T20:00:00Z");
    }

    @Test
    void aprobadoDisponibleDentroDeHorarioPuedeRecibirPedidos() {
        assertThat(mondayAfternoon().canReceiveOrders(TestRestaurants.approvedOpenOnMonday(OWNER, true), true)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = RestaurantStatus.class, names = {"PENDING", "REJECTED"})
    void pendienteORechazadoNuncaPuedeRecibirPedidos(RestaurantStatus status) {
        Restaurant restaurant = TestRestaurants.approvedOpenOnMonday(OWNER, true);
        ReflectionTestUtils.setField(restaurant, "status", status.name());
        assertThat(mondayAfternoon().canReceiveOrders(restaurant, true)).isFalse();
    }

    @Test
    void cuentaInhabilitadaNoPuedeRecibirPedidos() {
        assertThat(mondayAfternoon().canReceiveOrders(TestRestaurants.approvedOpenOnMonday(OWNER, true), false)).isFalse();
    }

    @Test
    void noDisponibleNoPuedeRecibirPedidos() {
        assertThat(mondayAfternoon().canReceiveOrders(TestRestaurants.approvedOpenOnMonday(OWNER, false), true)).isFalse();
    }

    @Test
    void fueraDeHorarioNoPuedeRecibirPedidos() {
        // Lunes 08:59 en Lima
        assertThat(at("2026-11-16T13:59:00Z").canReceiveOrders(TestRestaurants.approvedOpenOnMonday(OWNER, true), true)).isFalse();
    }

    @Test
    void laAperturaEsInclusivaYElCierreExclusivo() {
        Restaurant restaurant = TestRestaurants.approvedOpenOnMonday(OWNER, true);
        assertThat(at("2026-11-16T14:00:00Z").canReceiveOrders(restaurant, true)).isTrue();  // 09:00
        assertThat(at("2026-11-17T01:59:00Z").canReceiveOrders(restaurant, true)).isTrue();  // 20:59
        assertThat(at("2026-11-17T02:00:00Z").canReceiveOrders(restaurant, true)).isFalse(); // 21:00
    }

    @Test
    void diaCerradoNoPuedeRecibirPedidos() {
        // Martes 2026-11-17 15:00 en Lima
        assertThat(at("2026-11-17T20:00:00Z").canReceiveOrders(TestRestaurants.approvedOpenOnMonday(OWNER, true), true)).isFalse();
    }

    @Test
    void sinHorarioGuardadoNoPuedeRecibirPedidos() {
        Restaurant restaurant = TestRestaurants.restaurant(OWNER, RestaurantStatus.APPROVED);
        restaurant.setAvailable(true);
        assertThat(mondayAfternoon().canReceiveOrders(restaurant, true)).isFalse();
    }

    @Test
    void atencionPausadaTienePrioridadSobreElHorario() {
        assertThat(mondayAfternoon().attentionStatus(TestRestaurants.approvedOpenOnMonday(OWNER, false)))
                .isEqualTo(AttentionStatus.PAUSED);
    }

    @Test
    void atencionAbiertaDentroDelHorarioYFueraDeHorarioEnElResto() {
        Restaurant restaurant = TestRestaurants.approvedOpenOnMonday(OWNER, true);
        assertThat(mondayAfternoon().attentionStatus(restaurant)).isEqualTo(AttentionStatus.OPEN);
        // lunes 08:59 y martes (cerrado) en Lima
        assertThat(at("2026-11-16T13:59:00Z").attentionStatus(restaurant)).isEqualTo(AttentionStatus.OUTSIDE_HOURS);
        assertThat(at("2026-11-17T20:00:00Z").attentionStatus(restaurant)).isEqualTo(AttentionStatus.OUTSIDE_HOURS);
    }

    @Test
    void atencionSinHorarioGuardadoEsFueraDeHorario() {
        Restaurant restaurant = TestRestaurants.restaurant(OWNER, RestaurantStatus.APPROVED);
        restaurant.setAvailable(true);
        assertThat(mondayAfternoon().attentionStatus(restaurant)).isEqualTo(AttentionStatus.OUTSIDE_HOURS);
    }

    @Test
    void usaLaZonaHorariaConfiguradaYNoLaDelReloj() {
        // 02:00Z del martes es lunes 21:00 en Lima: fuera de horario aunque en UTC sea martes
        assertThat(at("2026-11-17T02:00:00Z").canReceiveOrders(TestRestaurants.approvedOpenOnMonday(OWNER, true), true)).isFalse();
    }
}
