package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.support.HistoryTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** US3: listados administrativos de pedidos y entregas por estado (RF-33). */
class AdminOrderListIntegrationTest extends HistoryTestSupport {

    private String adminToken;
    private UUID delivered;
    private UUID assigned;
    private UUID pending;
    private UUID cancelled;

    @BeforeEach
    void seedOrders() throws Exception {
        adminToken = adminToken();
        delivered = deliveredOrder("k1");
        assigned = readyOrder("k2");
        pending = placeOrder("k3");
        cancelled = placeOrder("k4");
        customerCancel(cancelled).andExpect(status().isOk());
    }

    @Test
    void ordersOfTheWholePlatformAreListedFromNewestToOldest() throws Exception {
        String body = getAs(adminToken, "/api/admin/orders").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].customer.name").value("Cliente"))
                .andExpect(jsonPath("$.items[0].customer.email").value("cliente@mail.com"))
                .andExpect(jsonPath("$.items[0].restaurant.name").value("La Picantería"))
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.items[*].id");
        assertThat(ids).containsExactly(cancelled.toString(), pending.toString(), assigned.toString(),
                delivered.toString());
    }

    @Test
    void ordersCanBeFilteredByStatus() throws Exception {
        assertStatusCount("/api/admin/orders", "DELIVERED", 1, delivered);
        assertStatusCount("/api/admin/orders", "ASSIGNED", 1, assigned);
        assertStatusCount("/api/admin/orders", "PENDING", 1, pending);
        assertStatusCount("/api/admin/orders", "CANCELLED", 1, cancelled);
        getAs(adminToken, "/api/admin/orders?status=ACCEPTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void ordersAreShownWithTheirPaymentStatus() throws Exception {
        getAs(adminToken, "/api/admin/orders?status=DELIVERED").andExpect(jsonPath("$.items[0].paymentStatus")
                .value("CAPTURED"));
        getAs(adminToken, "/api/admin/orders?status=CANCELLED").andExpect(jsonPath("$.items[0].paymentStatus")
                .value("RELEASED"));
        getAs(adminToken, "/api/admin/orders?status=PENDING").andExpect(jsonPath("$.items[0].paymentStatus")
                .value("AUTHORIZED"));
    }

    @Test
    void ordersArePaginatedWithoutRepeatingOrSkipping() throws Exception {
        List<String> all = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            String body = getAs(adminToken, "/api/admin/orders?size=3&page=" + page).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalPages").value(2)).andReturn().getResponse().getContentAsString();
            all.addAll(JsonPath.read(body, "$.items[*].id"));
        }
        assertThat(all).hasSize(4).doesNotHaveDuplicates();
    }

    @Test
    void deliveriesAreListedByTheStatusOfTheirOrder() throws Exception {
        String body = getAs(adminToken, "/api/admin/deliveries").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].restaurantName").value("La Picantería"))
                .andExpect(jsonPath("$.items[0].courier.email").value("repartidor-a@mail.com"))
                .andReturn().getResponse().getContentAsString();
        List<String> orderIds = JsonPath.read(body, "$.items[*].orderId");
        assertThat(orderIds).containsExactly(assigned.toString(), delivered.toString());

        getAs(adminToken, "/api/admin/deliveries?status=DELIVERED").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].orderId").value(delivered.toString()))
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"));
        getAs(adminToken, "/api/admin/deliveries?status=ASSIGNED").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].orderId").value(assigned.toString()));
        getAs(adminToken, "/api/admin/deliveries?status=READY").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        getAs(adminToken, "/api/admin/deliveries?status=PICKED_UP").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void aDeliveryStatusWithoutDeliveriesIsRejected() throws Exception {
        getAs(adminToken, "/api/admin/deliveries?status=PENDING").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
        getAs(adminToken, "/api/admin/deliveries?status=CANCELLED").andExpect(status().isBadRequest());
        getAs(adminToken, "/api/admin/deliveries?status=NOPE").andExpect(status().isBadRequest());
        getAs(adminToken, "/api/admin/orders?status=NOPE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
        getAs(adminToken, "/api/admin/orders?page=-1").andExpect(status().isBadRequest());
        getAs(adminToken, "/api/admin/deliveries?size=101").andExpect(status().isBadRequest());
    }

    @Test
    void ordersOfDeactivatedRestaurantsAndCouriersRemainVisible() throws Exception {
        jdbc.update("update cuentas set operation_enabled = false where role in ('RESTAURANT', 'COURIER')");
        disable(courierA.getId());

        getAs(adminToken, "/api/admin/orders").andExpect(jsonPath("$.totalItems").value(4));
        getAs(adminToken, "/api/admin/deliveries").andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].courier.id").value(courierA.getId().toString()));
    }

    private void assertStatusCount(String path, String statusValue, int expected, UUID expectedId) throws Exception {
        getAs(adminToken, path + "?status=" + statusValue).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(expected))
                .andExpect(jsonPath("$.items[0].id").value(expectedId.toString()))
                .andExpect(jsonPath("$.items[0].status").value(statusValue));
    }
}
