package com.mikuna.backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.HistoryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-001: con 1.000 operaciones, la primera página del historial responde en menos de 2 s. No acredita RNF-01. */
class HistoryPerformanceIntegrationTest extends HistoryTestSupport {

    @Test
    void laPrimeraPaginaConMilPedidosRespondeEnMenosDeDosSegundos() throws Exception {
        UUID base = placeOrder("pf-base");
        jdbc.update("""
                insert into pedidos (id, customer_id, restaurant_id, status, confirmation_key, payment_method,
                    subtotal, delivery_fee, commission, total, restaurant_name, delivery_label, delivery_street,
                    delivery_district, delivery_reference, delivery_phone, created_at)
                select gen_random_uuid(), customer_id, restaurant_id, 'REJECTED', 'k' || g, payment_method,
                    subtotal, delivery_fee, commission, total, restaurant_name, delivery_label, delivery_street,
                    delivery_district, delivery_reference, delivery_phone, created_at - (g || ' minutes')::interval
                from pedidos, generate_series(1, 999) g where id = ?""", base);
        jdbc.update("""
                insert into lineas_pedido (id, order_id, product_id, product_name, unit_price, quantity, line_total)
                select gen_random_uuid(), p.id, ?, 'Lomo saltado', 22.50, 2, 45.00
                from pedidos p where p.id <> ?""", lomo, base);
        jdbc.update("update pedidos set created_at = created_at");
        assertThat(count("pedidos")).isEqualTo(1000);

        history(token, CUSTOMER_HISTORY, "?size=20"); // calienta la conexión y las consultas
        long start = System.nanoTime();
        history(token, CUSTOMER_HISTORY, "?size=20").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1000)).andExpect(jsonPath("$.items.length()").value(20));
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;

        System.out.printf("SC-001: primera página con 1000 pedidos en %.3f s%n", seconds);
        assertThat(seconds).isLessThan(2.0);
        history(ownerToken, RESTAURANT_HISTORY, "?size=20").andExpect(jsonPath("$.totalItems").value(1000));
    }
}
