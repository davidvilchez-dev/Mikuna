package com.mikuna.backend.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.DeliveryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-24 y FR-010a: el administrador asigna a un candidato y libera las entregas de repartidores inhabilitados. */
class AdminDeliveryIntegrationTest extends DeliveryTestSupport {

    @Test
    void sinTokenEs401YConOtroRolEs403() throws Exception {
        UUID id = readyOrder("m-1");
        courierAccount("a@mail.com");
        String courier = loginAndGetToken("a@mail.com");
        for (String bearer : new String[] {token, ownerToken, courier}) {
            getAs(bearer, ADMIN_DELIVERIES + "/pending").andExpect(status().isForbidden());
            adminAct(bearer, id, "release").andExpect(status().isForbidden());
            adminAssign(bearer, id, UUID.randomUUID()).andExpect(status().isForbidden());
        }
        getAs(null, ADMIN_DELIVERIES + "/pending").andExpect(status().isUnauthorized());
        adminAct(null, id, "release").andExpect(status().isUnauthorized());
        adminAssign(null, id, UUID.randomUUID()).andExpect(status().isUnauthorized());
        assertThat(courierOf(id)).isNull();
    }

    @Test
    void laListaMuestraLasPendientesConSusCandidatos() throws Exception {
        UUID older = readyOrder("m-2");
        UUID newer = readyOrder("m-3");
        Account a = courierAccount("a@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        String admin = adminToken();
        // A está disponible pero ocupado con otra entrega: no es candidato de las pendientes
        setAvailable(tokenA, true);
        assertThat(courierOf(older)).isEqualTo(a.getId());
        Account b = courierAccount("b@mail.com");
        setAvailable(loginAndGetToken("b@mail.com"), false);

        getAs(admin, ADMIN_DELIVERIES + "/pending").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(newer.toString()))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].courierDisabled").value(false))
                .andExpect(jsonPath("$[0].candidates.length()").value(0));

        setAvailable(loginAndGetToken("b@mail.com"), true);
        assertThat(courierOf(newer)).isEqualTo(b.getId());
    }

    @Test
    void asignarAUnCandidatoDejaLaEntregaAsignadaConElAdministradorComoResponsable() throws Exception {
        UUID id = readyOrder("m-4");
        Account a = courierAccount("a@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        setAvailable(tokenA, false);
        String admin = adminToken();
        // A pasa a no disponible tras activarse: lo hace candidato solo si está disponible, así que se activa en BD
        jdbc.update("update repartidores_disponibilidad set available = true where courier_id = ?", a.getId());

        getAs(admin, ADMIN_DELIVERIES + "/pending").andExpect(jsonPath("$[0].candidates.length()").value(1))
                .andExpect(jsonPath("$[0].candidates[0].courierId").value(a.getId().toString()));
        adminAssign(admin, id, a.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.courierId").value(a.getId().toString()));

        assertThat(courierOf(id)).isEqualTo(a.getId());
        assertThat(orderStatus(id)).isEqualTo("ASSIGNED");
        var last = changesOf(id).get(changesOf(id).size() - 1);
        assertThat(last.get("actor_role")).isEqualTo("ADMIN");
        assertThat(last.get("actor_id")).isNotNull();
    }

    @Test
    void asignarUnaEntregaYaAsignadaOUnNoCandidatoEsConflictoSinCambios() throws Exception {
        Account a = courierAccount("a@mail.com");
        Account b = courierAccount("b@mail.com");
        Account c = courierAccount("c@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        String tokenB = loginAndGetToken("b@mail.com");
        String tokenC = loginAndGetToken("c@mail.com");
        String admin = adminToken();
        setAvailable(tokenA, true);
        UUID assigned = readyOrder("m-5");
        UUID pending = readyOrder("m-6");
        assertThat(courierOf(assigned)).isEqualTo(a.getId());

        adminAssign(admin, assigned, b.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        assertThat(courierOf(assigned)).isEqualTo(a.getId());

        setAvailable(tokenC, true); // C toma la pendiente: no queda ninguna
        assertThat(courierOf(pending)).isEqualTo(c.getId());
        UUID third = readyOrder("m-7");
        loginAndGetToken("b@mail.com");
        adminAssign(admin, third, a.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_CANDIDATE")); // A tiene entrega activa
        adminAssign(admin, third, b.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_CANDIDATE")); // B nunca se declaró disponible
        adminAssign(admin, third, UUID.randomUUID()).andExpect(status().isConflict()); // no existe como repartidor
        act(admin, ADMIN_DELIVERIES + "/" + third + "/assign", "{}").andExpect(status().isBadRequest());
        act(admin, ADMIN_DELIVERIES + "/" + third + "/assign", "{\"courierId\":\"no-uuid\"}")
                .andExpect(status().isBadRequest());
        adminAssign(admin, UUID.randomUUID(), a.getId()).andExpect(status().isNotFound());
        disable(c.getId());
        setAvailable(tokenB, true);
        assertThat(courierOf(third)).isEqualTo(b.getId()); // la automática sí funciona; la manual no cambió nada antes
    }

    @Test
    void liberarUnaEntregaDeUnRepartidorInhabilitadoLaDejaListaSinRechazoYSeReasigna() throws Exception {
        Account a = courierAccount("a@mail.com");
        Account b = courierAccount("b@mail.com");
        String tokenA = loginAndGetToken("a@mail.com");
        String tokenB = loginAndGetToken("b@mail.com");
        String admin = adminToken();
        setAvailable(tokenA, true);
        UUID id = readyOrder("m-8");
        assertThat(courierOf(id)).isEqualTo(a.getId());

        adminAct(admin, id, "release").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_STILL_ENABLED"));
        assertThat(courierOf(id)).isEqualTo(a.getId());

        disable(a.getId());
        getAs(tokenA, COURIER + "/deliveries").andExpect(status().isForbidden());
        getAs(admin, ADMIN_DELIVERIES + "/pending").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courierDisabled").value(true))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"));

        setAvailable(tokenB, true); // B es candidato; al liberar se le asigna sin pasar por rechazo
        adminAct(admin, id, "release").andExpect(status().isOk());

        assertThat(count("rechazos_entrega")).isZero();
        assertThat(courierOf(id)).isEqualTo(b.getId());
        assertThat(orderStatus(id)).isEqualTo("ASSIGNED");
        assertThat(transitions(id)).containsSubsequence("READY→ASSIGNED", "ASSIGNED→READY", "READY→ASSIGNED");
        var release = changesOf(id).get(changesOf(id).size() - 2);
        assertThat(release.get("actor_role")).isEqualTo("ADMIN");
        assertThat(release.get("actor_id")).isNotNull();
    }

    @Test
    void liberarUnaEntregaNoAsignadaOInexistenteEsConflictoOnoEncontrada() throws Exception {
        UUID pending = readyOrder("m-9");
        String admin = adminToken();

        adminAct(admin, pending, "release").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        adminAct(admin, UUID.randomUUID(), "release").andExpect(status().isNotFound());
    }
}
