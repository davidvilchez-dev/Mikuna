package com.mikuna.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.notification.service.NotificationWriter;
import com.mikuna.backend.support.DeliveryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** RF-29: avisos por cada cambio relevante, sin duplicados, consultables, leíbles y sin bloquear el cambio. */
class NotificationIntegrationTest extends DeliveryTestSupport {

    @MockitoSpyBean
    private NotificationWriter writer;

    private UUID ownerId() {
        return jdbc.queryForObject("select owner_id from restaurantes where id = ?", UUID.class, restaurantId);
    }

    @Test
    void elPedidoNuevoAvisaAlRestauranteYSuConfirmacionRepetidaNoDuplica() throws Exception {
        placeOrder("n-1");
        confirm(token, "n-1", body(TOTAL)).andExpect(status().isOk());

        assertThat(noticeTypes(ownerId())).containsExactly("NEW_ORDER");
        assertThat(noticeTypes(customer.getId())).isEmpty();
        assertThat(unreadCount(ownerToken)).isEqualTo(1);
    }

    @Test
    void aceptarPrepararYListoAvisanAlCliente() throws Exception {
        UUID id = readyOrder("n-2");

        assertThat(noticeTypes(customer.getId())).containsExactly("ACCEPTED", "PREPARING", "READY");
        assertThat(unreadCount(token)).isEqualTo(3);
        assertThat(id).isNotNull();
    }

    @Test
    void recorrerElPedidoHastaEntregadoAvisaACadaParticipanteLoQueCorresponde() throws Exception {
        Account courier = courierAccount("a@mail.com");
        String courierToken = loginAndGetToken("a@mail.com");
        setAvailable(courierToken, true);
        UUID id = readyOrder("n-3");
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());
        courierAct(courierToken, id, "complete").andExpect(status().isOk());

        assertThat(noticeTypes(customer.getId())).containsExactly("ACCEPTED", "PREPARING", "READY", "ASSIGNED",
                "PICKED_UP", "DELIVERED");
        assertThat(noticeTypes(ownerId())).containsExactly("NEW_ORDER", "ASSIGNED", "PICKED_UP", "DELIVERED");
        assertThat(noticeTypes(courier.getId())).containsExactly("ASSIGNED");
    }

    @Test
    void laReasignacionTrasUnRechazoGeneraUnSegundoAvisoDeAsignado() throws Exception {
        Account first = courierAccount("a@mail.com");
        Account second = courierAccount("b@mail.com");
        String firstToken = loginAndGetToken("a@mail.com");
        String secondToken = loginAndGetToken("b@mail.com");
        setAvailable(firstToken, true);
        UUID id = readyOrder("n-4");
        setAvailable(secondToken, true);

        courierAct(firstToken, id, "reject", "{}").andExpect(status().isOk());

        assertThat(courierOf(id)).isEqualTo(second.getId());
        assertThat(noticeTypes(customer.getId()).stream().filter("ASSIGNED"::equals).count()).isEqualTo(2);
        assertThat(noticeTypes(first.getId())).containsExactly("ASSIGNED");
        assertThat(noticeTypes(second.getId())).containsExactly("ASSIGNED");
    }

    @Test
    void cancelarAvisaAlRestauranteYNoAlClienteQueCancela() throws Exception {
        UUID id = placeOrder("n-5");

        customerCancel(id).andExpect(status().isOk());

        assertThat(noticeTypes(ownerId())).containsExactly("NEW_ORDER", "CANCELLED");
        assertThat(noticeTypes(customer.getId())).isEmpty();
    }

    @Test
    void rechazarAvisaAlCliente() throws Exception {
        UUID id = placeOrder("n-6");

        restaurantAct(id, "reject").andExpect(status().isOk());

        assertThat(noticeTypes(customer.getId())).containsExactly("REJECTED");
    }

    @Test
    void laConsultaEsPaginadaOrdenadaYSoloMuestraLosPropios() throws Exception {
        readyOrder("n-7");

        notifications(token, "?size=2").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("READY"))
                .andExpect(jsonPath("$.items[1].type").value("PREPARING"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.totalItems").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        notifications(token, "?size=2&page=1").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("ACCEPTED"));
        notifications(ownerToken, "").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NEW_ORDER"));
        notifications(token, "?size=500").andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void marcarLeidoBajaElContadorYElFiltroDeNoLeidosLoRefleja() throws Exception {
        readyOrder("n-8");
        String body = notifications(token, "").andReturn().getResponse().getContentAsString();
        String firstId = JsonPath.read(body, "$.items[0].id");

        mvc.perform(post("/api/notifications/" + firstId + "/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/notifications/" + firstId + "/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(unreadCount(token)).isEqualTo(2);
        notifications(token, "?unreadOnly=true").andExpect(jsonPath("$.items.length()").value(2));
        notifications(token, "").andExpect(jsonPath("$.items[0].read").value(true));

        mvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(unreadCount(token)).isZero();
        assertThat(unreadCount(ownerToken)).isEqualTo(1);
    }

    @Test
    void losAvisosAjenosSonInaccesibles() throws Exception {
        readyOrder("n-9");
        String body = notifications(token, "").andReturn().getResponse().getContentAsString();
        String customerNotice = JsonPath.read(body, "$.items[0].id");

        mvc.perform(post("/api/notifications/" + customerNotice + "/read").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + UUID.randomUUID() + "/read")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        assertThat(unreadCount(token)).isEqualTo(3);
    }

    @Test
    void sinSesionEs401ElAdministradorEs403YNoExisteBorrado() throws Exception {
        notifications(null, "").andExpect(status().isUnauthorized());
        notifications(adminToken(), "").andExpect(status().isForbidden());
        readyOrder("n-10");
        String body = notifications(token, "").andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.items[0].id");

        mvc.perform(delete("/api/notifications/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
        assertThat(count("avisos")).isEqualTo(4);
    }

    @Test
    void siFallaElGuardadoDelAvisoElCambioDeEstadoIgualSeCompleta() throws Exception {
        UUID id = placeOrder("n-11");
        doThrow(new IllegalStateException("fallo simulado")).when(writer).save(any(), any(), any());

        restaurantAct(id, "accept").andExpect(status().isOk());

        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");
        assertThat(transitions(id)).containsExactly("PENDING→ACCEPTED");
        assertThat(noticeTypes(customer.getId())).isEmpty();
    }
}
