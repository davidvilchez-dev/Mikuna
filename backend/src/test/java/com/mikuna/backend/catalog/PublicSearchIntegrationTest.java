package com.mikuna.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestProducts;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** RF-09: búsqueda pública de restaurantes, visibilidad y datos expuestos. */
class PublicSearchIntegrationTest extends IntegrationTestSupport {

    private static final String PUBLIC = "/api/public/restaurants";

    private UUID restaurant(String email, String name, String category, String zone) {
        Account owner = restaurantOwner(email);
        return TestProducts.approvedRestaurant(jdbc, owner.getId(), name, category, zone);
    }

    private ResultActions search(String query) throws Exception {
        return mvc.perform(get(PUBLIC + query));
    }

    @Test
    void sinSesionSoloApareceLoAprobadoConCuentaHabilitadaConSuAtencion() throws Exception {
        UUID open = restaurant("a@mail.com", "Abierto", "Criolla", "Ayacucho");
        UUID paused = restaurant("b@mail.com", "Pausado", "Criolla", "Ayacucho");
        UUID closed = restaurant("c@mail.com", "Cerrado", "Criolla", "Ayacucho");
        UUID pending = restaurant("d@mail.com", "Pendiente", "Criolla", "Ayacucho");
        UUID rejected = restaurant("e@mail.com", "Rechazado", "Criolla", "Ayacucho");
        UUID disabled = restaurant("f@mail.com", "Inhabilitado", "Criolla", "Ayacucho");
        jdbc.update("update restaurantes set available = false where id = ?", paused);
        TestProducts.closeAllDays(jdbc, closed);
        jdbc.update("update restaurantes set status = 'PENDING' where id = ?", pending);
        jdbc.update("update restaurantes set status = 'REJECTED' where id = ?", rejected);
        disable(accounts.findByEmail("f@mail.com").orElseThrow().getId());

        String body = search("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Abierto"))
                .andExpect(jsonPath("$.items[0].attention").value("OPEN"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<java.util.List<String>>read(body, "$.items[*].name")).containsExactly("Abierto", "Cerrado", "Pausado");
        assertThat(JsonPath.<String>read(body, "$.items[1].attention")).isEqualTo("OUTSIDE_HOURS");
        assertThat(JsonPath.<String>read(body, "$.items[2].attention")).isEqualTo("PAUSED");
        assertThat(open).isNotNull();
        assertThat(disabled).isNotNull();
    }

    @Test
    void filtraPorNombreCategoriaYZonaYCombinados() throws Exception {
        restaurant("a@mail.com", "Pollería Don Pepe", "Pollos", "Huamanga");
        restaurant("b@mail.com", "La Picantería", "Criolla", "Huamanga");
        restaurant("c@mail.com", "Pizza Nova", "Pizza", "Jesús Nazareno");

        search("?name=pollERIA").andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].name").value("Pollería Don Pepe"));
        search("?name=picanteria").andExpect(jsonPath("$.totalItems").value(1));
        search("?category=criolla").andExpect(jsonPath("$.totalItems").value(1));
        search("?zone=huamanga").andExpect(jsonPath("$.totalItems").value(2));
        search("?zone=jesus nazareno").andExpect(jsonPath("$.totalItems").value(1));
        search("?category=pollos&zone=huamanga").andExpect(jsonPath("$.totalItems").value(1));
        search("?category=pollos&zone=jesus nazareno").andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
        // «%» y «_» son texto literal, no comodines
        mvc.perform(get(PUBLIC).param("name", "%")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get(PUBLIC).param("name", "_")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void laRespuestaNoExponeDatosSensibles() throws Exception {
        restaurant("a@mail.com", "Abierto", "Criolla", "Ayacucho");

        String body = search("").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Set<String> keys = JsonPath.<java.util.Map<String, Object>>read(body, "$.items[0]").keySet();
        assertThat(keys).containsExactlyInAnyOrder("id", "name", "category", "description", "zone", "attention", "logoUrl", "coverUrl", "distanceKm", "branchCount");
        assertThat(body).doesNotContain("Jr. Lima 123").doesNotContain("966123456").doesNotContain("a@mail.com");
    }

    @Test
    void lasOpcionesDeFiltroSoloSalenDeRestaurantesVisibles() throws Exception {
        restaurant("a@mail.com", "A", "Criolla", "Huamanga");
        restaurant("b@mail.com", "B", "criolla", "Huamanga");
        UUID pending = restaurant("c@mail.com", "C", "Secreta", "Zona oculta");
        jdbc.update("update restaurantes set status = 'PENDING' where id = ?", pending);

        mvc.perform(get(PUBLIC + "/filters")).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0]").value("Criolla"))
                .andExpect(jsonPath("$.zones[0]").value("Huamanga"))
                .andExpect(jsonPath("$.zones.length()").value(1));
    }

    @Test
    void paginaYValidaLosParametros() throws Exception {
        for (int i = 0; i < 3; i++) {
            restaurant("r" + i + "@mail.com", "Rest " + i, "Criolla", "Ayacucho");
        }
        search("?size=2").andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        search("?size=2&page=1").andExpect(jsonPath("$.items.length()").value(1));
        search("?size=2&page=9").andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalItems").value(3));
        search("?page=-1").andExpect(status().isBadRequest());
        search("?size=0").andExpect(status().isBadRequest());
        search("?size=51").andExpect(status().isBadRequest());
        search("?page=abc").andExpect(status().isBadRequest());
    }

    @Test
    void rutasPublicasSoloLecturaYElTokenInvalidoNoSeAcepta() throws Exception {
        restaurant("a@mail.com", "Abierto", "Criolla", "Ayacucho");

        mvc.perform(get(PUBLIC).header("Authorization", "Bearer token.manipulado.x")).andExpect(status().isUnauthorized());
        mvc.perform(post(PUBLIC)).andExpect(status().isUnauthorized());
        mvc.perform(put(PUBLIC + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(delete(PUBLIC + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        // Con un token válido de otro rol la lectura sigue siendo la misma lectura pública.
        restaurantOwner("otro@mail.com");
        String token = loginAndGetToken("otro@mail.com");
        mvc.perform(get(PUBLIC).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }
}
