package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.HistoryTestSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** US5: cada activación o desactivación efectiva queda registrada con fecha y responsable (FR-017). */
class AdminAuditIntegrationTest extends HistoryTestSupport {

    private Account first;
    private Account second;
    private String firstToken;
    private String secondToken;

    @BeforeEach
    void admins() throws Exception {
        first = admin("admin@mail.com");
        second = admin("admin2@mail.com");
        firstToken = loginAndGetToken("admin@mail.com");
        secondToken = loginAndGetToken("admin2@mail.com");
    }

    @Test
    void theDetailListsEveryEffectiveActionWithDateAndResponsibleAdministratorNewestFirst() throws Exception {
        Instant before = Instant.now().minusSeconds(5);
        String url = "/api/admin/users/" + customer.getId();
        act(firstToken, url + "/disable").andExpect(status().isOk());
        act(firstToken, url + "/disable").andExpect(status().isOk()); // repetido: no se registra
        act(secondToken, url + "/enable").andExpect(status().isOk());
        act(secondToken, url + "/disable").andExpect(status().isOk());

        String body = getAs(firstToken, url).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(3))
                .andExpect(jsonPath("$.actions[0].action").value("DEACTIVATED"))
                .andExpect(jsonPath("$.actions[0].adminId").value(second.getId().toString()))
                .andExpect(jsonPath("$.actions[1].action").value("ACTIVATED"))
                .andExpect(jsonPath("$.actions[1].adminId").value(second.getId().toString()))
                .andExpect(jsonPath("$.actions[2].action").value("DEACTIVATED"))
                .andExpect(jsonPath("$.actions[2].adminId").value(first.getId().toString()))
                .andExpect(jsonPath("$.actions[0].adminName").value("Responsable"))
                .andReturn().getResponse().getContentAsString();

        List<String> dates = JsonPath.read(body, "$.actions[*].createdAt");
        assertThat(dates).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(Instant.parse(dates.get(2))).isAfter(before);
        assertThat(count("acciones_admin_cuenta")).isEqualTo(3);
    }

    @Test
    void operationActionsAppearInTheRestaurantAndCourierDetailButNotAccessOnes() throws Exception {
        java.util.UUID ownerId = accounts.findByEmail("local@mail.com").orElseThrow().getId();
        act(firstToken, "/api/admin/restaurants/" + restaurantId + "/deactivate").andExpect(status().isOk());
        act(secondToken, "/api/admin/users/" + ownerId + "/disable").andExpect(status().isOk());
        act(secondToken, "/api/admin/couriers/" + courierA.getId() + "/deactivate").andExpect(status().isOk());

        getAs(firstToken, "/api/admin/restaurants/" + restaurantId).andExpect(jsonPath("$.actions.length()").value(1))
                .andExpect(jsonPath("$.actions[0].scope").value("OPERATION"))
                .andExpect(jsonPath("$.actions[0].adminId").value(first.getId().toString()));
        getAs(firstToken, "/api/admin/users/" + ownerId).andExpect(jsonPath("$.actions.length()").value(2));
        getAs(firstToken, "/api/admin/couriers/" + courierA.getId())
                .andExpect(jsonPath("$.actions[0].adminId").value(second.getId().toString()));
    }

    @Test
    void theRecordIsImmutable() throws Exception {
        act(firstToken, "/api/admin/couriers/" + courierA.getId() + "/deactivate").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from acciones_admin_cuenta where admin_id = ? "
                + "and scope = 'OPERATION' and action = 'DEACTIVATED'", Integer.class, first.getId())).isEqualTo(1);
        // La API no ofrece ninguna operación para editar o borrar el registro.
        for (String method : List.of("PUT", "PATCH", "DELETE")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .request(org.springframework.http.HttpMethod.valueOf(method),
                                    "/api/admin/couriers/" + courierA.getId() + "/actions")
                            .header("Authorization", "Bearer " + firstToken))
                    .andExpect(status().is4xxClientError());
        }
        assertThat(count("acciones_admin_cuenta")).isEqualTo(1);
    }
}
