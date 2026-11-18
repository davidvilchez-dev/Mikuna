package com.mikuna.backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.HistoryTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-30 y RF-31: el repartidor ve sus entregas (concluidas y en curso) sin comisión ni importes. */
class CourierHistoryIntegrationTest extends HistoryTestSupport {

    @Test
    void sinTokenEs401YConOtroRolEs403() throws Exception {
        history(null, COURIER_HISTORY, "").andExpect(status().isUnauthorized());
        history(token, COURIER_HISTORY, "").andExpect(status().isForbidden());
        history(ownerToken, COURIER_HISTORY, "").andExpect(status().isForbidden());
    }

    @Test
    void veSusEntregasConcluidasYEnCursoConSuCalificacion() throws Exception {
        UUID delivered = deliveredOrder("ch-1");
        rate(token, delivered, "{\"value\":4}").andExpect(status().isCreated());
        UUID assigned = readyOrder("ch-2");
        UUID notYetAssigned = placeOrder("ch-3");

        history(courierToken, COURIER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].orderId").value(assigned.toString()))
                .andExpect(jsonPath("$.items[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.items[0].rating").doesNotExist())
                .andExpect(jsonPath("$.items[1].orderId").value(delivered.toString()))
                .andExpect(jsonPath("$.items[1].status").value("DELIVERED"))
                .andExpect(jsonPath("$.items[1].rating").value(4));
        assertThat(notYetAssigned).isNotNull();

        courierAct(courierToken, assigned, "pickup").andExpect(status().isOk());
        history(courierToken, COURIER_HISTORY, "").andExpect(jsonPath("$.items[0].status").value("PICKED_UP"));
    }

    @Test
    void muestraSoloLoQueElRepartidorNecesitaYNoComisionNiImportes() throws Exception {
        deliveredOrder("ch-4");

        history(courierToken, COURIER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].customerName").value("Cliente"))
                .andExpect(jsonPath("$.items[0].restaurant.name").value("La Picantería"))
                .andExpect(jsonPath("$.items[0].deliveryAddress.street").value("Jr. Lima 123"))
                .andExpect(jsonPath("$.items[0].deliveryAddress.district").value("Ayacucho"))
                .andExpect(jsonPath("$.items[0].deliveryFee").value(5.0))
                .andExpect(jsonPath("$.items[0].lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.items[0].lines[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lines[0].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.items[0].lines[0].lineTotal").doesNotExist())
                .andExpect(jsonPath("$.items[0].totals").doesNotExist())
                .andExpect(jsonPath("$.items[0].payment").doesNotExist())
                .andExpect(jsonPath("$.items[0].canRate").value(false));

        String body = history(courierToken, COURIER_HISTORY, "").andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("commission").doesNotContain("subtotal").doesNotContain("52.25")
                .doesNotContain("22.5").doesNotContain("45.0").doesNotContain("phone").doesNotContain("Phone");
    }

    @Test
    void otroRepartidorNoVeLasEntregasAjenas() throws Exception {
        deliveredOrder("ch-5");
        courierAccount("repartidor-b@mail.com");
        String tokenB = loginAndGetToken("repartidor-b@mail.com");

        history(tokenB, COURIER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void unaEntregaRechazadaYReasignadaYaNoApareceEnSuHistorial() throws Exception {
        Account b = courierAccount("repartidor-b@mail.com");
        String tokenB = loginAndGetToken("repartidor-b@mail.com");
        setAvailable(tokenB, true);
        freeSince(courierA.getId(), Instant.parse("2026-11-17T08:00:00Z"));
        freeSince(b.getId(), Instant.parse("2026-11-17T09:00:00Z"));
        UUID id = readyOrder("ch-6");
        assertThat(courierOf(id)).isEqualTo(courierA.getId());
        history(courierToken, COURIER_HISTORY, "").andExpect(jsonPath("$.totalItems").value(1));

        courierAct(courierToken, id, "reject").andExpect(status().isOk());

        assertThat(courierOf(id)).isEqualTo(b.getId());
        history(courierToken, COURIER_HISTORY, "").andExpect(jsonPath("$.totalItems").value(0));
        history(tokenB, COURIER_HISTORY, "").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].orderId").value(id.toString()));
    }

    @Test
    void elFiltroDeEstadoSoloAdmiteLosEstadosDelRepartidor() throws Exception {
        deliveredOrder("ch-7");
        readyOrder("ch-8");

        history(courierToken, COURIER_HISTORY, "?status=PENDING").andExpect(status().isBadRequest());
        history(courierToken, COURIER_HISTORY, "?status=READY").andExpect(status().isBadRequest());
        history(courierToken, COURIER_HISTORY, "?status=DELIVERED").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
        history(courierToken, COURIER_HISTORY, "?status=ASSIGNED").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void unRepartidorSinDisponibilidadConservaSuHistorial() throws Exception {
        deliveredOrder("ch-9");
        setAvailable(courierToken, false);

        history(courierToken, COURIER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void unaCuentaBloqueadaNoPuedeConsultarNada() throws Exception {
        deliveredOrder("ch-10");
        disable(courierA.getId());

        int code = history(courierToken, COURIER_HISTORY, "").andReturn().getResponse().getStatus();

        assertThat(code).isIn(401, 403);
    }
}
