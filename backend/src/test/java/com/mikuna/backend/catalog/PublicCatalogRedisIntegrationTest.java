package com.mikuna.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestProducts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec 015 con Redis real (perfil "redis"): las entradas se guardan en Redis, vuelven idénticas tras serializarse
 * (precios, horarios, fechas) y un cambio las borra del almacén compartido, así que cualquier otra instancia lee el
 * dato nuevo. Requiere Redis en localhost:6379 (CI: MIKUNA_TEST_REDIS=true).
 */
@EnabledIfEnvironmentVariable(named = "MIKUNA_TEST_REDIS", matches = "true")
@ActiveProfiles("redis")
class PublicCatalogRedisIntegrationTest extends IntegrationTestSupport {

    private static final String LIST_KEY = "mikuna:cache:public-restaurants::all";

    @Autowired
    StringRedisTemplate redis;

    @Test
    void lasConsultasSeGuardanEnRedisYVuelvenIdenticas() throws Exception {
        UUID owner = restaurantOwner("redis@mail.com").getId();
        UUID restaurant = TestProducts.approvedRestaurant(jdbc, owner, "La Picantería", "Criolla", "Ayacucho");
        TestProducts.product(jdbc, restaurant, "Lomo saltado", "Fondos", "22.50", true, true);
        String catalogKey = "mikuna:cache:public-catalog::" + restaurant;

        String firstList = body("/api/public/restaurants");
        String firstCatalog = body("/api/public/restaurants/" + restaurant + "/catalog");
        assertThat(redis.hasKey(LIST_KEY)).isTrue();
        assertThat(redis.hasKey(catalogKey)).isTrue();
        assertThat(redis.getExpire(LIST_KEY)).isPositive();

        assertThat(body("/api/public/restaurants")).isEqualTo(firstList);
        assertThat(body("/api/public/restaurants/" + restaurant + "/catalog")).isEqualTo(firstCatalog)
                .contains("22.5");
    }

    @Test
    void unCambioBorraLaEntradaDelAlmacenCompartido() throws Exception {
        restaurantOwner("pausa@mail.com");
        String token = loginAndGetToken("pausa@mail.com");
        UUID owner = accounts.findByEmail("pausa@mail.com").orElseThrow().getId();
        TestProducts.approvedRestaurant(jdbc, owner, "La Picantería", "Criolla", "Ayacucho");

        assertThat(body("/api/public/restaurants")).contains("\"attention\":\"OPEN\"");
        assertThat(redis.hasKey(LIST_KEY)).isTrue();

        mvc.perform(put("/api/restaurant/me/availability").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());

        assertThat(redis.hasKey(LIST_KEY)).isFalse();
        assertThat(body("/api/public/restaurants")).contains("\"attention\":\"PAUSED\"");
    }

    private String body(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
