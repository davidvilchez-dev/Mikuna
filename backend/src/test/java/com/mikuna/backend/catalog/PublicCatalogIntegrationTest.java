package com.mikuna.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestProducts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** RF-10: catálogo público; solo productos activos y disponibles de restaurantes visibles. */
class PublicCatalogIntegrationTest extends IntegrationTestSupport {

    private static final String PUBLIC = "/api/public/restaurants";

    private Account owner;
    private UUID restaurantId;

    @BeforeEach
    void prepareRestaurant() {
        owner = restaurantOwner("local@mail.com");
        restaurantId = TestProducts.approvedRestaurant(jdbc, owner.getId(), "La Picantería", "Criolla", "Ayacucho");
    }

    private ResultActions catalog(UUID id, String query) throws Exception {
        return mvc.perform(get(PUBLIC + "/" + id + "/catalog" + query));
    }

    @Test
    void sinSesionSoloMuestraProductosActivosYDisponiblesOrdenadosPorCategoriaYNombre() throws Exception {
        TestProducts.product(jdbc, restaurantId, "Lomo saltado", "Fondos", "22.50", true, true);
        TestProducts.product(jdbc, restaurantId, "Cebiche", "Entradas", "25.00", true, true);
        TestProducts.product(jdbc, restaurantId, "Ají de gallina", "Fondos", "18.00", true, true);
        TestProducts.product(jdbc, restaurantId, "Sin stock", "Fondos", "10.00", false, true);
        TestProducts.product(jdbc, restaurantId, "Retirado", "Fondos", "10.00", true, false);
        UUID other = TestProducts.approvedRestaurant(jdbc, restaurantOwner("otro@mail.com").getId(), "Otro", "Pizza", "Lima");
        TestProducts.product(jdbc, other, "Pizza ajena", "Pizzas", "30.00", true, true);

        String body = catalog(restaurantId, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.name").value("La Picantería"))
                .andExpect(jsonPath("$.restaurant.attention").value("OPEN"))
                .andExpect(jsonPath("$.products.totalItems").value(3))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.products.items[*].name"))
                .containsExactly("Cebiche", "Ají de gallina", "Lomo saltado");
        assertThat(JsonPath.<Double>read(body, "$.products.items[0].price")).isEqualTo(25.0);
        assertThat(JsonPath.<String>read(body, "$.products.items[0].imageUrl")).isEqualTo("https://img.test/p.jpg");
        assertThat(body).doesNotContain("Sin stock").doesNotContain("Retirado").doesNotContain("Pizza ajena");
    }

    @Test
    void laRespuestaNoExponeCamposInternos() throws Exception {
        TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);

        String body = catalog(restaurantId, "").andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$.products.items[0]").keySet())
                .containsExactlyInAnyOrder("id", "name", "description", "category", "price", "imageUrl");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.restaurant").keySet())
                .containsExactlyInAnyOrder("id", "name", "category", "description", "zone", "attention", "logoUrl", "coverUrl");
        assertThat(body).doesNotContain("mikuna/products/p").doesNotContain("Jr. Lima 123").doesNotContain("local@mail.com");
    }

    @Test
    void unProductoQueDejaDeEstarDisponibleORetiradoDesapareceEnLaSiguienteConsulta() throws Exception {
        UUID product = TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);
        catalog(restaurantId, "").andExpect(jsonPath("$.products.totalItems").value(1));

        jdbc.update("update productos set available = false where id = ?", product);
        catalog(restaurantId, "").andExpect(jsonPath("$.products.totalItems").value(0));

        jdbc.update("update productos set available = true where id = ?", product);
        catalog(restaurantId, "").andExpect(jsonPath("$.products.totalItems").value(1));

        jdbc.update("update productos set active = null, withdrawn_at = now() where id = ?", product);
        catalog(restaurantId, "").andExpect(jsonPath("$.products.totalItems").value(0));
    }

    @Test
    void unRestauranteSinProductosDisponiblesDevuelveUnaListaVacia() throws Exception {
        catalog(restaurantId, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.products.items.length()").value(0))
                .andExpect(jsonPath("$.products.totalItems").value(0));
    }

    @Test
    void pendienteRechazadoInhabilitadoEInexistenteDanElMismoNoEncontrado() throws Exception {
        TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);

        String missing = catalog(UUID.randomUUID(), "").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String expectedDetail = JsonPath.read(missing, "$.detail");

        jdbc.update("update restaurantes set status = 'PENDING' where id = ?", restaurantId);
        assertThat(JsonPath.<String>read(catalog(restaurantId, "").andExpect(status().isNotFound()).andReturn()
                .getResponse().getContentAsString(), "$.detail")).isEqualTo(expectedDetail);
        jdbc.update("update restaurantes set status = 'REJECTED' where id = ?", restaurantId);
        catalog(restaurantId, "").andExpect(status().isNotFound());

        jdbc.update("update restaurantes set status = 'APPROVED' where id = ?", restaurantId);
        catalog(restaurantId, "").andExpect(status().isOk());
        disable(owner.getId());
        catalog(restaurantId, "").andExpect(status().isNotFound());
    }

    @Test
    void pausadoOFueraDeHorarioSeMuestraConSuAvisoYSusProductos() throws Exception {
        TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);

        jdbc.update("update restaurantes set available = false where id = ?", restaurantId);
        catalog(restaurantId, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.attention").value("PAUSED"))
                .andExpect(jsonPath("$.products.items[0].name").value("Lomo"));

        jdbc.update("update restaurantes set available = true where id = ?", restaurantId);
        TestProducts.closeAllDays(jdbc, restaurantId);
        catalog(restaurantId, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.attention").value("OUTSIDE_HOURS"))
                .andExpect(jsonPath("$.products.totalItems").value(1));
    }

    @Test
    void paginaElCatalogoYValidaLosParametros() throws Exception {
        for (int i = 0; i < 5; i++) {
            TestProducts.product(jdbc, restaurantId, "Producto " + i, "Fondos", "10.00", true, true);
        }
        catalog(restaurantId, "?size=2").andExpect(jsonPath("$.products.items.length()").value(2))
                .andExpect(jsonPath("$.products.totalPages").value(3));
        catalog(restaurantId, "?size=2&page=2").andExpect(jsonPath("$.products.items.length()").value(1));
        catalog(restaurantId, "?page=-1").andExpect(status().isBadRequest());
        catalog(restaurantId, "?size=51").andExpect(status().isBadRequest());
    }

    @Test
    void noExisteRutaPublicaDeProductoPorIdYUnIdMalFormadoNoDa500() throws Exception {
        UUID product = TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);

        int code = mvc.perform(get(PUBLIC + "/" + restaurantId + "/products/" + product)).andReturn().getResponse().getStatus();
        assertThat(code).isEqualTo(404);
        mvc.perform(get(PUBLIC + "/no-es-un-uuid/catalog")).andExpect(status().isBadRequest());
        // Ninguna otra ruta pública devuelve productos: no existe y responde 404 sin datos.
        mvc.perform(get("/api/public/productos/" + product)).andExpect(status().isNotFound());
    }
}
