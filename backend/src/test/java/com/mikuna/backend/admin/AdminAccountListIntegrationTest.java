package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.HistoryTestSupport;
import com.mikuna.backend.support.TestProducts;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** US1: listados, búsqueda y detalle de usuarios, restaurantes y repartidores (RF-32). */
class AdminAccountListIntegrationTest extends HistoryTestSupport {

    private String adminToken;
    private Account adminAccount;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminToken = adminToken();
        adminAccount = accounts.findByEmail("admin@mail.com").orElseThrow();
    }

    @Test
    void usersListCoversCustomerRestaurantAndCourierButNeverAdministrators() throws Exception {
        getAs(adminToken, "/api/admin/users").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[?(@.role == 'ADMIN')]").isEmpty())
                .andExpect(jsonPath("$.items[0].accessEnabled").value(true));
        getAs(adminToken, "/api/admin/users?role=CUSTOMER").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].email").value("cliente@mail.com"))
                .andExpect(jsonPath("$.items[0].operationEnabled").doesNotExist());
        getAs(adminToken, "/api/admin/users?role=COURIER").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].operationEnabled").value(true));
    }

    @Test
    void usersCanBeFilteredByAccessStatusAndSearchedByNameOrEmail() throws Exception {
        disable(customer.getId());

        getAs(adminToken, "/api/admin/users?status=DISABLED").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].accessEnabled").value(false));
        getAs(adminToken, "/api/admin/users?status=ENABLED").andExpect(jsonPath("$.totalItems").value(2));
        getAs(adminToken, "/api/admin/users?q=CLIENTE@").andExpect(jsonPath("$.totalItems").value(1));
        getAs(adminToken, "/api/admin/users?q=dueño").andExpect(jsonPath("$.totalItems").value(1));
        getAs(adminToken, "/api/admin/users?q=%25").andExpect(jsonPath("$.totalItems").value(0));
        getAs(adminToken, "/api/admin/users?q=nadie").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void usersArePaginatedWithoutRepeatingOrSkipping() throws Exception {
        for (int i = 0; i < 4; i++) {
            customer("extra" + i + "@mail.com");
        }

        String first = getAs(adminToken, "/api/admin/users?size=3&page=0").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(7)).andExpect(jsonPath("$.totalPages").value(3))
                .andReturn().getResponse().getContentAsString();
        String second = getAs(adminToken, "/api/admin/users?size=3&page=1").andReturn().getResponse()
                .getContentAsString();
        String third = getAs(adminToken, "/api/admin/users?size=3&page=2").andReturn().getResponse()
                .getContentAsString();

        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String body : java.util.List.of(first, second, third)) {
            ids.addAll(com.jayway.jsonpath.JsonPath.read(body, "$.items[*].id"));
        }
        assertThat(ids).hasSize(7).doesNotHaveDuplicates();
    }

    @Test
    void restaurantsListFiltersByRegistrationOperationAndSearch() throws Exception {
        getAs(adminToken, "/api/admin/restaurants/search").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(restaurantId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("La Picantería"))
                .andExpect(jsonPath("$.items[0].email").value("local@mail.com"))
                .andExpect(jsonPath("$.items[0].registrationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.items[0].operationEnabled").value(true));
        getAs(adminToken, "/api/admin/restaurants/search?registration=APPROVED")
                .andExpect(jsonPath("$.totalItems").value(1));
        getAs(adminToken, "/api/admin/restaurants/search?registration=PENDING")
                .andExpect(jsonPath("$.totalItems").value(0));
        getAs(adminToken, "/api/admin/restaurants/search?operation=INACTIVE")
                .andExpect(jsonPath("$.totalItems").value(0));
        getAs(adminToken, "/api/admin/restaurants/search?operation=ACTIVE")
                .andExpect(jsonPath("$.totalItems").value(1));
        getAs(adminToken, "/api/admin/restaurants/search?q=PICANT").andExpect(jsonPath("$.totalItems").value(1));
        getAs(adminToken, "/api/admin/restaurants/search?q=local@").andExpect(jsonPath("$.totalItems").value(1));
        getAs(adminToken, "/api/admin/restaurants/search?q=zzz").andExpect(jsonPath("$.items").isEmpty());

        jdbc.update("update cuentas set operation_enabled = false where id = ?", restaurantOwnerId());
        getAs(adminToken, "/api/admin/restaurants/search?operation=INACTIVE")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].operationEnabled").value(false))
                .andExpect(jsonPath("$.items[0].accessEnabled").value(true));
    }

    @Test
    void pendingRestaurantsAppearWithTheirRegistrationStatus() throws Exception {
        UUID pending = TestProducts.approvedRestaurant(jdbc, restaurantOwner("pend@mail.com").getId(), "Pendiente",
                "Criolla", "Ayacucho");
        jdbc.update("update restaurantes set status = 'PENDING' where id = ?", pending);

        getAs(adminToken, "/api/admin/restaurants/search?registration=PENDING")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(pending.toString()));
    }

    @Test
    void couriersListFiltersByOperationAndSearch() throws Exception {
        getAs(adminToken, "/api/admin/couriers").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].role").value("COURIER"))
                .andExpect(jsonPath("$.items[0].operationEnabled").value(true));
        getAs(adminToken, "/api/admin/couriers?operation=INACTIVE").andExpect(jsonPath("$.totalItems").value(0));
        getAs(adminToken, "/api/admin/couriers?q=repartidor-a").andExpect(jsonPath("$.totalItems").value(1));
        getAs(adminToken, "/api/admin/couriers?q=otro").andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void detailsShowDataStatusAndAnEmptyActionLog() throws Exception {
        getAs(adminToken, "/api/admin/users/" + customer.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value("cliente@mail.com"))
                .andExpect(jsonPath("$.account.accessEnabled").value(true))
                .andExpect(jsonPath("$.actions").isEmpty());
        getAs(adminToken, "/api/admin/users/" + restaurantOwnerId())
                .andExpect(jsonPath("$.restaurant.category").value("Criolla"));
        getAs(adminToken, "/api/admin/restaurants/" + restaurantId).andExpect(status().isOk())
                .andExpect(jsonPath("$.account.name").value("La Picantería"))
                .andExpect(jsonPath("$.restaurant.address").value("Jr. Lima 123"))
                .andExpect(jsonPath("$.restaurant.available").value(true))
                .andExpect(jsonPath("$.actions").isEmpty());
        getAs(adminToken, "/api/admin/couriers/" + courierA.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.courier.available").value(true))
                .andExpect(jsonPath("$.actions").isEmpty());
    }

    @Test
    void unknownResourcesAreNotFoundAndAdministratorsAreNeverExposed() throws Exception {
        UUID unknown = UUID.randomUUID();
        getAs(adminToken, "/api/admin/users/" + unknown).andExpect(status().isNotFound());
        getAs(adminToken, "/api/admin/restaurants/" + unknown).andExpect(status().isNotFound());
        getAs(adminToken, "/api/admin/couriers/" + unknown).andExpect(status().isNotFound());
        getAs(adminToken, "/api/admin/users/" + adminAccount.getId()).andExpect(status().isNotFound());
        getAs(adminToken, "/api/admin/couriers/" + adminAccount.getId()).andExpect(status().isNotFound());
        getAs(adminToken, "/api/admin/couriers/" + customer.getId()).andExpect(status().isNotFound());
        getAs(adminToken, "/api/admin/restaurants/" + restaurantOwnerId()).andExpect(status().isNotFound());
    }

    @Test
    void invalidParametersAreRejectedWithTheOffendingField() throws Exception {
        getAs(adminToken, "/api/admin/users?page=-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").exists());
        getAs(adminToken, "/api/admin/users?size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
        getAs(adminToken, "/api/admin/users?size=101").andExpect(status().isBadRequest());
        getAs(adminToken, "/api/admin/users?role=NOPE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
        getAs(adminToken, "/api/admin/users?role=ADMIN").andExpect(status().isBadRequest());
        getAs(adminToken, "/api/admin/users?status=NOPE").andExpect(status().isBadRequest());
        getAs(adminToken, "/api/admin/restaurants/search?registration=NOPE").andExpect(status().isBadRequest());
        getAs(adminToken, "/api/admin/restaurants/search?operation=NOPE").andExpect(status().isBadRequest());
        getAs(adminToken, "/api/admin/couriers?operation=NOPE").andExpect(status().isBadRequest());
    }

    @Test
    void noResponseExposesCredentials() throws Exception {
        for (String path : java.util.List.of("/api/admin/users", "/api/admin/users/" + customer.getId(),
                "/api/admin/restaurants/search", "/api/admin/restaurants/" + restaurantId, "/api/admin/couriers",
                "/api/admin/couriers/" + courierA.getId())) {
            String body = getAs(adminToken, path).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString().toLowerCase();
            assertThat(body).doesNotContain("password").doesNotContain("hash").doesNotContain("pbkdf2");
        }
    }

    private UUID restaurantOwnerId() {
        return accounts.findByEmail("local@mail.com").orElseThrow().getId();
    }
}
