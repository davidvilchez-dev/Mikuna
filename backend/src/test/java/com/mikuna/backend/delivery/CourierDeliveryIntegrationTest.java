package com.mikuna.backend.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.DeliveryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF-25: el repartidor ve solo sus entregas, registra la recogida y la entrega, y no opera las ajenas. */
class CourierDeliveryIntegrationTest extends DeliveryTestSupport {

    private Account courierA;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void couriers() throws Exception {
        courierA = courierAccount("a@mail.com");
        courierAccount("b@mail.com");
        tokenA = loginAndGetToken("a@mail.com");
        tokenB = loginAndGetToken("b@mail.com");
    }

    @Test
    void sinTokenEs401ConOtroRolEs403() throws Exception {
        UUID id = readyOrder("c-1");
        for (String action : new String[] {"pickup", "complete", "reject"}) {
            courierAct(null, id, action).andExpect(status().isUnauthorized());
            courierAct(token, id, action).andExpect(status().isForbidden());
            courierAct(ownerToken, id, action).andExpect(status().isForbidden());
        }
        assertThat(orderStatus(id)).isEqualTo("READY");
    }

    @Test
    void laListaMuestraSoloLasEntregasPropiasConLosDatosDelReparto() throws Exception {
        setAvailable(tokenA, true);
        UUID id = readyOrder("c-2");

        getAs(tokenA, COURIER + "/deliveries").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(id.toString()))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].restaurant.name").value("La Picantería"))
                .andExpect(jsonPath("$[0].delivery.street").value("Jr. Lima 123"))
                .andExpect(jsonPath("$[0].lines[0].productName").value("Lomo saltado"))
                .andExpect(jsonPath("$[0].lines[0].quantity").value(2));
        getAs(tokenB, COURIER + "/deliveries").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recogerYEntregarLlevanElPedidoHastaEntregadoConSuRegistro() throws Exception {
        setAvailable(tokenA, true);
        UUID id = readyOrder("c-3");

        courierAct(tokenA, id, "pickup").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PICKED_UP"));
        assertThat(orderStatus(id)).isEqualTo("PICKED_UP");
        getAs(tokenA, COURIER + "/deliveries").andExpect(jsonPath("$[0].status").value("PICKED_UP"));
        courierAct(tokenA, id, "complete").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));

        assertThat(orderStatus(id)).isEqualTo("DELIVERED");
        assertThat(transitions(id)).containsSubsequence("READY→ASSIGNED", "ASSIGNED→PICKED_UP", "PICKED_UP→DELIVERED");
        var changes = changesOf(id);
        var picked = changes.get(changes.size() - 2);
        var delivered = changes.get(changes.size() - 1);
        assertThat(picked.get("actor_role")).isEqualTo("COURIER");
        assertThat(picked.get("actor_id")).isEqualTo(courierA.getId());
        assertThat(delivered.get("actor_role")).isEqualTo("COURIER");
        assertThat(delivered.get("changed_at")).isNotNull();
        getAs(tokenA, COURIER + "/deliveries").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void saltosRetrocesosYReenviosDanConflictoSinDuplicarRegistros() throws Exception {
        setAvailable(tokenA, true);
        UUID id = readyOrder("c-4");
        int before = changesOf(id).size();

        courierAct(tokenA, id, "complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        assertThat(changesOf(id)).hasSize(before);

        courierAct(tokenA, id, "pickup").andExpect(status().isOk());
        courierAct(tokenA, id, "pickup").andExpect(status().isConflict());
        courierAct(tokenA, id, "reject").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").isNotEmpty());
        courierAct(tokenA, id, "complete").andExpect(status().isOk());
        courierAct(tokenA, id, "complete").andExpect(status().isConflict());
        courierAct(tokenA, id, "pickup").andExpect(status().isConflict());

        assertThat(orderStatus(id)).isEqualTo("DELIVERED");
        assertThat(changesOf(id)).hasSize(before + 2);
    }

    @Test
    void unRepartidorNoOperaLaEntregaDeOtroNiUnaInexistente() throws Exception {
        setAvailable(tokenA, true);
        UUID id = readyOrder("c-5");
        int before = changesOf(id).size();

        for (String action : new String[] {"pickup", "complete", "reject"}) {
            courierAct(tokenB, id, action).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").isNotEmpty());
            courierAct(tokenB, UUID.randomUUID(), action).andExpect(status().isNotFound());
        }

        assertThat(orderStatus(id)).isEqualTo("ASSIGNED");
        assertThat(courierOf(id)).isEqualTo(courierA.getId());
        assertThat(changesOf(id)).hasSize(before);
    }

    @Test
    void elClienteNoCancelaUnPedidoAsignadoRecogidoOEntregadoYNoHayReembolso() throws Exception {
        setAvailable(tokenA, true);
        UUID id = readyOrder("c-7");

        customerCancel(id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reembolsos")));
        courierAct(tokenA, id, "pickup").andExpect(status().isOk());
        customerCancel(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reembolsos")));
        courierAct(tokenA, id, "complete").andExpect(status().isOk());
        customerCancel(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reembolsos")));

        assertThat(orderStatus(id)).isEqualTo("DELIVERED");
        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
    }

    @Test
    void elMotivoDelRechazoAdmiteHastaDoscientosCaracteres() throws Exception {
        setAvailable(tokenA, true);
        UUID id = readyOrder("c-6");

        act(tokenA, COURIER + "/deliveries/" + id + "/reject", "{\"reason\":\"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest());
        act(tokenA, COURIER + "/deliveries/" + id + "/reject", "{\"campo\":1}").andExpect(status().isBadRequest());
        assertThat(orderStatus(id)).isEqualTo("ASSIGNED");
    }
}
