package com.mikuna.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestProducts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Spec 015, historia 2 (FR-004, SC-004): cada cambio hecho por su endpoint real se ve en la siguiente consulta
 * pública aunque la anterior haya quedado guardada en la caché.
 */
class PublicCatalogInvalidationIntegrationTest extends IntegrationTestSupport {

    private static final String PUBLIC = "/api/public/restaurants";
    private static final String ME = "/api/restaurant/me";

    private Account owner;
    private UUID restaurantId;
    private String ownerToken;
    private String adminToken;

    @BeforeEach
    void prepare() throws Exception {
        owner = restaurantOwner("cache@mail.com");
        ownerToken = loginAndGetToken("cache@mail.com");
        restaurantId = TestProducts.approvedRestaurant(jdbc, owner.getId(), "La Picantería", "Criolla", "Ayacucho");
        admin("admin-cache@mail.com");
        adminToken = loginAndGetToken("admin-cache@mail.com");
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String token, String json) throws Exception {
        builder.header("Authorization", "Bearer " + token);
        return mvc.perform(json == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String catalogBody() throws Exception {
        return mvc.perform(get(PUBLIC + "/" + restaurantId + "/catalog")).andReturn().getResponse()
                .getContentAsString();
    }

    private List<String> productNames() throws Exception {
        return JsonPath.read(catalogBody(), "$.products.items[*].name");
    }

    private String listBody() throws Exception {
        return mvc.perform(get(PUBLIC)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long productVersion(UUID productId) {
        return jdbc.queryForObject("select version from productos where id = ?", Long.class, productId);
    }

    private MockMultipartFile productPart(String name, String price, Long version) {
        String json = "{\"name\":\"%s\",\"category\":\"Fondos\",\"description\":\"Casero\",\"price\":%s%s}"
                .formatted(name, price, version == null ? "" : ",\"version\":" + version);
        return new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
    }

    @Test
    void crearEditarCambiarDisponibilidadYRetirarUnPlatoSeVenEnLaSiguienteConsulta() throws Exception {
        assertThat(productNames()).isEmpty(); // queda guardada la carta vacía
        assertThat(JsonPath.<String>read(listBody(), "$.items[0].coverUrl")).isNull();

        String created = mvc.perform(multipart(ME + "/products").file(productPart("Lomo saltado", "22.50", null))
                        .file(new MockMultipartFile("image", "foto.jpg", "image/jpeg", TestProducts.JPEG))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID product = UUID.fromString(JsonPath.read(created, "$.id"));
        assertThat(productNames()).containsExactly("Lomo saltado");
        // La portada de respaldo depende del primer plato: la lista también se renovó.
        assertThat(JsonPath.<String>read(listBody(), "$.items[0].coverUrl")).isEqualTo("https://img.test/p.jpg");

        mvc.perform(multipart(HttpMethod.PUT, ME + "/products/" + product)
                        .file(productPart("Lomo saltado", "30.00", productVersion(product)))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        assertThat(JsonPath.<Double>read(catalogBody(), "$.products.items[0].price")).isEqualTo(30.0);

        send(put(ME + "/products/" + product + "/availability"), ownerToken,
                "{\"available\":false,\"version\":%d}".formatted(productVersion(product))).andExpect(status().isOk());
        assertThat(productNames()).isEmpty();

        send(put(ME + "/products/" + product + "/availability"), ownerToken,
                "{\"available\":true,\"version\":%d}".formatted(productVersion(product))).andExpect(status().isOk());
        assertThat(productNames()).containsExactly("Lomo saltado");

        send(delete(ME + "/products/" + product + "?version=" + productVersion(product)), ownerToken, null)
                .andExpect(status().isNoContent());
        assertThat(productNames()).isEmpty();
    }

    @Test
    void losDatosElHorarioLaDisponibilidadYLasImagenesDelRestauranteSeVenEnLaSiguienteConsulta() throws Exception {
        mvc.perform(get(PUBLIC + "/filters")).andExpect(jsonPath("$.categories[0]").value("Criolla"));
        assertThat(JsonPath.<String>read(listBody(), "$.items[0].attention")).isEqualTo("OPEN");

        send(put(ME), ownerToken, """
                {"name":"Picantería Renovada","category":"Marina","description":"Nueva carta",
                 "address":"Jr. Lima 123","zone":"Huamanga","phone":"+51 966 123 456"}""").andExpect(status().isOk());
        String list = listBody();
        assertThat(JsonPath.<String>read(list, "$.items[0].name")).isEqualTo("Picantería Renovada");
        assertThat(JsonPath.<String>read(list, "$.items[0].zone")).isEqualTo("Huamanga");
        mvc.perform(get(PUBLIC + "/filters")).andExpect(jsonPath("$.categories[0]").value("Marina"));

        send(put(ME + "/availability"), ownerToken, "{\"available\":false}").andExpect(status().isOk());
        assertThat(JsonPath.<String>read(listBody(), "$.items[0].attention")).isEqualTo("PAUSED");

        send(put(ME + "/availability"), ownerToken, "{\"available\":true}").andExpect(status().isOk());
        String closedWeek = "{\"days\":[" + String.join(",", java.util.Arrays.stream(java.time.DayOfWeek.values())
                .map(day -> "{\"day\":\"%s\",\"opensAt\":null,\"closesAt\":null}".formatted(day)).toList()) + "]}";
        send(put(ME + "/schedule"), ownerToken, closedWeek).andExpect(status().isOk());
        assertThat(JsonPath.<String>read(listBody(), "$.items[0].attention")).isEqualTo("OUTSIDE_HOURS");

        mvc.perform(multipart(HttpMethod.PUT, ME + "/images")
                        .file(new MockMultipartFile("cover", "portada.jpg", "image/jpeg", TestProducts.JPEG))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        assertThat(JsonPath.<String>read(listBody(), "$.items[0].coverUrl")).isEqualTo("https://img.test/p.jpg");
    }

    @Test
    void aprobarUnRestauranteLoHaceAparecerYRechazarloNoLoMuestra() throws Exception {
        assertThat(JsonPath.<Integer>read(listBody(), "$.totalItems")).isEqualTo(1);

        Account second = restaurantOwner("nuevo@mail.com");
        String secondToken = loginAndGetToken("nuevo@mail.com");
        String body = send(post(ME), secondToken, validRestaurantJson()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID pending = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(JsonPath.<Integer>read(listBody(), "$.totalItems")).isEqualTo(1);

        send(post("/api/admin/restaurants/" + pending + "/approve"), adminToken, null).andExpect(status().isOk());
        assertThat(JsonPath.<Integer>read(listBody(), "$.totalItems")).isEqualTo(2);

        Account third = restaurantOwner("rechazado@mail.com");
        String thirdBody = send(post(ME), loginAndGetToken("rechazado@mail.com"), validRestaurantJson())
                .andReturn().getResponse().getContentAsString();
        UUID rejected = UUID.fromString(JsonPath.read(thirdBody, "$.id"));
        send(post("/api/admin/restaurants/" + rejected + "/reject"), adminToken, "{\"reason\":\"Datos incompletos\"}")
                .andExpect(status().isOk());
        assertThat(JsonPath.<Integer>read(listBody(), "$.totalItems")).isEqualTo(2);
        assertThat(second.getId()).isNotEqualTo(third.getId());
    }

    @Test
    void suspenderODeshabilitarAlTitularLoOcultaYReactivarloLoMuestra() throws Exception {
        TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);
        assertThat(productNames()).containsExactly("Lomo");
        assertThat(JsonPath.<Integer>read(listBody(), "$.totalItems")).isEqualTo(1);

        send(post("/api/admin/restaurants/" + restaurantId + "/deactivate"), adminToken, null)
                .andExpect(status().isOk());
        assertThat(JsonPath.<Integer>read(listBody(), "$.totalItems")).isZero();
        mvc.perform(get(PUBLIC + "/" + restaurantId + "/catalog")).andExpect(status().isNotFound());

        send(post("/api/admin/restaurants/" + restaurantId + "/activate"), adminToken, null)
                .andExpect(status().isOk());
        assertThat(JsonPath.<Integer>read(listBody(), "$.totalItems")).isEqualTo(1);

        send(post("/api/admin/users/" + owner.getId() + "/disable"), adminToken, null).andExpect(status().isOk());
        assertThat(JsonPath.<Integer>read(listBody(), "$.totalItems")).isZero();
        mvc.perform(get(PUBLIC + "/" + restaurantId + "/catalog")).andExpect(status().isNotFound());

        send(post("/api/admin/users/" + owner.getId() + "/enable"), adminToken, null).andExpect(status().isOk());
        assertThat(productNames()).containsExactly("Lomo");
    }
}
