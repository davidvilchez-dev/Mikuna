package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.common.exception.RefundFailedException;
import com.mikuna.backend.support.DeliveryTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Spec 014: cancelación administrativa con reembolso, fallos de la pasarela, repartidor liberado, avisos y vistas. */
class AdminOrderCancelIntegrationTest extends DeliveryTestSupport {

    private static final String ADMIN_ORDERS = "/api/admin/orders";
    private static final String REASON = "El restaurante cerró por un corte de luz";

    private String adminToken;

    @BeforeEach
    void admin() throws Exception {
        adminToken = adminToken();
    }

    private ResultActions cancel(String bearer, UUID orderId, String reason) throws Exception {
        String json = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        return act(bearer, ADMIN_ORDERS + "/" + orderId + "/cancel", json);
    }

    private UUID acceptedOrder(String key) throws Exception {
        UUID id = placeOrder(key);
        restaurantAct(id, "accept").andExpect(status().isOk());
        return id;
    }

    private UUID cashOrder(String key) throws Exception {
        jdbc.update("update carritos set restaurant_id = ?, selected_address_id = ? where id = ?", restaurantId,
                addressId, cartId);
        jdbc.update("delete from lineas_carrito where cart_id = ?", cartId);
        com.mikuna.backend.support.TestOrders.line(jdbc, cartId, lomo, 2);
        String json = confirm(token, key, "{\"paymentMethod\":\"CASH\",\"expectedTotal\":" + TOTAL + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private List<Map<String, Object>> transactionsOf(UUID orderId, String type) {
        return jdbc.queryForList("select type, status, amount, result_code from transacciones_pago "
                + "where order_id = ? and type = ? order by created_at", orderId, type);
    }

    @Test
    void cancelaConReembolsoDesdeCadaEstadoAceptadoYNoEntregado() throws Exception {
        UUID accepted = acceptedOrder("k-acc");
        UUID preparing = acceptedOrder("k-prep");
        restaurantAct(preparing, "start-preparation").andExpect(status().isOk());
        UUID ready = readyOrder("k-ready");
        assertThat(orderStatus(ready)).isEqualTo("READY");
        for (UUID id : List.of(accepted, preparing, ready)) {
            assertCancelledWithRefund(id);
        }

        // Con repartidor: Asignado y Recogido.
        Account courier = courierAccount("rep@mail.com");
        String courierToken = loginAndGetToken("rep@mail.com");
        setAvailable(courierToken, true);
        UUID assigned = readyOrder("k-asig");
        assertThat(orderStatus(assigned)).isEqualTo("ASSIGNED");
        assertCancelledWithRefund(assigned);
        UUID pickedUp = readyOrder("k-recog");
        courierAct(courierToken, pickedUp, "pickup").andExpect(status().isOk());
        UUID waiting = readyOrder("k-espera");
        assertThat(orderStatus(waiting)).as("repartidor ocupado").isEqualTo("READY");
        assertCancelledWithRefund(pickedUp);

        // El repartidor del pedido Recogido quedó libre: la entrega pendiente se le asignó.
        assertThat(courierOf(waiting)).isEqualTo(courier.getId());
        assertThat(orderStatus(waiting)).isEqualTo("ASSIGNED");
    }

    private void assertCancelledWithRefund(UUID id) throws Exception {
        String from = orderStatus(id);
        cancel(adminToken, id, REASON).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.transactions[-1].type").value("REFUND"))
                .andExpect(jsonPath("$.transactions[-1].status").value("REFUNDED"));
        assertThat(orderStatus(id)).as(from).isEqualTo("CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("REFUNDED");
        List<Map<String, Object>> refunds = transactionsOf(id, "REFUND");
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).get("status")).isEqualTo("REFUNDED");
        assertThat(refunds.get(0).get("amount").toString()).isEqualTo(TOTAL);
        Map<String, Object> last = changesOf(id).getLast();
        assertThat(last.get("from_status")).isEqualTo(from);
        assertThat(last.get("to_status")).isEqualTo("CANCELLED");
        assertThat(last.get("actor_role")).isEqualTo("ADMIN");
        assertThat(last.get("reason")).isEqualTo(REASON);
        verify(stripeGateway).refund("pi_test_1", "refund-" + id);
    }

    @Test
    void sinMotivoOConUnoDemasiadoLargoNoCambiaNada() throws Exception {
        UUID id = acceptedOrder("k-1");

        cancel(adminToken, id, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
        cancel(adminToken, id, "   ").andExpect(status().isBadRequest());
        cancel(adminToken, id, "x".repeat(201)).andExpect(status().isBadRequest());

        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");
        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
        verify(stripeGateway, never()).refund(anyString(), anyString());
    }

    @Test
    void losEstadosNoCancelablesSeRechazanCon409() throws Exception {
        UUID pending = placeOrder("k-pend");
        UUID rejected = placeOrder("k-rej");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        UUID cancelled = placeOrder("k-canc");
        customerCancel(cancelled).andExpect(status().isOk());

        for (UUID id : List.of(pending, rejected, cancelled)) {
            String before = orderStatus(id);
            cancel(adminToken, id, REASON).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
            assertThat(orderStatus(id)).isEqualTo(before);
        }
        verify(stripeGateway, never()).refund(anyString(), anyString());
    }

    @Test
    void unPedidoEntregadoNoSeCancela() throws Exception {
        courierAccount("rep@mail.com");
        String courierToken = loginAndGetToken("rep@mail.com");
        setAvailable(courierToken, true);
        UUID id = readyOrder("k-ent");
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());
        courierAct(courierToken, id, "complete").andExpect(status().isOk());

        cancel(adminToken, id, REASON).andExpect(status().isConflict());

        assertThat(orderStatus(id)).isEqualTo("DELIVERED");
        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
    }

    @Test
    void soloElAdministradorCancelaYUnPedidoInexistenteEs404() throws Exception {
        UUID id = acceptedOrder("k-rol");

        cancel(token, id, REASON).andExpect(status().isForbidden());
        cancel(ownerToken, id, REASON).andExpect(status().isForbidden());
        cancel(null, id, REASON).andExpect(status().isUnauthorized());
        cancel(adminToken, UUID.randomUUID(), REASON).andExpect(status().isNotFound());

        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void siLaPasarelaFallaElPedidoNoCambiaYElReintentoReembolsaUnaSolaVez() throws Exception {
        UUID id = acceptedOrder("k-falla");
        doThrow(new PaymentUnavailableException()).doThrow(new RefundFailedException()).doNothing()
                .when(stripeGateway).refund(anyString(), anyString());

        cancel(adminToken, id, REASON).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("REFUND_FAILED"));
        cancel(adminToken, id, REASON).andExpect(status().isServiceUnavailable());

        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");
        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
        assertThat(transactionsOf(id, "REFUND")).extracting(row -> row.get("status"))
                .containsExactly("FAILED", "FAILED");
        assertThat(transactionsOf(id, "REFUND")).extracting(row -> row.get("result_code"))
                .containsExactly("gateway_unavailable", "refund_failed");
        assertThat(changesOf(id).getLast().get("to_status")).isEqualTo("ACCEPTED");

        cancel(adminToken, id, REASON).andExpect(status().isOk());

        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("REFUNDED");
        assertThat(transactionsOf(id, "REFUND")).extracting(row -> row.get("status"))
                .containsExactly("FAILED", "FAILED", "REFUNDED");
        verify(stripeGateway, times(3)).refund("pi_test_1", "refund-" + id);
        cancel(adminToken, id, REASON).andExpect(status().isConflict());
        verify(stripeGateway, times(3)).refund(anyString(), anyString());
    }

    @Test
    void elEfectivoCanceladoQuedaSinCobroYSinPasarela() throws Exception {
        UUID id = cashOrder("k-cash");
        restaurantAct(id, "accept").andExpect(status().isOk());
        assertThat(paymentStatus(id)).isEqualTo("AUTHORIZED");

        cancel(adminToken, id, REASON).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("RELEASED"));

        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(transactionsOf(id, "RELEASE")).extracting(row -> row.get("result_code")).containsExactly("cash");
        verify(stripeGateway, never()).refund(anyString(), anyString());
    }

    @Test
    void elRepartidorQuedaLibreYTodosRecibenUnAvisoConElMotivo() throws Exception {
        Account courier = courierAccount("rep@mail.com");
        String courierToken = loginAndGetToken("rep@mail.com");
        setAvailable(courierToken, true);
        UUID id = readyOrder("k-aviso");
        assertThat(orderStatus(id)).isEqualTo("ASSIGNED");
        UUID ownerId = accounts.findByEmail("local@mail.com").orElseThrow().getId();
        jdbc.update("delete from avisos");

        cancel(adminToken, id, REASON).andExpect(status().isOk())
                .andExpect(jsonPath("$.courier.id").value(courier.getId().toString()));

        getAs(courierToken, COURIER + "/deliveries").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(noticeTypes(customer.getId())).containsExactly("CANCELLED");
        assertThat(noticeTypes(ownerId)).containsExactly("CANCELLED");
        assertThat(noticeTypes(courier.getId())).containsExactly("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from avisos", Integer.class)).isEqualTo(3);
        String customerMessage = jdbc.queryForObject("select message from avisos where recipient_id = ?",
                String.class, customer.getId());
        assertThat(customerMessage).contains(REASON).contains("Te devolvimos el pago");
        // Una entrega Lista posterior se le asigna: el repartidor volvió a estar libre.
        UUID next = readyOrder("k-sig");
        assertThat(courierOf(next)).isEqualTo(courier.getId());
    }

    @Test
    void unPedidoListoSinRepartidorSaleDeLasPendientes() throws Exception {
        UUID id = readyOrder("k-pend-ent");
        getAs(adminToken, ADMIN_DELIVERIES + "/pending").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        cancel(adminToken, id, REASON).andExpect(status().isOk());

        getAs(adminToken, ADMIN_DELIVERIES + "/pending").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elClienteVeLaCancelacionYElReembolsoEnSuPedidoYSuHistorial() throws Exception {
        UUID id = acceptedOrder("k-vista");

        cancel(adminToken, id, REASON).andExpect(status().isOk());

        getAs(token, ORDERS + "/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.cancellation.by").value("ADMIN"))
                .andExpect(jsonPath("$.cancellation.reason").value(REASON));
        getAs(token, ORDERS + "/history").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].payment.status").value("REFUNDED"));
        getAs(adminToken, ADMIN_ORDERS + "/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].type").value("AUTHORIZATION"))
                .andExpect(jsonPath("$.transactions[1].type").value("CAPTURE"))
                .andExpect(jsonPath("$.transactions[2].type").value("REFUND"))
                .andExpect(jsonPath("$.transactions[2].amount").value(52.25))
                .andExpect(jsonPath("$.statusChanges[-1].reason").value(REASON));
    }

    @Test
    void unPedidoNoCanceladoNoTraeCancelacion() throws Exception {
        UUID id = acceptedOrder("k-sin");

        getAs(token, ORDERS + "/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation").doesNotExist());
    }
}
