package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.support.HistoryTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** US4: detalle administrativo de pedidos y entregas con pago y cambios de estado (RF-33). */
class AdminOrderDetailIntegrationTest extends HistoryTestSupport {

    private String adminToken;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminToken = adminToken();
    }

    @Test
    void deliveredOrderShowsPartiesLinesAmountsPaymentAndTheChronologicalChanges() throws Exception {
        UUID id = deliveredOrder("k1");

        String body = getAs(adminToken, "/api/admin/orders/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.customer.name").value("Cliente"))
                .andExpect(jsonPath("$.restaurant.name").value("La Picantería"))
                .andExpect(jsonPath("$.courier.email").value("repartidor-a@mail.com"))
                .andExpect(jsonPath("$.deliveryId").exists())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(22.5))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.amounts.total").value(52.25))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                .andExpect(jsonPath("$.payment.method").value("CARD"))
                .andExpect(jsonPath("$.deliveryAddress.street").value("Jr. Lima 123"))
                .andReturn().getResponse().getContentAsString();

        List<String> steps = JsonPath.read(body, "$.statusChanges[*].to");
        assertThat(steps).containsExactly("ACCEPTED", "PREPARING", "READY", "ASSIGNED", "PICKED_UP", "DELIVERED");
        List<String> roles = JsonPath.read(body, "$.statusChanges[*].actorRole");
        assertThat(roles).hasSize(6).contains("RESTAURANT", "COURIER");
        List<String> dates = JsonPath.read(body, "$.statusChanges[*].changedAt");
        assertThat(dates).isSorted();
    }

    @Test
    void cancelledOrderShowsTheReleasedPaymentAndNoCourier() throws Exception {
        UUID id = placeOrder("k1");
        customerCancel(id).andExpect(status().isOk());

        getAs(adminToken, "/api/admin/orders/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("RELEASED"))
                .andExpect(jsonPath("$.courier").doesNotExist())
                .andExpect(jsonPath("$.deliveryId").doesNotExist())
                .andExpect(jsonPath("$.statusChanges.length()").value(1))
                .andExpect(jsonPath("$.statusChanges[0].to").value("CANCELLED"))
                .andExpect(jsonPath("$.statusChanges[0].actorRole").value("CUSTOMER"));
    }

    @Test
    void orderOfADeactivatedCustomerRestaurantOrCourierIsStillReadable() throws Exception {
        UUID id = deliveredOrder("k1");
        disable(customer.getId());
        disable(courierA.getId());

        getAs(adminToken, "/api/admin/orders/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.name").value("Cliente"))
                .andExpect(jsonPath("$.courier.name").exists());
    }

    @Test
    void deliveryDetailShowsTheCourierChangesAndTheAssociatedOrderWithItsPayment() throws Exception {
        UUID orderId = deliveredOrder("k1");
        String order = getAs(adminToken, "/api/admin/orders/" + orderId).andReturn().getResponse()
                .getContentAsString();
        String deliveryId = JsonPath.read(order, "$.deliveryId");

        getAs(adminToken, "/api/admin/deliveries/" + deliveryId).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.courier.email").value("repartidor-a@mail.com"))
                .andExpect(jsonPath("$.assignedAt").exists())
                .andExpect(jsonPath("$.order.paymentStatus").value("CAPTURED"))
                .andExpect(jsonPath("$.order.customer.name").value("Cliente"))
                .andExpect(jsonPath("$.statusChanges.length()").value(6))
                .andExpect(jsonPath("$.statusChanges[5].to").value("DELIVERED"));
    }

    @Test
    void unassignedDeliveryHasNoCourier() throws Exception {
        setAvailable(courierToken, false);
        UUID orderId = readyOrder("k1");
        String order = getAs(adminToken, "/api/admin/orders/" + orderId).andReturn().getResponse()
                .getContentAsString();
        String deliveryId = JsonPath.read(order, "$.deliveryId");

        getAs(adminToken, "/api/admin/deliveries/" + deliveryId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY")).andExpect(jsonPath("$.courier").doesNotExist());
    }

    @Test
    void unknownIdsAreNotFoundAndNoResponseExposesCredentials() throws Exception {
        getAs(adminToken, "/api/admin/orders/" + UUID.randomUUID()).andExpect(status().isNotFound());
        getAs(adminToken, "/api/admin/deliveries/" + UUID.randomUUID()).andExpect(status().isNotFound());

        UUID id = deliveredOrder("k1");
        String body = getAs(adminToken, "/api/admin/orders/" + id).andReturn().getResponse().getContentAsString()
                .toLowerCase();
        assertThat(body).doesNotContain("password").doesNotContain("hash");
    }
}
