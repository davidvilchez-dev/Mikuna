package com.mikuna.backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.support.HistoryTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-006 y FR-007: orden del más reciente al más antiguo, paginación sin repetir ni omitir, y filtros. */
class HistoryPagingFilterIntegrationTest extends HistoryTestSupport {

    private static final String[] PATHS = {CUSTOMER_HISTORY, RESTAURANT_HISTORY, COURIER_HISTORY};

    private final List<UUID> newestFirst = new ArrayList<>();

    private String bearer(int role) {
        return new String[] {token, ownerToken, courierToken}[role];
    }

    /** Cinco pedidos entregados; los dos más antiguos comparten fecha para probar el desempate por id. */
    private void fiveDelivered() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(deliveredOrder("pg-" + i));
        }
        Instant base = Instant.parse("2026-11-10T15:00:00Z");
        // índices 0 y 1 con la misma fecha; 2, 3 y 4 en días distintos y consecutivos.
        long[] days = {0, 0, 2, 4, 6};
        for (int i = 0; i < 5; i++) {
            jdbc.update("update pedidos set created_at = ? where id = ?",
                    Timestamp.from(base.plusSeconds(days[i] * 86_400)), ids.get(i));
        }
        List<UUID> tied = jdbc.queryForList("select id from pedidos where created_at = ? order by id desc",
                UUID.class, Timestamp.from(base));
        newestFirst.add(ids.get(4));
        newestFirst.add(ids.get(3));
        newestFirst.add(ids.get(2));
        newestFirst.addAll(tied);
    }

    @Test
    void recorrerLasPaginasMuestraCadaOperacionUnaVezEnOrdenDescendente() throws Exception {
        fiveDelivered();

        for (int role = 0; role < 3; role++) {
            List<UUID> seen = new ArrayList<>();
            int[] expectedSizes = {2, 2, 1};
            for (int page = 0; page < 3; page++) {
                String body = history(bearer(role), PATHS[role], "?size=2&page=" + page).andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalItems").value(5)).andExpect(jsonPath("$.totalPages").value(3))
                        .andExpect(jsonPath("$.items.length()").value(expectedSizes[page]))
                        .andReturn().getResponse().getContentAsString();
                List<String> ids = JsonPath.read(body, "$.items[*].orderId");
                ids.forEach(id -> seen.add(UUID.fromString(id)));
            }
            assertThat(seen).as("rol " + role).containsExactlyElementsOf(newestFirst);
        }
    }

    @Test
    void unaPaginaFueraDeRangoDevuelveUnaListaVaciaSinError() throws Exception {
        fiveDelivered();

        for (int role = 0; role < 3; role++) {
            history(bearer(role), PATHS[role], "?size=2&page=9").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.totalItems").value(5));
        }
    }

    @Test
    void losParametrosDePaginaInvalidosSonErrorDeSolicitud() throws Exception {
        for (int role = 0; role < 3; role++) {
            for (String query : new String[] {"?size=0", "?size=101", "?page=-1", "?status=EN_CAMINO",
                    "?from=18-11-2026", "?from=2026-11-18&to=2026-11-17"}) {
                history(bearer(role), PATHS[role], query).andExpect(status().isBadRequest());
            }
        }
        history(token, CUSTOMER_HISTORY, "?from=2026-11-18&to=2026-11-17")
                .andExpect(jsonPath("$.errors.from").exists());
    }

    @Test
    void filtraPorEstadoEnCadaRol() throws Exception {
        UUID delivered = deliveredOrder("pf-1");
        UUID assigned = readyOrder("pf-2");

        history(courierToken, COURIER_HISTORY, "?status=DELIVERED").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].orderId").value(delivered.toString()));
        history(courierToken, COURIER_HISTORY, "?status=ASSIGNED").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].orderId").value(assigned.toString()));
        UUID rejected = placeOrder("pf-3");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        for (int role = 0; role < 2; role++) {
            history(bearer(role), PATHS[role], "?status=REJECTED").andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].orderId").value(rejected.toString()));
            history(bearer(role), PATHS[role], "?status=DELIVERED").andExpect(jsonPath("$.totalItems").value(1));
            history(bearer(role), PATHS[role], "").andExpect(jsonPath("$.totalItems").value(3));
        }
    }

    @Test
    void filtraPorRangoDeFechasInclusivoYLoCombinaConElEstado() throws Exception {
        fiveDelivered();
        // Se reubican con la zona de la plataforma (UTC-5): 23:59 del 17 y 00:00 del 18 son días distintos.
        List<UUID> ids = jdbc.queryForList("select id from pedidos order by created_at, id", UUID.class);
        jdbc.update("update pedidos set created_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-11-18T04:59:59Z")), ids.get(0)); // 17 23:59:59 Lima
        jdbc.update("update pedidos set created_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-11-18T05:00:00Z")), ids.get(1)); // 18 00:00:00 Lima
        jdbc.update("update pedidos set created_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-11-19T04:59:59Z")), ids.get(2)); // 18 23:59:59 Lima
        jdbc.update("update pedidos set created_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-11-19T05:00:00Z")), ids.get(3)); // 19 00:00:00 Lima
        jdbc.update("update pedidos set created_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-11-25T12:00:00Z")), ids.get(4));

        for (int role = 0; role < 3; role++) {
            history(bearer(role), PATHS[role], "?from=2026-11-18&to=2026-11-18")
                    .andExpect(jsonPath("$.totalItems").value(2));
            history(bearer(role), PATHS[role], "?from=2026-11-18").andExpect(jsonPath("$.totalItems").value(4));
            history(bearer(role), PATHS[role], "?to=2026-11-18").andExpect(jsonPath("$.totalItems").value(3));
            history(bearer(role), PATHS[role], "?from=2026-11-18&to=2026-11-18&status=DELIVERED")
                    .andExpect(jsonPath("$.totalItems").value(2));
            history(bearer(role), PATHS[role], "?from=2026-11-18&to=2026-11-18&status=ASSIGNED")
                    .andExpect(jsonPath("$.totalItems").value(0));
            history(bearer(role), PATHS[role], "?from=2030-01-01").andExpect(jsonPath("$.totalItems").value(0));
        }
    }
}
