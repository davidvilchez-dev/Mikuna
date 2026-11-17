package com.mikuna.backend.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** RF-06 y RF-04: revisión administrativa, resolución única y rechazos de acceso. */
class RestaurantAdminIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN_API = "/api/admin/restaurants";

    private Account adminAccount;
    private String adminToken;

    @BeforeEach
    void prepareAdmin() throws Exception {
        adminAccount = admin("admin@mikuna.pe");
        adminToken = loginAndGetToken("admin@mikuna.pe");
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String token, String body) throws Exception {
        builder = token == null ? builder : builder.header("Authorization", "Bearer " + token);
        return mvc.perform(body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Crea una cuenta de restaurante con solicitud pendiente enviada en el instante indicado. */
    private UUID pendingRestaurant(String email, String name, String submittedAt) throws Exception {
        restaurantOwner(email);
        send(post("/api/restaurant/me"), loginAndGetToken(email), validRestaurantJson().replace("La Picantería", name))
                .andExpect(status().isCreated());
        jdbc.update("update restaurantes set submitted_at = ?::timestamptz where name = ?", submittedAt, name);
        return jdbc.queryForObject("select id from restaurantes where name = ?", UUID.class, name);
    }

    @Test
    void listaSoloPendientesDeLaMasAntiguaALaMasReciente() throws Exception {
        pendingRestaurant("b@mail.com", "Segundo", "2026-11-16T12:00:00Z");
        pendingRestaurant("a@mail.com", "Primero", "2026-11-16T10:00:00Z");
        UUID approved = pendingRestaurant("c@mail.com", "Aprobado", "2026-11-16T09:00:00Z");
        jdbc.update("update restaurantes set status = 'APPROVED' where id = ?", approved);

        send(get(ADMIN_API), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Primero"))
                .andExpect(jsonPath("$[1].name").value("Segundo"))
                .andExpect(jsonPath("$[0].ownerEmail").value("a@mail.com"))
                .andExpect(jsonPath("$[0].ownerName").value("Dueño"))
                .andExpect(jsonPath("$[0].acceptingOrders").value(false));

        send(get(ADMIN_API + "?status=APPROVED"), adminToken, null).andExpect(jsonPath("$.length()").value(1));
        send(get(ADMIN_API + "?status=TODOS"), adminToken, null).andExpect(status().isBadRequest());
    }

    @Test
    void sinSolicitudesLaListaVieneVacia() throws Exception {
        send(get(ADMIN_API), adminToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aprobarRegistraAdministradorYFechaYElTitularLoVe() throws Exception {
        UUID id = pendingRestaurant("local@mail.com", "La Picantería", "2026-11-16T10:00:00Z");

        send(post(ADMIN_API + "/" + id + "/approve"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.available").value(false));

        assertThat(jdbc.queryForObject("select resolved_by from restaurantes where id = ?", UUID.class, id))
                .isEqualTo(adminAccount.getId());
        assertThat(jdbc.queryForObject("select resolved_at from restaurantes where id = ?", Object.class, id)).isNotNull();
        send(get("/api/restaurant/me"), loginAndGetToken("local@mail.com"), null)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.acceptingOrders").value(false));
    }

    @Test
    void rechazarExigeMotivoYElTitularLoVe() throws Exception {
        UUID id = pendingRestaurant("local@mail.com", "La Picantería", "2026-11-16T10:00:00Z");

        send(post(ADMIN_API + "/" + id + "/reject"), adminToken, "{}").andExpect(status().isBadRequest());
        send(post(ADMIN_API + "/" + id + "/reject"), adminToken, "{\"reason\":\"   \"}").andExpect(status().isBadRequest());
        send(post(ADMIN_API + "/" + id + "/reject"), adminToken, "{\"reason\":\"" + "x".repeat(301) + "\"}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select status from restaurantes where id = ?", String.class, id)).isEqualTo("PENDING");

        send(post(ADMIN_API + "/" + id + "/reject"), adminToken, "{\"reason\":\"Faltan datos de contacto\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        send(get("/api/restaurant/me"), loginAndGetToken("local@mail.com"), null)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Faltan datos de contacto"));
    }

    @Test
    void unaSolicitudYaResueltaNoSePuedeResolverDeNuevo() throws Exception {
        UUID id = pendingRestaurant("local@mail.com", "La Picantería", "2026-11-16T10:00:00Z");
        send(post(ADMIN_API + "/" + id + "/approve"), adminToken, null).andExpect(status().isOk());
        send(post(ADMIN_API + "/" + id + "/approve"), adminToken, null).andExpect(status().isConflict());
        send(post(ADMIN_API + "/" + id + "/reject"), adminToken, "{\"reason\":\"Tarde\"}").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from restaurantes where id = ?", String.class, id)).isEqualTo("APPROVED");
    }

    @Test
    void unRechazadoQueReenviaReapareceEnLaLista() throws Exception {
        UUID id = pendingRestaurant("local@mail.com", "La Picantería", "2026-11-16T10:00:00Z");
        send(post(ADMIN_API + "/" + id + "/reject"), adminToken, "{\"reason\":\"Corrige la dirección\"}").andExpect(status().isOk());
        send(get(ADMIN_API), adminToken, null).andExpect(jsonPath("$.length()").value(0));

        send(post("/api/restaurant/me/resubmit"), loginAndGetToken("local@mail.com"), null).andExpect(status().isOk());
        send(get(ADMIN_API), adminToken, null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void idDesconocidoDa404() throws Exception {
        UUID unknown = UUID.randomUUID();
        send(post(ADMIN_API + "/" + unknown + "/approve"), adminToken, null).andExpect(status().isNotFound());
        send(post(ADMIN_API + "/" + unknown + "/reject"), adminToken, "{\"reason\":\"Motivo\"}").andExpect(status().isNotFound());
    }

    @Test
    void camposDesconocidosEnElCuerpoDan400() throws Exception {
        UUID id = pendingRestaurant("local@mail.com", "La Picantería", "2026-11-16T10:00:00Z");
        send(post(ADMIN_API + "/" + id + "/reject"), adminToken, "{\"reason\":\"Motivo\",\"status\":\"APPROVED\"}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select status from restaurantes where id = ?", String.class, id)).isEqualTo("PENDING");
    }

    @Test
    void sinTokenDa401() throws Exception {
        UUID id = pendingRestaurant("local@mail.com", "La Picantería", "2026-11-16T10:00:00Z");
        send(get(ADMIN_API), null, null).andExpect(status().isUnauthorized());
        send(post(ADMIN_API + "/" + id + "/approve"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void otrosRolesDan403YNoCambianElEstado() throws Exception {
        UUID id = pendingRestaurant("local@mail.com", "La Picantería", "2026-11-16T10:00:00Z");
        registrationService.registerPublic("Cliente", "cliente@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER);
        registrationService.registerPublic("Repartidor", "rep@mail.com", PASSWORD, PASSWORD, Role.COURIER);
        for (String email : new String[] {"cliente@mail.com", "rep@mail.com", "local@mail.com"}) {
            String token = loginAndGetToken(email);
            send(get(ADMIN_API), token, null).andExpect(status().isForbidden());
            send(post(ADMIN_API + "/" + id + "/approve"), token, null).andExpect(status().isForbidden());
            send(post(ADMIN_API + "/" + id + "/reject"), token, "{\"reason\":\"Motivo\"}").andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select status from restaurantes where id = ?", String.class, id)).isEqualTo("PENDING");
    }

    @Test
    void administradorInhabilitadoDa403() throws Exception {
        disable(adminAccount.getId());
        send(get(ADMIN_API), adminToken, null).andExpect(status().isForbidden());
    }

    @Test
    void resolucionesSimultaneasDejanUnSoloGanador() throws Exception {
        UUID id = pendingRestaurant("local@mail.com", "La Picantería", "2026-11-16T10:00:00Z");
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            boolean approve = i % 2 == 0;
            results.add(pool.submit(() -> {
                start.await();
                ResultActions action = approve
                        ? send(post(ADMIN_API + "/" + id + "/approve"), adminToken, null)
                        : send(post(ADMIN_API + "/" + id + "/reject"), adminToken, "{\"reason\":\"Rechazo simultáneo\"}");
                return action.andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        int conflicts = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            if (code == 200) ok++;
            if (code == 409) conflicts++;
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(conflicts).isEqualTo(threads - 1);
        String finalStatus = jdbc.queryForObject("select status from restaurantes where id = ?", String.class, id);
        assertThat(finalStatus).isIn("APPROVED", "REJECTED");
        // El estado final es coherente con la única resolución que prosperó
        String reason = jdbc.queryForObject("select rejection_reason from restaurantes where id = ?", String.class, id);
        assertThat(reason == null).isEqualTo(finalStatus.equals("APPROVED"));
    }
}
