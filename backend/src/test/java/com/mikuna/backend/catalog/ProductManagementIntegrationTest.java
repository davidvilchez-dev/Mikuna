package com.mikuna.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.common.exception.ImageUnavailableException;
import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestProducts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** RF-08 y RF-04: gestión del catálogo propio; autorización, pertenencia, duplicados y versiones. */
class ProductManagementIntegrationTest extends IntegrationTestSupport {

    private static final String PRODUCTS = "/api/restaurant/me/products";

    private Account owner;
    private UUID restaurantId;
    private String token;

    @BeforeEach
    void prepareRestaurant() throws Exception {
        owner = restaurantOwner("local@mail.com");
        token = loginAndGetToken("local@mail.com");
        restaurantId = TestProducts.approvedRestaurant(jdbc, owner.getId(), "La Picantería", "Criolla", "Ayacucho");
    }

    private static String json(String name, String category, String price, Long version) {
        return "{\"name\":\"%s\",\"category\":\"%s\",\"description\":\"Casero\",\"price\":%s%s}".formatted(name,
                category, price, version == null ? "" : ",\"version\":" + version);
    }

    private ResultActions create(String bearer, String productJson, byte[] imageBytes, String imageType) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart(PRODUCTS)
                .file(new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE, productJson.getBytes()));
        if (imageBytes != null) {
            builder.file(new MockMultipartFile("image", "foto.jpg", imageType, imageBytes));
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(builder);
    }

    private ResultActions create(String bearer, String productJson) throws Exception {
        return create(bearer, productJson, TestProducts.JPEG, "image/jpeg");
    }

    private ResultActions edit(String bearer, UUID id, String productJson, byte[] imageBytes) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart(HttpMethod.PUT, PRODUCTS + "/" + id)
                .file(new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE, productJson.getBytes()));
        if (imageBytes != null) {
            builder.file(new MockMultipartFile("image", "nueva.png", "image/png", imageBytes));
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(builder);
    }

    private ResultActions availability(String bearer, UUID id, boolean available, long version) throws Exception {
        var builder = put(PRODUCTS + "/" + id + "/availability").contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":%s,\"version\":%d}".formatted(available, version));
        return mvc.perform(bearer == null ? builder : builder.header("Authorization", "Bearer " + bearer));
    }

    private ResultActions withdraw(String bearer, UUID id, Long version) throws Exception {
        var builder = delete(PRODUCTS + "/" + id + (version == null ? "" : "?version=" + version));
        return mvc.perform(bearer == null ? builder : builder.header("Authorization", "Bearer " + bearer));
    }

    private ResultActions list(String bearer, String query) throws Exception {
        var builder = get(PRODUCTS + query);
        return mvc.perform(bearer == null ? builder : builder.header("Authorization", "Bearer " + bearer));
    }

    private UUID createAndGetId(String name, String category) throws Exception {
        String body = create(token, json(name, category, "18.50", null)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static <T> List<T> race(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void creaUnProductoDisponibleConImagenYQuedaGuardadoParaSuRestaurante() throws Exception {
        create(token, json("Ají de gallina", "Fondos", "18.50", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ají de gallina"))
                .andExpect(jsonPath("$.price").value(18.5))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.imageUrl").value("https://img.test/p.jpg"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.restaurantId").doesNotExist());

        assertThat(jdbc.queryForObject(
                "select count(*) from productos where restaurant_id = ? and active = true and available = true",
                Integer.class, restaurantId)).isEqualTo(1);
    }

    @Test
    void elDuplicadoDeNombreYCategoriaDa409PeroOtraCategoriaOUnoRetiradoSePermiten() throws Exception {
        UUID first = createAndGetId("Lomo saltado", "Fondos");

        create(token, json("LOMO SALTADO", "fondos", "10", null)).andExpect(status().isConflict());
        create(token, json("Lómo Saltado", "Fondos", "10", null)).andExpect(status().isConflict());
        verify(imageStorage, org.mockito.Mockito.atLeastOnce()).delete("mikuna/products/p");
        create(token, json("Lomo saltado", "Entradas", "10", null)).andExpect(status().isCreated());

        withdraw(token, first, 0L).andExpect(status().isNoContent());
        create(token, json("Lomo saltado", "Fondos", "10", null)).andExpect(status().isCreated());
    }

    @Test
    void dosAltasIdenticasSimultaneasCreanUnaSolaFila() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> create(token, json("Cebiche", "Entradas", "25", null)).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = race(tasks);

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(code -> code == 201).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from productos where active = true", Integer.class)).isEqualTo(1);
    }

    @Test
    void rechazaDatosEImagenInvalidos() throws Exception {
        create(token, json("X", "Fondos", "10", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        create(token, json("Lomo", "F", "10", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").exists());
        create(token, json("Lomo", "Fondos", "0", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").exists());
        create(token, json("Lomo", "Fondos", "1.234", null)).andExpect(status().isBadRequest());
        create(token, json("Lomo", "Fondos", "10000", null)).andExpect(status().isBadRequest());
        create(token, json("Lomo", "Fondos", "10", 0L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());

        create(token, json("Lomo", "Fondos", "10", null), null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.image").value("La imagen es obligatoria"));
        create(token, json("Lomo", "Fondos", "10", null), "%PDF-1.7".getBytes(), "image/jpeg")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.image").exists());
        create(token, json("Lomo", "Fondos", "10", null), TestProducts.JPEG, "application/pdf")
                .andExpect(status().isBadRequest());
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(TestProducts.JPEG, 0, tooBig, 0, TestProducts.JPEG.length);
        create(token, json("Lomo", "Fondos", "10", null), tooBig, "image/jpeg").andExpect(status().isPayloadTooLarge());

        assertThat(jdbc.queryForObject("select count(*) from productos", Integer.class)).isZero();
        verify(imageStorage, never()).upload(any(), any());
    }

    @Test
    void rechazaCamposQueElClienteNoDebeEnviar() throws Exception {
        for (String extra : new String[] {"\"restaurantId\":\"%s\"".formatted(UUID.randomUUID()), "\"active\":false",
                "\"available\":false", "\"status\":\"X\""}) {
            create(token, "{\"name\":\"Lomo\",\"category\":\"Fondos\",\"price\":10," + extra + "}")
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from productos", Integer.class)).isZero();
    }

    @Test
    void siFallaElServicioDeImagenesDa503SinGuardarNada() throws Exception {
        when(imageStorage.upload(any(), any())).thenThrow(new ImageUnavailableException());
        create(token, json("Lomo", "Fondos", "10", null)).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from productos", Integer.class)).isZero();
    }

    @Test
    void listaSoloLosPropiosNoRetiradosConPaginacion() throws Exception {
        UUID retired = createAndGetId("Retirado", "Fondos");
        createAndGetId("Cebiche", "Entradas");
        createAndGetId("Lomo", "Fondos");
        withdraw(token, retired, 0L).andExpect(status().isNoContent());
        TestProducts.product(jdbc, UUID.randomUUID(), "Ajeno", "Fondos", "5", true, true);
        TestProducts.product(jdbc, restaurantId, "No disponible", "Postres", "5", false, true);

        list(token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].category").value("Entradas"));
        list(token, "?page=1&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        list(token, "?size=0").andExpect(status().isBadRequest());
        list(token, "?size=51").andExpect(status().isBadRequest());
        list(token, "?page=-1").andExpect(status().isBadRequest());
    }

    @Test
    void consultaYEditaConNuevaVersionYConservaLaImagenSiNoSeEnvia() throws Exception {
        UUID id = createAndGetId("Lomo", "Fondos");
        mvc.perform(get(PRODUCTS + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Lomo"));

        edit(token, id, json("Lomo especial", "Fondos", "21.00", 0L), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lomo especial"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.imageUrl").value("https://img.test/p.jpg"));
        verify(imageStorage, org.mockito.Mockito.times(1)).upload(any(), any());

        when(imageStorage.upload(any(), any()))
                .thenReturn(new com.mikuna.backend.catalog.service.CloudinaryImageStorage.StoredImage(
                        "https://img.test/nueva.png", "mikuna/products/nueva"));
        edit(token, id, json("Lomo especial", "Fondos", "22.00", 1L), TestProducts.PNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://img.test/nueva.png"));
        verify(imageStorage).delete("mikuna/products/p");
    }

    @Test
    void editarConVersionDesfasadaDa409YNoCambiaNada() throws Exception {
        UUID id = createAndGetId("Lomo", "Fondos");
        edit(token, id, json("Lomo 2", "Fondos", "21", 0L), null).andExpect(status().isOk());

        edit(token, id, json("Lomo 3", "Fondos", "30", 0L), null).andExpect(status().isConflict());
        edit(token, id, json("Lomo 3", "Fondos", "30", null), null).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select name from productos where id = ?", String.class, id)).isEqualTo("Lomo 2");
    }

    @Test
    void dosEdicionesSimultaneasConLaMismaVersionSoloUnaProspera() throws Exception {
        UUID id = createAndGetId("Lomo", "Fondos");
        List<Callable<Integer>> tasks = List.of(
                () -> edit(token, id, json("Lomo A", "Fondos", "21", 0L), null).andReturn().getResponse().getStatus(),
                () -> edit(token, id, json("Lomo B", "Fondos", "22", 0L), null).andReturn().getResponse().getStatus());

        List<Integer> statuses = race(tasks);

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select version from productos where id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void disponibilidadAlternaYExigeLaVersion() throws Exception {
        UUID id = createAndGetId("Lomo", "Fondos");
        availability(token, id, false, 0).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.version").value(1));
        availability(token, id, true, 1).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        availability(token, id, false, 0).andExpect(status().isConflict());
        mvc.perform(put(PRODUCTS + "/" + id + "/availability").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retirarEsLogicoYDefinitivoYExigeLaVersion() throws Exception {
        UUID id = createAndGetId("Lomo", "Fondos");
        withdraw(token, id, null).andExpect(status().isBadRequest());
        withdraw(token, id, 5L).andExpect(status().isConflict());

        withdraw(token, id, 0L).andExpect(status().isNoContent());
        mvc.perform(get(PRODUCTS + "/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        edit(token, id, json("Lomo", "Fondos", "10", 1L), null).andExpect(status().isNotFound());
        availability(token, id, true, 1).andExpect(status().isNotFound());
        withdraw(token, id, 1L).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from productos where id = ? and active is null and withdrawn_at is not null",
                Integer.class, id)).isEqualTo(1);
        verify(imageStorage, never()).delete(any());
    }

    @Test
    void unProductoDeOtroRestauranteEsIndistinguibleDeUnoInexistente() throws Exception {
        Account other = restaurantOwner("otro@mail.com");
        UUID otherRestaurant = TestProducts.approvedRestaurant(jdbc, other.getId(), "Otro", "Pizza", "Lima");
        UUID foreign = TestProducts.product(jdbc, otherRestaurant, "Ajeno", "Fondos", "9", true, true);

        mvc.perform(get(PRODUCTS + "/" + foreign).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        edit(token, foreign, json("Hackeado", "Fondos", "1", 0L), null).andExpect(status().isNotFound());
        availability(token, foreign, false, 0).andExpect(status().isNotFound());
        withdraw(token, foreign, 0L).andExpect(status().isNotFound());
        mvc.perform(get(PRODUCTS + "/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select name from productos where id = ?", String.class, foreign)).isEqualTo("Ajeno");
        assertThat(jdbc.queryForObject("select active from productos where id = ?", Boolean.class, foreign)).isTrue();
    }

    @Test
    void sinRestauranteDa404YPendienteORechazadoDa403() throws Exception {
        restaurantOwner("nuevo@mail.com");
        String nuevo = loginAndGetToken("nuevo@mail.com");
        create(nuevo, json("Lomo", "Fondos", "10", null)).andExpect(status().isNotFound());

        for (String state : new String[] {"PENDING", "REJECTED"}) {
            jdbc.update("update restaurantes set status = ? where id = ?", state, restaurantId);
            create(token, json("Lomo", "Fondos", "10", null)).andExpect(status().isForbidden());
            list(token, "").andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select count(*) from productos", Integer.class)).isZero();
    }

    @Test
    void controlDeAccesoPorTokenRolYEstadoDeCuenta() throws Exception {
        create(null, json("Lomo", "Fondos", "10", null)).andExpect(status().isUnauthorized());
        create("token.manipulado.x", json("Lomo", "Fondos", "10", null)).andExpect(status().isUnauthorized());
        list(null, "").andExpect(status().isUnauthorized());

        registrationService.registerPublic("Cliente", "cliente@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER);
        registrationService.registerPublic("Repartidor", "rep@mail.com", PASSWORD, PASSWORD, Role.COURIER);
        admin("admin@mail.com");
        for (String email : new String[] {"cliente@mail.com", "rep@mail.com", "admin@mail.com"}) {
            String other = loginAndGetToken(email);
            create(other, json("Lomo", "Fondos", "10", null)).andExpect(status().isForbidden());
            list(other, "").andExpect(status().isForbidden());
        }

        disable(owner.getId());
        create(token, json("Lomo", "Fondos", "10", null)).andExpect(status().isForbidden());
        list(token, "").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from productos", Integer.class)).isZero();
    }
}
