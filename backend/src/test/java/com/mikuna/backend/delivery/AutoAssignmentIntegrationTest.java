package com.mikuna.backend.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.DeliveryTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** RF-21 y RF-22: disponibilidad del repartidor y asignación automática al candidato que lleva más tiempo libre. */
class AutoAssignmentIntegrationTest extends DeliveryTestSupport {

    @Test
    void sinTokenEs401ConRolClienteEs403YCuentaDeshabilitadaEs403() throws Exception {
        getAs(null, COURIER + "/availability").andExpect(status().isUnauthorized());
        getAs(token, COURIER + "/availability").andExpect(status().isForbidden());
        getAs(ownerToken, COURIER + "/deliveries").andExpect(status().isForbidden());
        Account account = courierAccount("a@mail.com");
        String courier = loginAndGetToken("a@mail.com");
        disable(account.getId());
        getAs(courier, COURIER + "/availability").andExpect(status().isForbidden());
    }

    @Test
    void elRepartidorActivaYDesactivaSuDisponibilidad() throws Exception {
        courierAccount("a@mail.com");
        String a = loginAndGetToken("a@mail.com");

        getAs(a, COURIER + "/availability").andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        putAvailability(a, true).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.freeSince").isNotEmpty());
        getAs(a, COURIER + "/availability").andExpect(jsonPath("$.available").value(true));
        putAvailability(a, false).andExpect(jsonPath("$.available").value(false));
        mvc.perform(put(COURIER + "/availability").header("Authorization", "Bearer " + a)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void asignaSoloAlDisponibleHabilitadoYRegistraElCambioComoSistema() throws Exception {
        Account a = courierAccount("a@mail.com");
        Account b = courierAccount("b@mail.com");
        Account c = courierAccount("c@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        String tokenC = loginAndGetToken("c@mail.com");
        loginAndGetToken("b@mail.com"); // B nunca activa su disponibilidad
        setAvailable(tokenC, true);
        disable(c.getId());
        setAvailable(tokenA, true);

        UUID order = readyOrder("auto-1");

        assertThat(orderStatus(order)).isEqualTo("ASSIGNED");
        assertThat(courierOf(order)).isEqualTo(a.getId()).isNotEqualTo(b.getId());
        assertThat(transitions(order)).endsWith("READY→ASSIGNED");
        var last = changesOf(order).get(changesOf(order).size() - 1);
        assertThat(last.get("actor_role")).isEqualTo("SYSTEM");
        assertThat(last.get("actor_id")).isNull();
    }

    @Test
    void eligeAlQueLlevaMasTiempoLibreYDespuesAlSiguiente() throws Exception {
        Account a = courierAccount("a@mail.com");
        Account b = courierAccount("b@mail.com");
        setAvailable(loginAndGetToken("a@mail.com"), true);
        setAvailable(loginAndGetToken("b@mail.com"), true);
        freeSince(a.getId(), Instant.parse("2026-11-17T10:00:00Z"));
        freeSince(b.getId(), Instant.parse("2026-11-17T09:00:00Z")); // B lleva más tiempo libre

        UUID first = readyOrder("auto-2");
        UUID second = readyOrder("auto-3");

        assertThat(courierOf(first)).isEqualTo(b.getId());
        assertThat(courierOf(second)).isEqualTo(a.getId());
    }

    @Test
    void sinCandidatosQuedaPendienteYSeAsignaAlActivarseUnRepartidor() throws Exception {
        Account a = courierAccount("a@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");

        UUID older = readyOrder("auto-4");
        UUID newer = readyOrder("auto-5");

        assertThat(orderStatus(older)).isEqualTo("READY");
        assertThat(courierOf(older)).isNull();

        setAvailable(tokenA, true);

        assertThat(courierOf(older)).isEqualTo(a.getId());
        assertThat(orderStatus(older)).isEqualTo("ASSIGNED");
        assertThat(orderStatus(newer)).isEqualTo("READY");
        assertThat(courierOf(newer)).isNull();
    }

    @Test
    void unRepartidorConEntregaActivaNoRecibeOtraYLaTomaAlTerminar() throws Exception {
        Account a = courierAccount("a@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        setAvailable(tokenA, true);

        UUID first = readyOrder("auto-6");
        UUID second = readyOrder("auto-7");

        assertThat(courierOf(first)).isEqualTo(a.getId());
        assertThat(courierOf(second)).isNull();

        courierAct(tokenA, first, "pickup").andExpect(status().isOk());
        courierAct(tokenA, first, "complete").andExpect(status().isOk());

        assertThat(courierOf(second)).isEqualTo(a.getId());
        assertThat(orderStatus(second)).isEqualTo("ASSIGNED");
    }

    @Test
    void desactivarLaDisponibilidadMantieneLaEntregaAsignadaYNoSeOfreceNada() throws Exception {
        Account a = courierAccount("a@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        setAvailable(tokenA, true);
        UUID first = readyOrder("auto-8");

        setAvailable(tokenA, false);
        UUID second = readyOrder("auto-9");

        assertThat(courierOf(first)).isEqualTo(a.getId());
        assertThat(orderStatus(first)).isEqualTo("ASSIGNED");
        assertThat(courierOf(second)).isNull();
    }
}
