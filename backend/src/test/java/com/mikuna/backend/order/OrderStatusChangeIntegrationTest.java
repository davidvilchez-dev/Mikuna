package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.OrderStatusTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-012: cada cambio efectivo se registra con fecha y responsable; los intentos denegados no dejan rastro. */
class OrderStatusChangeIntegrationTest extends OrderStatusTestSupport {

    @Test
    void elRecorridoCompletoDejaUnRegistroPorCambioConSuResponsable() throws Exception {
        UUID id = placeOrder("l1");
        UUID ownerId = accounts.findByEmail("local@mail.com").orElseThrow().getId();
        Instant before = Instant.now().minusSeconds(5);

        restaurantAct(id, "accept").andExpect(status().isOk());
        restaurantAct(id, "start-preparation").andExpect(status().isOk());
        restaurantAct(id, "mark-ready").andExpect(status().isOk());

        List<Map<String, Object>> rows = changesOf(id);
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("from_status")).containsExactly("PENDING", "ACCEPTED", "PREPARING");
        assertThat(rows).extracting(r -> r.get("to_status")).containsExactly("ACCEPTED", "PREPARING", "READY");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("actor_role")).isEqualTo("RESTAURANT");
            assertThat(row.get("actor_id")).isEqualTo(ownerId);
            assertThat(((Timestamp) row.get("changed_at")).toInstant()).isAfter(before);
        });
    }

    @Test
    void rechazoYCancelacionRegistranAlResponsableYElMotivo() throws Exception {
        UUID rejected = placeOrder("l2a");
        UUID cancelled = placeOrder("l2b");
        act(ownerToken, RESTAURANT_ORDERS + "/" + rejected + "/reject", "{\"reason\":\"Cerrado\"}")
                .andExpect(status().isOk());
        act(token, ORDERS + "/" + cancelled + "/cancel", "{\"reason\":\"Ya no\"}").andExpect(status().isOk());

        assertThat(changesOf(rejected)).singleElement().satisfies(row -> {
            assertThat(row.get("actor_role")).isEqualTo("RESTAURANT");
            assertThat(row.get("to_status")).isEqualTo("REJECTED");
            assertThat(row.get("reason")).isEqualTo("Cerrado");
        });
        assertThat(changesOf(cancelled)).singleElement().satisfies(row -> {
            assertThat(row.get("actor_role")).isEqualTo("CUSTOMER");
            assertThat(row.get("actor_id")).isEqualTo(customer.getId());
            assertThat(row.get("to_status")).isEqualTo("CANCELLED");
            assertThat(row.get("reason")).isEqualTo("Ya no");
        });
    }

    @Test
    void losIntentosDenegadosONoValidosNoCreanRegistros() throws Exception {
        UUID id = placeOrder("l3");
        String other = otherOwnerToken();

        act(null, RESTAURANT_ORDERS + "/" + id + "/accept").andExpect(status().isUnauthorized());
        act(token, RESTAURANT_ORDERS + "/" + id + "/accept").andExpect(status().isForbidden());
        act(other, RESTAURANT_ORDERS + "/" + id + "/accept").andExpect(status().isNotFound());
        restaurantAct(id, "mark-ready").andExpect(status().isConflict());

        assertThat(count("cambios_estado_pedido")).isZero();
    }
}
