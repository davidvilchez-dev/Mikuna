package com.mikuna.backend.history;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.HistoryTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-005 y SC-003: el historial conserva nombres, precios e importes aunque cambie o se retire el producto. */
class HistorySnapshotIntegrationTest extends HistoryTestSupport {

    @Test
    void cambiarYRetirarElProductoNoAlteraLosTresHistoriales() throws Exception {
        UUID id = deliveredOrder("sn-1");

        jdbc.update("update productos set name = 'Lomo renovado', name_key = 'lomo renovado', price = 99.90 "
                + "where id = ?", lomo);
        jdbc.update("update productos set active = false, available = false, withdrawn_at = ? where id = ?",
                Timestamp.from(Instant.now()), lomo);

        history(token, CUSTOMER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderId").value(id.toString()))
                .andExpect(jsonPath("$.items[0].lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.items[0].lines[0].unitPrice").value(22.5))
                .andExpect(jsonPath("$.items[0].lines[0].lineTotal").value(45.0))
                .andExpect(jsonPath("$.items[0].totals.total").value(52.25));
        history(ownerToken, RESTAURANT_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.items[0].lines[0].unitPrice").value(22.5))
                .andExpect(jsonPath("$.items[0].totals.subtotal").value(45.0));
        history(courierToken, COURIER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.items[0].lines[0].quantity").value(2));
    }

    @Test
    void cambiarElNombreDelRestauranteNoAlteraElHistorialDelCliente() throws Exception {
        deliveredOrder("sn-2");
        jdbc.update("update restaurantes set name = 'Otro nombre' where id = ?", restaurantId);

        history(token, CUSTOMER_HISTORY, "").andExpect(jsonPath("$.items[0].restaurant.name").value("La Picantería"));
    }
}
