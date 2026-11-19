package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.HistoryTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** US5: solo el rol ADMIN accede a la administración (RF-04, RNF-07) y la administración no modifica pedidos. */
class AdminAccessControlIntegrationTest extends HistoryTestSupport {

    private record Endpoint(HttpMethod method, String path) {
    }

    private final UUID id = UUID.randomUUID();
    private List<Endpoint> endpoints;
    private String adminToken;
    private Account adminAccount;

    @BeforeEach
    void prepare() throws Exception {
        adminToken = adminToken();
        adminAccount = accounts.findByEmail("admin@mail.com").orElseThrow();
        endpoints = List.of(
                new Endpoint(HttpMethod.GET, "/api/admin/users"),
                new Endpoint(HttpMethod.GET, "/api/admin/users/" + id),
                new Endpoint(HttpMethod.POST, "/api/admin/users/" + customer.getId() + "/disable"),
                new Endpoint(HttpMethod.POST, "/api/admin/users/" + customer.getId() + "/enable"),
                new Endpoint(HttpMethod.GET, "/api/admin/restaurants/search"),
                new Endpoint(HttpMethod.GET, "/api/admin/restaurants/" + restaurantId),
                new Endpoint(HttpMethod.POST, "/api/admin/restaurants/" + restaurantId + "/deactivate"),
                new Endpoint(HttpMethod.POST, "/api/admin/restaurants/" + restaurantId + "/activate"),
                new Endpoint(HttpMethod.GET, "/api/admin/couriers"),
                new Endpoint(HttpMethod.GET, "/api/admin/couriers/" + courierA.getId()),
                new Endpoint(HttpMethod.POST, "/api/admin/couriers/" + courierA.getId() + "/deactivate"),
                new Endpoint(HttpMethod.POST, "/api/admin/couriers/" + courierA.getId() + "/activate"),
                new Endpoint(HttpMethod.GET, "/api/admin/orders"),
                new Endpoint(HttpMethod.GET, "/api/admin/orders/" + id),
                new Endpoint(HttpMethod.GET, "/api/admin/deliveries"),
                new Endpoint(HttpMethod.GET, "/api/admin/deliveries/" + id));
    }

    @Test
    void everyEndpointRejectsCustomerRestaurantAndCourierWithForbidden() throws Exception {
        for (Endpoint endpoint : endpoints) {
            for (String bearer : List.of(token, ownerToken, courierToken)) {
                call(endpoint, bearer).andExpect(status().isForbidden());
            }
        }
        assertNothingChanged();
    }

    @Test
    void everyEndpointRejectsRequestsWithoutASessionAsUnauthenticated() throws Exception {
        for (Endpoint endpoint : endpoints) {
            call(endpoint, null).andExpect(status().isUnauthorized());
        }
        assertNothingChanged();
    }

    @Test
    void aDisabledAdministratorIsRejected() throws Exception {
        disable(adminAccount.getId());

        for (Endpoint endpoint : endpoints) {
            call(endpoint, adminToken).andExpect(status().isForbidden());
        }
        assertNothingChanged();
    }

    @Test
    void ordersAndDeliveriesCannotBeModifiedThroughTheAdministrationApi() throws Exception {
        UUID order = placeOrder("k1");
        String statusBefore = orderStatus(order);

        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            for (String path : List.of("/api/admin/orders", "/api/admin/orders/" + order, "/api/admin/deliveries",
                    "/api/admin/deliveries/" + id)) {
                mvc.perform(request(method, path).header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isMethodNotAllowed());
            }
        }

        assertThat(orderStatus(order)).isEqualTo(statusBefore);
        assertThat(count("pedidos")).isEqualTo(1);
    }

    @Test
    void accountsCannotBeDeleted() throws Exception {
        for (String path : List.of("/api/admin/users/" + customer.getId(),
                "/api/admin/restaurants/" + restaurantId, "/api/admin/couriers/" + courierA.getId())) {
            mvc.perform(request(HttpMethod.DELETE, path).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertThat(accounts.findById(customer.getId())).isPresent();
    }

    private org.springframework.test.web.servlet.ResultActions call(Endpoint endpoint, String bearer)
            throws Exception {
        MockHttpServletRequestBuilder request = request(endpoint.method(), endpoint.path());
        return mvc.perform(bearer == null ? request : request.header("Authorization", "Bearer " + bearer));
    }

    private static MockHttpServletRequestBuilder request(HttpMethod method, String path) {
        return MockMvcRequestBuilders.request(method, path).contentType("application/json");
    }

    private void assertNothingChanged() {
        assertThat(count("acciones_admin_cuenta")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from cuentas where id <> ? "
                + "and (status <> 'ENABLED' or operation_enabled = false)", Integer.class, adminAccount.getId()))
                .isZero();
    }
}
