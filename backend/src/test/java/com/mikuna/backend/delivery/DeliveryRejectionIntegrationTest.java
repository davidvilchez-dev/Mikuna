package com.mikuna.backend.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.DeliveryTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-23: el rechazo del repartidor reasigna la entrega a otro, sin volver a ofrecérsela, y no rechaza el pedido. */
class DeliveryRejectionIntegrationTest extends DeliveryTestSupport {

    @Test
    void alRechazarSeReasignaAOtroCandidatoYNuncaAlQueRechazo() throws Exception {
        Account a = courierAccount("a@mail.com");
        Account b = courierAccount("b@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        String tokenB = loginAndGetToken("b@mail.com");
        setAvailable(tokenA, true);
        setAvailable(tokenB, true);
        freeSince(a.getId(), Instant.parse("2026-11-17T08:00:00Z"));
        freeSince(b.getId(), Instant.parse("2026-11-17T09:00:00Z"));
        UUID id = readyOrder("r-1");
        assertThat(courierOf(id)).isEqualTo(a.getId());

        long start = System.nanoTime();
        courierAct(tokenA, id, "reject").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        assertThat((System.nanoTime() - start) / 1_000_000_000.0).as("reasignación en menos de 5 s").isLessThan(5);

        assertThat(courierOf(id)).isEqualTo(b.getId());
        assertThat(orderStatus(id)).isEqualTo("ASSIGNED");
        assertThat(transitions(id)).containsSubsequence("READY→ASSIGNED", "ASSIGNED→READY", "READY→ASSIGNED");
        assertThat(count("rechazos_entrega")).isEqualTo(1);
        getAs(tokenA, COURIER + "/deliveries").andExpect(jsonPath("$.length()").value(0));
        getAs(tokenB, COURIER + "/deliveries").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void siTodosRechazanQuedaPendienteSinCandidatosYElPedidoNuncaSeRechaza() throws Exception {
        Account a = courierAccount("a@mail.com");
        Account b = courierAccount("b@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        String tokenB = loginAndGetToken("b@mail.com");
        setAvailable(tokenA, true);
        setAvailable(tokenB, true);
        freeSince(a.getId(), Instant.parse("2026-11-17T08:00:00Z"));
        freeSince(b.getId(), Instant.parse("2026-11-17T09:00:00Z"));
        UUID id = readyOrder("r-2");

        courierAct(tokenA, id, "reject").andExpect(status().isOk());
        courierAct(tokenB, id, "reject", "{\"reason\":\"moto averiada\"}").andExpect(status().isOk());

        assertThat(orderStatus(id)).isEqualTo("READY");
        assertThat(courierOf(id)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from pedidos where status in ('REJECTED','CANCELLED')",
                Integer.class)).isZero();
        var rejectionB = changesOf(id).get(changesOf(id).size() - 1);
        assertThat(rejectionB.get("reason")).isEqualTo("moto averiada");
        assertThat(rejectionB.get("actor_role")).isEqualTo("COURIER");

        // un tercer repartidor disponible la recibe; ni A ni B pueden recibirla de nuevo
        Account c = courierAccount("c@mail.com");
        setAvailable(loginAndGetToken("c@mail.com"), true);
        assertThat(courierOf(id)).isEqualTo(c.getId());
        assertThat(orderStatus(id)).isEqualTo("ASSIGNED");
    }

    @Test
    void elQueRechazoNoRecibeEsaEntregaAlReactivarseNiPorLaAsignacionManual() throws Exception {
        Account a = courierAccount("a@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        setAvailable(tokenA, true);
        UUID id = readyOrder("r-3");
        courierAct(tokenA, id, "reject").andExpect(status().isOk());
        assertThat(courierOf(id)).isNull();

        setAvailable(tokenA, false);
        setAvailable(tokenA, true);
        assertThat(courierOf(id)).isNull();

        adminAssign(adminToken(), id, a.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_CANDIDATE"));
        assertThat(courierOf(id)).isNull();
    }

    @Test
    void soloSePuedeRechazarEnEstadoAsignado() throws Exception {
        Account a = courierAccount("a@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        setAvailable(tokenA, true);
        UUID id = readyOrder("r-4");
        courierAct(tokenA, id, "pickup").andExpect(status().isOk());

        courierAct(tokenA, id, "reject").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        assertThat(orderStatus(id)).isEqualTo("PICKED_UP");
        assertThat(courierOf(id)).isEqualTo(a.getId());
        assertThat(count("rechazos_entrega")).isZero();
    }
}
