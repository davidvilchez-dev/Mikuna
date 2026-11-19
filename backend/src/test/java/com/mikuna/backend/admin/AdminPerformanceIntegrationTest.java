package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.HistoryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Meta del plan: con 1.000 cuentas y 1.000 pedidos, la primera página de cada listado responde en menos de 2 s. */
class AdminPerformanceIntegrationTest extends HistoryTestSupport {

    @Test
    void firstPagesWithAThousandAccountsAndOrdersRespondInUnderTwoSeconds() throws Exception {
        String admin = adminToken();
        UUID base = placeOrder("pf-base");
        jdbc.update("""
                insert into pedidos (id, customer_id, restaurant_id, status, confirmation_key, payment_method,
                    subtotal, delivery_fee, commission, total, restaurant_name, delivery_label, delivery_street,
                    delivery_district, delivery_reference, delivery_phone, created_at)
                select gen_random_uuid(), customer_id, restaurant_id, 'READY', 'k' || g, payment_method,
                    subtotal, delivery_fee, commission, total, restaurant_name, delivery_label, delivery_street,
                    delivery_district, delivery_reference, delivery_phone, created_at - (g || ' minutes')::interval
                from pedidos, generate_series(1, 999) g where id = ?""", base);
        jdbc.update("""
                insert into entregas (id, order_id, courier_id, ready_at)
                select gen_random_uuid(), p.id, null, p.created_at from pedidos p where p.id <> ?""", base);
        jdbc.update("""
                insert into cuentas (id, name, email, password_hash, role, status, created_at, operation_enabled)
                select gen_random_uuid(), 'Cliente ' || g, 'masivo' || g || '@mail.com', 'x', 'CUSTOMER', 'ENABLED',
                    now(), true
                from generate_series(1, 1000) g""");
        assertThat(count("pedidos")).isEqualTo(1000);

        for (String path : new String[] {"/api/admin/users?size=20", "/api/admin/orders?size=20",
                "/api/admin/deliveries?size=20"}) {
            getAs(admin, path); // calienta la conexión y las consultas
            long start = System.nanoTime();
            getAs(admin, path).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20));
            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            System.out.printf("Administración: %s en %.3f s%n", path, seconds);
            assertThat(seconds).isLessThan(2.0);
        }
        getAs(admin, "/api/admin/orders?size=20").andExpect(jsonPath("$.totalItems").value(1000));
        getAs(admin, "/api/admin/users?q=masivo99&size=20").andExpect(status().isOk());
    }
}
