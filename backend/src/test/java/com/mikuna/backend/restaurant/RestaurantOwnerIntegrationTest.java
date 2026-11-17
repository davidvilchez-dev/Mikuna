package com.mikuna.backend.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** RF-05 y RF-04: solicitud del restaurante propio, sus errores y los rechazos de acceso. */
class RestaurantOwnerIntegrationTest extends IntegrationTestSupport {

    private static final String ME = "/api/restaurant/me";

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String token, String body) throws Exception {
        builder = token == null ? builder : builder.header("Authorization", "Bearer " + token);
        return mvc.perform(body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String ownerToken(String email) throws Exception {
        restaurantOwner(email);
        return loginAndGetToken(email);
    }

    @Test
    void enviaLaSolicitudYQuedaPendienteYPersistida() throws Exception {
        String token = ownerToken("local@mail.com");

        send(post(ME), token, validRestaurantJson())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.acceptingOrders").value(false))
                .andExpect(jsonPath("$.name").value("La Picantería"))
                .andExpect(content().string(not(containsString("ownerId"))))
                .andExpect(content().string(not(containsString("resolvedBy"))));

        send(get(ME), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(jdbc.queryForObject("select count(*) from restaurantes", Integer.class)).isEqualTo(1);
    }

    @Test
    void unaSegundaSolicitudDeLaMismaCuentaDa409() throws Exception {
        String token = ownerToken("local@mail.com");
        send(post(ME), token, validRestaurantJson()).andExpect(status().isCreated());
        send(post(ME), token, validRestaurantJson()).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from restaurantes", Integer.class)).isEqualTo(1);
    }

    @Test
    void cadaCampoInvalidoDa400ConSuError() throws Exception {
        String token = ownerToken("local@mail.com");
        String[][] cases = {
                {"name", "{\"name\":\"\",\"category\":\"Criolla\",\"address\":\"Jr. Lima 123\",\"zone\":\"Ayacucho\",\"phone\":\"966123456\"}"},
                {"category", "{\"name\":\"Local\",\"category\":\"C\",\"address\":\"Jr. Lima 123\",\"zone\":\"Ayacucho\",\"phone\":\"966123456\"}"},
                {"address", "{\"name\":\"Local\",\"category\":\"Criolla\",\"address\":\"Jr\",\"zone\":\"Ayacucho\",\"phone\":\"966123456\"}"},
                {"zone", "{\"name\":\"Local\",\"category\":\"Criolla\",\"address\":\"Jr. Lima 123\",\"zone\":\"\",\"phone\":\"966123456\"}"},
                {"phone", "{\"name\":\"Local\",\"category\":\"Criolla\",\"address\":\"Jr. Lima 123\",\"zone\":\"Ayacucho\",\"phone\":\"123\"}"},
                {"description", "{\"name\":\"Local\",\"category\":\"Criolla\",\"description\":\"" + "x".repeat(301)
                        + "\",\"address\":\"Jr. Lima 123\",\"zone\":\"Ayacucho\",\"phone\":\"966123456\"}"},
        };
        for (String[] c : cases) {
            send(post(ME), token, c[1]).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + c[0]).exists());
        }
        assertThat(jdbc.queryForObject("select count(*) from restaurantes", Integer.class)).isZero();
    }

    @Test
    void rechazaEstadoTitularOAprobacionEnviadosPorElCliente() throws Exception {
        String token = ownerToken("local@mail.com");
        for (String extra : new String[] {"\"status\":\"APPROVED\"", "\"ownerId\":\"" + java.util.UUID.randomUUID() + "\"",
                "\"approved\":true", "\"available\":true"}) {
            String body = validRestaurantJson().replaceFirst("\\{", "{" + extra + ",");
            send(post(ME), token, body).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from restaurantes", Integer.class)).isZero();
    }

    @Test
    void editarDatosConservaElEstado() throws Exception {
        String token = ownerToken("local@mail.com");
        send(post(ME), token, validRestaurantJson()).andExpect(status().isCreated());

        send(put(ME), token, validRestaurantJson().replace("La Picantería", "La Nueva Picantería"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("La Nueva Picantería"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        send(put(ME), token, "{\"name\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void sinSolicitudConsultarYEditarDan404() throws Exception {
        String token = ownerToken("local@mail.com");
        send(get(ME), token, null).andExpect(status().isNotFound());
        send(put(ME), token, validRestaurantJson()).andExpect(status().isNotFound());
        send(post(ME + "/resubmit"), token, null).andExpect(status().isNotFound());
    }

    @Test
    void reenviarSoloDesdeRechazadoYVuelveAPendienteSinMotivo() throws Exception {
        Account owner = restaurantOwner("local@mail.com");
        String token = loginAndGetToken("local@mail.com");
        send(post(ME), token, validRestaurantJson()).andExpect(status().isCreated());

        send(post(ME + "/resubmit"), token, null).andExpect(status().isForbidden());

        jdbc.update("update restaurantes set status = 'REJECTED', rejection_reason = 'Faltan datos' where owner_id = ?", owner.getId());
        send(get(ME), token, null).andExpect(jsonPath("$.rejectionReason").value("Faltan datos"));
        send(post(ME + "/resubmit"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void sinTokenOConTokenManipuladoDa401() throws Exception {
        send(get(ME), null, null).andExpect(status().isUnauthorized());
        send(post(ME), null, validRestaurantJson()).andExpect(status().isUnauthorized());
        String token = ownerToken("local@mail.com");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        send(get(ME), tampered, null).andExpect(status().isUnauthorized());
    }

    @Test
    void otrosRolesDan403() throws Exception {
        registrationService.registerPublic("Cliente", "cliente@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER);
        registrationService.registerPublic("Repartidor", "rep@mail.com", PASSWORD, PASSWORD, Role.COURIER);
        admin("admin@mikuna.pe");
        for (String email : new String[] {"cliente@mail.com", "rep@mail.com", "admin@mikuna.pe"}) {
            String token = loginAndGetToken(email);
            send(post(ME), token, validRestaurantJson()).andExpect(status().isForbidden());
            send(get(ME), token, null).andExpect(status().isForbidden());
            send(put(ME + "/schedule"), token, "{}").andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select count(*) from restaurantes", Integer.class)).isZero();
    }

    @Test
    void cuentaInhabilitadaDa403() throws Exception {
        Account owner = restaurantOwner("local@mail.com");
        String token = loginAndGetToken("local@mail.com");
        disable(owner.getId());
        send(post(ME), token, validRestaurantJson()).andExpect(status().isForbidden());
        send(get(ME), token, null).andExpect(status().isForbidden());
    }

    @Test
    void cadaTitularSoloVeSuPropioRestaurante() throws Exception {
        String ana = ownerToken("ana@mail.com");
        String beto = ownerToken("beto@mail.com");
        send(post(ME), ana, validRestaurantJson()).andExpect(status().isCreated());
        send(post(ME), beto, validRestaurantJson().replace("La Picantería", "El Fogón")).andExpect(status().isCreated());

        send(get(ME), ana, null).andExpect(jsonPath("$.name").value("La Picantería"));
        send(get(ME), beto, null).andExpect(jsonPath("$.name").value("El Fogón"));
    }

    @Test
    void solicitudesSimultaneasDeLaMismaCuentaCreanUnaSolaFila() throws Exception {
        String token = ownerToken("local@mail.com");
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> call = () -> {
                start.await();
                return send(post(ME), token, validRestaurantJson()).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            if (code == 201) created++;
            if (code == 409) conflicts++;
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(threads - 1);
        assertThat(jdbc.queryForObject("select count(*) from restaurantes", Integer.class)).isEqualTo(1);
    }
}
