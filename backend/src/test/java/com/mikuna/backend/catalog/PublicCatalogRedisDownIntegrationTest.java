package com.mikuna.backend.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestProducts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Spec 015, FR-006 y SC-005: con el perfil "redis" y Redis inalcanzable (puerto sin servicio), la consulta pública
 * responde igual desde la base de datos. No necesita Redis, así que corre siempre.
 */
@ActiveProfiles("redis")
@TestPropertySource(properties = {"spring.data.redis.port=1", "spring.data.redis.connect-timeout=200ms"})
class PublicCatalogRedisDownIntegrationTest extends IntegrationTestSupport {

    @Override
    protected void clearCaches() {
        // Con Redis caído, vaciar la caché también fallaría; no hay nada guardado que limpiar.
    }

    @Test
    void sinRedisLaBusquedaLosFiltrosYElCatalogoRespondenDesdeLaBaseDeDatos() throws Exception {
        UUID owner = restaurantOwner("redis-caido@mail.com").getId();
        UUID restaurant = TestProducts.approvedRestaurant(jdbc, owner, "La Picantería", "Criolla", "Ayacucho");
        TestProducts.product(jdbc, restaurant, "Lomo saltado", "Fondos", "22.50", true, true);

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/public/restaurants")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].name").value("La Picantería"))
                    .andExpect(jsonPath("$.items[0].attention").value("OPEN"));
            mvc.perform(get("/api/public/restaurants/filters")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories[0]").value("Criolla"));
            mvc.perform(get("/api/public/restaurants/" + restaurant + "/catalog")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.products.items[0].name").value("Lomo saltado"));
        }
    }
}
