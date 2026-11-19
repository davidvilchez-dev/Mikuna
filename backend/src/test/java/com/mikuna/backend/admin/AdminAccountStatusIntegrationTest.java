package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.HistoryTestSupport;
import com.mikuna.backend.support.TestOrders;
import com.mikuna.backend.support.TestProducts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** US2: activar y desactivar cuentas, con su efecto en acceso, búsqueda, pedidos y candidatos (RF-32). */
class AdminAccountStatusIntegrationTest extends HistoryTestSupport {

    private String adminToken;
    private Account adminAccount;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminToken = adminToken();
        adminAccount = accounts.findByEmail("admin@mail.com").orElseThrow();
    }

    @Test
    void disablingAUserBlocksTheirSessionAndLoginAndEnablingRestoresIt() throws Exception {
        act(adminToken, "/api/admin/users/" + customer.getId() + "/disable").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.accessEnabled").value(false))
                .andExpect(jsonPath("$.actions[0].action").value("DEACTIVATED"))
                .andExpect(jsonPath("$.actions[0].scope").value("ACCESS"));

        getAs(token, ORDERS + "/history").andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"cliente@mail.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isForbidden());

        act(adminToken, "/api/admin/users/" + customer.getId() + "/enable").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.accessEnabled").value(true))
                .andExpect(jsonPath("$.actions[0].action").value("ACTIVATED"));
        getAs(token, ORDERS + "/history").andExpect(status().isOk());
        loginAndGetToken("cliente@mail.com");
    }

    @Test
    void deactivatedRestaurantLeavesTheSearchAndStopsReceivingOrdersButItsOwnerAndOrdersInProgressContinue()
            throws Exception {
        UUID inProgress = placeOrder("k1");
        getAs(null, "/api/public/restaurants").andExpect(jsonPath("$.totalItems").value(1));

        act(adminToken, "/api/admin/restaurants/" + restaurantId + "/deactivate").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.operationEnabled").value(false))
                .andExpect(jsonPath("$.account.accessEnabled").value(true))
                .andExpect(jsonPath("$.actions[0].scope").value("OPERATION"))
                .andExpect(jsonPath("$.actions[0].action").value("DEACTIVATED"));

        getAs(null, "/api/public/restaurants").andExpect(jsonPath("$.totalItems").value(0));
        getAs(null, "/api/public/restaurants/" + restaurantId).andExpect(status().isNotFound());
        int before = count("pedidos");
        jdbc.update("update carritos set restaurant_id = ?, selected_address_id = ? where id = ?", restaurantId,
                addressId, cartId);
        jdbc.update("delete from lineas_carrito where cart_id = ?", cartId);
        TestOrders.line(jdbc, cartId, lomo, 2);
        confirm(token, "k2", body(TOTAL)).andExpect(status().is4xxClientError());
        assertThat(count("pedidos")).isEqualTo(before);

        // El titular conserva el acceso y su historial, y el pedido en curso sigue su flujo.
        loginAndGetToken("local@mail.com");
        getAs(ownerToken, RESTAURANT_HISTORY).andExpect(status().isOk());
        restaurantAct(inProgress, "accept").andExpect(status().isOk());
        restaurantAct(inProgress, "start-preparation").andExpect(status().isOk());
        restaurantAct(inProgress, "mark-ready").andExpect(status().isOk());
        courierAct(courierToken, inProgress, "pickup").andExpect(status().isOk());
        courierAct(courierToken, inProgress, "complete").andExpect(status().isOk());
        assertThat(orderStatus(inProgress)).isEqualTo("DELIVERED");

        act(adminToken, "/api/admin/restaurants/" + restaurantId + "/activate").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.operationEnabled").value(true));
        getAs(null, "/api/public/restaurants").andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void deactivatedCourierStopsBeingCandidateButKeepsAccessAndItsDeliveryInProgress() throws Exception {
        UUID assigned = readyOrder("k1");
        assertThat(courierOf(assigned)).isEqualTo(courierA.getId());

        act(adminToken, "/api/admin/couriers/" + courierA.getId() + "/deactivate").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.operationEnabled").value(false))
                .andExpect(jsonPath("$.account.accessEnabled").value(true));

        loginAndGetToken("repartidor-a@mail.com");
        courierAct(courierToken, assigned, "pickup").andExpect(status().isOk());
        courierAct(courierToken, assigned, "complete").andExpect(status().isOk());
        assertThat(orderStatus(assigned)).isEqualTo("DELIVERED");

        UUID waiting = readyOrder("k2");
        assertThat(courierOf(waiting)).isNull();
        adminAssign(adminToken, waiting, courierA.getId()).andExpect(status().is4xxClientError());
        getAs(adminToken, ADMIN_DELIVERIES + "/pending").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidates").isEmpty());

        act(adminToken, "/api/admin/couriers/" + courierA.getId() + "/activate").andExpect(status().isOk());
        adminAssign(adminToken, waiting, courierA.getId()).andExpect(status().isOk());
        assertThat(courierOf(waiting)).isEqualTo(courierA.getId());
    }

    @Test
    void repeatingAChangeAlreadyAppliedRespondsOkAndRecordsNothingNew() throws Exception {
        act(adminToken, "/api/admin/couriers/" + courierA.getId() + "/deactivate").andExpect(status().isOk());
        act(adminToken, "/api/admin/couriers/" + courierA.getId() + "/deactivate").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.operationEnabled").value(false))
                .andExpect(jsonPath("$.actions.length()").value(1));
        act(adminToken, "/api/admin/users/" + customer.getId() + "/enable").andExpect(status().isOk())
                .andExpect(jsonPath("$.actions").isEmpty());

        assertThat(count("acciones_admin_cuenta")).isEqualTo(1);
    }

    @Test
    void administratorAccountsCannotBeDeactivatedNotEvenTheOwn() throws Exception {
        Account other = admin("admin2@mail.com");

        act(adminToken, "/api/admin/users/" + adminAccount.getId() + "/disable").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_PROTECTED"));
        act(adminToken, "/api/admin/users/" + other.getId() + "/disable").andExpect(status().isConflict());
        act(adminToken, "/api/admin/couriers/" + adminAccount.getId() + "/deactivate")
                .andExpect(status().isNotFound());

        assertThat(count("acciones_admin_cuenta")).isZero();
        getAs(adminToken, "/api/admin/users").andExpect(status().isOk());
    }

    @Test
    void unknownAccountsAreNotFoundOnEveryChange() throws Exception {
        UUID unknown = UUID.randomUUID();
        act(adminToken, "/api/admin/users/" + unknown + "/disable").andExpect(status().isNotFound());
        act(adminToken, "/api/admin/restaurants/" + unknown + "/deactivate").andExpect(status().isNotFound());
        act(adminToken, "/api/admin/couriers/" + unknown + "/deactivate").andExpect(status().isNotFound());
        act(adminToken, "/api/admin/couriers/" + customer.getId() + "/deactivate").andExpect(status().isNotFound());
    }

    @Test
    void simultaneousDisablesProduceOneChangeAndOneRecord() throws Exception {
        for (int round = 0; round < 3; round++) {
            Account target = customer("objetivo" + round + "@mail.com");
            List<Integer> codes = fire(target.getId(), 4);

            assertThat(codes).containsOnly(200);
            assertThat(jdbc.queryForObject("select count(*) from acciones_admin_cuenta where account_id = ?",
                    Integer.class, target.getId())).isEqualTo(1);
        }
    }

    @Test
    void activatingAPendingRestaurantDoesNotApproveOrShowIt() throws Exception {
        UUID pending = TestProducts.approvedRestaurant(jdbc, restaurantOwner("pend@mail.com").getId(), "Pendiente",
                "Criolla", "Ayacucho");
        jdbc.update("update restaurantes set status = 'PENDING' where id = ?", pending);

        act(adminToken, "/api/admin/restaurants/" + pending + "/deactivate").andExpect(status().isOk());
        act(adminToken, "/api/admin/restaurants/" + pending + "/activate").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select status from restaurantes where id = ?", String.class, pending))
                .isEqualTo("PENDING");
        getAs(null, "/api/public/restaurants/" + pending).andExpect(status().isNotFound());
        getAs(null, "/api/public/restaurants").andExpect(jsonPath("$.totalItems").value(1));
    }

    private List<Integer> fire(UUID accountId, int requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    ResultActions result = act(adminToken, "/api/admin/users/" + accountId + "/disable");
                    return result.andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                codes.add(future.get());
            }
            return codes;
        } finally {
            pool.shutdownNow();
        }
    }
}
