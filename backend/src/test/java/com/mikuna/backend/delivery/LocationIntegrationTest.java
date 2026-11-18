package com.mikuna.backend.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.DeliveryTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF-26: ubicación vigente sin historial, orden garantizado y hitos permanentes al recoger y al entregar. */
class LocationIntegrationTest extends DeliveryTestSupport {

    private Account courier;
    private String courierToken;

    @BeforeEach
    void courierReady() throws Exception {
        courier = courierAccount("a@mail.com");
        courierToken = loginAndGetToken("a@mail.com");
        setAvailable(courierToken, true);
    }

    private List<Map<String, Object>> vigentes() {
        return jdbc.queryForList("select * from ubicaciones_repartidor");
    }

    @Test
    void variasUbicacionesDejanUnaSolaFilaConLaUltima() throws Exception {
        UUID order = readyOrder("l-1");
        Instant base = Instant.now().minusSeconds(60);

        sendLocation(courierToken, -13.10, -74.20, base).andExpect(status().isNoContent());
        sendLocation(courierToken, -13.11, -74.21, base.plusSeconds(10)).andExpect(status().isNoContent());
        sendLocation(courierToken, -13.12, -74.22, base.plusSeconds(20)).andExpect(status().isNoContent());

        assertThat(vigentes()).hasSize(1);
        assertThat(vigentes().get(0).get("latitude")).isEqualTo(-13.12);
        assertThat(vigentes().get(0).get("order_id")).isEqualTo(order);
        assertThat(vigentes().get(0).get("courier_id")).isEqualTo(courier.getId());
    }

    @Test
    void unaUbicacionMasAntiguaNoReemplazaALaNueva() throws Exception {
        readyOrder("l-2");
        Instant base = Instant.now().minusSeconds(60);
        sendLocation(courierToken, -13.12, -74.22, base.plusSeconds(20)).andExpect(status().isNoContent());

        sendLocation(courierToken, -1.0, -1.0, base).andExpect(status().isNoContent());

        assertThat(vigentes()).hasSize(1);
        assertThat(vigentes().get(0).get("latitude")).isEqualTo(-13.12);
    }

    @Test
    void sinEntregaEnCursoEsConflictoYNoSeGuardaNada() throws Exception {
        sendLocation(courierToken, -13.1, -74.2, Instant.now()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_REJECTED"));

        assertThat(vigentes()).isEmpty();
    }

    @Test
    void conLaEntregaYaEntregadaSeRechaza() throws Exception {
        UUID order = readyOrder("l-3");
        courierAct(courierToken, order, "pickup").andExpect(status().isOk());
        courierAct(courierToken, order, "complete").andExpect(status().isOk());

        sendLocation(courierToken, -13.1, -74.2, Instant.now()).andExpect(status().isConflict());
    }

    @Test
    void lasCoordenadasInvalidasSeRechazanSinModificarLaVigente() throws Exception {
        readyOrder("l-4");
        Instant base = Instant.now().minusSeconds(30);
        sendLocation(courierToken, -13.12, -74.22, base).andExpect(status().isNoContent());

        sendLocation(courierToken, 91.0, -74.2, base.plusSeconds(5)).andExpect(status().isBadRequest());
        sendLocation(courierToken, -13.1, 181.0, base.plusSeconds(5)).andExpect(status().isBadRequest());
        sendLocationJson(courierToken, "{\"latitude\":-13.1,\"recordedAt\":\"" + base.plusSeconds(5) + "\"}")
                .andExpect(status().isBadRequest());
        sendLocation(courierToken, -13.1, -74.2, Instant.now().plusSeconds(600)).andExpect(status().isBadRequest());

        assertThat(vigentes()).hasSize(1);
        assertThat(vigentes().get(0).get("latitude")).isEqualTo(-13.12);
    }

    @Test
    void sinSesionEs401YConOtroRolEs403() throws Exception {
        readyOrder("l-5");

        sendLocation(null, -13.1, -74.2, Instant.now()).andExpect(status().isUnauthorized());
        sendLocation(token, -13.1, -74.2, Instant.now()).andExpect(status().isForbidden());
        sendLocation(ownerToken, -13.1, -74.2, Instant.now()).andExpect(status().isForbidden());
        assertThat(vigentes()).isEmpty();
    }

    @Test
    void alRecogerYEntregarQuedanDosHitosYNingunaUbicacionVigente() throws Exception {
        UUID order = readyOrder("l-6");
        Instant base = Instant.now().minusSeconds(60);

        sendLocation(courierToken, -13.10, -74.20, base).andExpect(status().isNoContent());
        courierAct(courierToken, order, "pickup").andExpect(status().isOk());
        sendLocation(courierToken, -13.20, -74.30, base.plusSeconds(30)).andExpect(status().isNoContent());
        sendLocation(courierToken, -13.30, -74.40, base.plusSeconds(40)).andExpect(status().isNoContent());
        courierAct(courierToken, order, "complete").andExpect(status().isOk());

        List<Map<String, Object>> hitos = jdbc.queryForList(
                "select type, latitude from hitos_entrega where order_id = ? order by type", order);
        assertThat(hitos).hasSize(2);
        assertThat(hitos.get(0)).containsEntry("type", "DELIVERY").containsEntry("latitude", -13.30);
        assertThat(hitos.get(1)).containsEntry("type", "PICKUP").containsEntry("latitude", -13.10);
        assertThat(vigentes()).isEmpty();
    }

    @Test
    void sinUbicacionConocidaRecogerFuncionaYNoCreaHito() throws Exception {
        UUID order = readyOrder("l-7");

        courierAct(courierToken, order, "pickup").andExpect(status().isOk());

        assertThat(orderStatus(order)).isEqualTo("PICKED_UP");
        assertThat(count("hitos_entrega")).isZero();
    }

    @Test
    void alRechazarLaEntregaLaUbicacionVigenteDesaparece() throws Exception {
        UUID order = readyOrder("l-8");
        sendLocation(courierToken, -13.1, -74.2, Instant.now().minusSeconds(10)).andExpect(status().isNoContent());
        assertThat(vigentes()).hasSize(1);

        courierAct(courierToken, order, "reject", "{}").andExpect(status().isOk());

        assertThat(vigentes()).isEmpty();
    }
}
