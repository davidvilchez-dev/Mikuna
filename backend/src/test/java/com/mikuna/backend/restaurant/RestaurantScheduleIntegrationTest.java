package com.mikuna.backend.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** RF-07 y RF-04: horario semanal y disponibilidad del restaurante aprobado. */
class RestaurantScheduleIntegrationTest extends IntegrationTestSupport {

    private static final String ME = "/api/restaurant/me";

    private Account owner;
    private String token;

    @BeforeEach
    void prepareRestaurant() throws Exception {
        owner = restaurantOwner("local@mail.com");
        token = loginAndGetToken("local@mail.com");
        send(post(ME), token, validRestaurantJson()).andExpect(status().isCreated());
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String bearer, String body) throws Exception {
        builder = bearer == null ? builder : builder.header("Authorization", "Bearer " + bearer);
        return mvc.perform(body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void setStatus(String status) {
        jdbc.update("update restaurantes set status = ? where owner_id = ?", status, owner.getId());
    }

    private static String day(String name, String opens, String closes) {
        return "{\"day\":\"%s\",\"opensAt\":%s,\"closesAt\":%s}".formatted(name,
                opens == null ? "null" : "\"" + opens + "\"", closes == null ? "null" : "\"" + closes + "\"");
    }

    private static String week(String mondayOpens, String mondayCloses) {
        return "{\"days\":[" + day("MONDAY", mondayOpens, mondayCloses) + "," + day("TUESDAY", "10:00", "18:00") + ","
                + day("WEDNESDAY", null, null) + "," + day("THURSDAY", null, null) + "," + day("FRIDAY", null, null) + ","
                + day("SATURDAY", null, null) + "," + day("SUNDAY", null, null) + "]}";
    }

    @Test
    void aprobadoGuardaSuHorarioSemanalYLoDevuelveEnOrden() throws Exception {
        setStatus("APPROVED");
        send(put(ME + "/schedule"), token, week("09:00", "21:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.length()").value(7))
                .andExpect(jsonPath("$.schedule[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.schedule[0].opensAt").value("09:00"))
                .andExpect(jsonPath("$.schedule[0].closesAt").value("21:00"))
                .andExpect(jsonPath("$.schedule[2].opensAt").doesNotExist());
        send(get(ME), token, null).andExpect(jsonPath("$.schedule[1].closesAt").value("18:00"));

        // Reemplaza el horario completo
        send(put(ME + "/schedule"), token, week("08:00", "12:00")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from horarios_restaurante", Integer.class)).isEqualTo(7);
        send(get(ME), token, null).andExpect(jsonPath("$.schedule[0].opensAt").value("08:00"));
    }

    @Test
    void horarioInvalidoDa400YConservaElAnterior() throws Exception {
        setStatus("APPROVED");
        send(put(ME + "/schedule"), token, week("09:00", "21:00")).andExpect(status().isOk());

        send(put(ME + "/schedule"), token, week("21:00", "09:00")).andExpect(status().isBadRequest());
        send(put(ME + "/schedule"), token, week("09:00", "09:00")).andExpect(status().isBadRequest());
        send(put(ME + "/schedule"), token, week("09:00", null)).andExpect(status().isBadRequest());
        send(put(ME + "/schedule"), token, "{\"days\":[" + day("MONDAY", null, null) + "]}").andExpect(status().isBadRequest());
        send(put(ME + "/schedule"), token, "{}").andExpect(status().isBadRequest());

        send(get(ME), token, null).andExpect(jsonPath("$.schedule[0].opensAt").value("09:00"));
    }

    @Test
    void recienAprobadoEmpiezaNoDisponibleYPuedeActivarYPausar() throws Exception {
        setStatus("APPROVED");
        send(get(ME), token, null).andExpect(jsonPath("$.available").value(false));

        send(put(ME + "/availability"), token, "{\"available\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
        send(put(ME + "/availability"), token, "{\"available\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.acceptingOrders").value(false));
        send(put(ME + "/availability"), token, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void pendienteYRechazadoDan403EnHorarioYDisponibilidad() throws Exception {
        for (String state : new String[] {"PENDING", "REJECTED"}) {
            setStatus(state);
            send(put(ME + "/schedule"), token, week("09:00", "21:00")).andExpect(status().isForbidden());
            send(put(ME + "/availability"), token, "{\"available\":true}").andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select count(*) from horarios_restaurante", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select available from restaurantes", Boolean.class)).isFalse();
    }

    @Test
    void editarDatosDeUnAprobadoConservaLaAprobacion() throws Exception {
        setStatus("APPROVED");
        send(put(ME), token, validRestaurantJson().replace("Ayacucho", "Huamanga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("Huamanga"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void laDisponibilidadNoSeAceptaDesdeLaSolicitud() throws Exception {
        // `available` no es un campo de la solicitud: el cliente no puede habilitarse por esa vía
        send(put(ME), token, validRestaurantJson().replaceFirst("\\{", "{\"available\":true,"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select available from restaurantes", Boolean.class)).isFalse();
    }

    @Test
    void otrosRolesYSinTokenSonRechazados() throws Exception {
        setStatus("APPROVED");
        send(put(ME + "/schedule"), null, week("09:00", "21:00")).andExpect(status().isUnauthorized());
        send(put(ME + "/availability"), null, "{\"available\":true}").andExpect(status().isUnauthorized());

        registrationService.registerPublic("Cliente", "cliente@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER);
        String customer = loginAndGetToken("cliente@mail.com");
        send(put(ME + "/schedule"), customer, week("09:00", "21:00")).andExpect(status().isForbidden());
        send(put(ME + "/availability"), customer, "{\"available\":true}").andExpect(status().isForbidden());
    }

    @Test
    void cuentaInhabilitadaDa403YLaReglaDeHabilitacionLaExcluye() throws Exception {
        setStatus("APPROVED");
        send(put(ME + "/availability"), token, "{\"available\":true}").andExpect(status().isOk());
        disable(owner.getId());

        send(put(ME + "/availability"), token, "{\"available\":false}").andExpect(status().isForbidden());
        // La habilitación para pedidos (OrderEligibility) exige además cuenta habilitada: ver OrderEligibilityTest.
        assertThat(jdbc.queryForObject("select available from restaurantes", Boolean.class)).isTrue();
    }

    @Test
    void laHabilitacionSeCalculaYUnTitularInhabilitadoDejaDeEstarHabilitado() throws Exception {
        setStatus("APPROVED");
        String allDays = "{\"days\":[" + String.join(",", java.util.stream.Stream.of("MONDAY", "TUESDAY", "WEDNESDAY",
                "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY").map(d -> day(d, "00:00", "23:59")).toList()) + "]}";
        send(put(ME + "/schedule"), token, allDays).andExpect(status().isOk());
        send(put(ME + "/availability"), token, "{\"available\":true}").andExpect(status().isOk());
        send(get(ME), token, null).andExpect(jsonPath("$.acceptingOrders").value(true));

        admin("admin@mikuna.pe");
        String adminToken = loginAndGetToken("admin@mikuna.pe");
        send(get("/api/admin/restaurants?status=APPROVED"), adminToken, null)
                .andExpect(jsonPath("$[0].acceptingOrders").value(true));

        disable(owner.getId());
        send(get("/api/admin/restaurants?status=APPROVED"), adminToken, null)
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].acceptingOrders").value(false));
    }
}
