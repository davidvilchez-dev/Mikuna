package com.mikuna.backend.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestProducts;
import org.junit.jupiter.api.Test;

/**
 * Spec 015 (contrato, RNF-07): las métricas de aciertos de caché solo las lee el administrador; la salud sigue
 * siendo pública.
 */
class ActuatorMetricsSecurityIntegrationTest extends IntegrationTestSupport {

    private static final String HITS = "/actuator/metrics/cache.gets?tag=cache:public-restaurants&tag=result:hit";

    @Test
    void lasMetricasSonSoloParaElAdministrador() throws Exception {
        mvc.perform(get(HITS)).andExpect(status().isUnauthorized());

        customer("cliente-metricas@mail.com");
        mvc.perform(get(HITS).header("Authorization", "Bearer " + loginAndGetToken("cliente-metricas@mail.com")))
                .andExpect(status().isForbidden());

        TestProducts.approvedRestaurant(jdbc, restaurantOwner("local-metricas@mail.com").getId(), "La Picantería",
                "Criolla", "Ayacucho");
        mvc.perform(get("/api/public/restaurants")).andExpect(status().isOk());
        mvc.perform(get("/api/public/restaurants")).andExpect(status().isOk());

        admin("admin-metricas@mail.com");
        mvc.perform(get(HITS).header("Authorization", "Bearer " + loginAndGetToken("admin-metricas@mail.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("cache.gets"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber());
    }

    @Test
    void laSaludYLasSondasSonPublicas() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
