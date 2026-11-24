package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.order.service.OrderExpiryBackfill;
import com.mikuna.backend.order.service.OrderExpiryPolicy;
import com.mikuna.backend.order.service.PendingOrderExpirySweeper;
import com.mikuna.backend.support.OrderStatusTestSupport;
import com.mikuna.backend.support.TestOrders;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 012: vencimiento de pedidos Pendientes sin respuesta del restaurante. */
class PendingOrderExpiryIntegrationTest extends OrderStatusTestSupport {

    @Autowired
    private PendingOrderExpirySweeper sweeper;

    @Autowired
    private OrderExpiryBackfill backfill;

    private void expireNow(UUID orderId) {
        jdbc.update("update pedidos set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), orderId);
    }

    private UUID placeCashOrder(String key) throws Exception {
        jdbc.update("update carritos set restaurant_id = ?, selected_address_id = ? where id = ?", restaurantId,
                addressId, cartId);
        jdbc.update("delete from lineas_carrito where cart_id = ?", cartId);
        TestOrders.line(jdbc, cartId, lomo, 2);
        String json = confirm(token, key, "{\"paymentMethod\":\"CASH\",\"expectedTotal\":" + TOTAL + "}")
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private String noticesFor(UUID orderId) {
        return String.join("|", jdbc.queryForList(
                "select message from avisos where order_id = ? and type = 'CANCELLED' order by message",
                String.class, orderId));
    }

    @Test
    void elPedidoNaceConVencimientoYLasRespuestasMuestranElTiempoRestante() throws Exception {
        UUID id = placeOrder("e1");

        Timestamp created = jdbc.queryForObject("select created_at from pedidos where id = ?", Timestamp.class, id);
        Timestamp expires = jdbc.queryForObject("select expires_at from pedidos where id = ?", Timestamp.class, id);
        assertThat(Duration.between(created.toInstant(), expires.toInstant())).isEqualTo(Duration.ofMinutes(15));

        mvc.perform(get(ORDERS + "/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.secondsToExpire", Matchers.both(Matchers.greaterThan(880))
                        .and(Matchers.lessThanOrEqualTo(900))));
        list(ownerToken, "?status=PENDING").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].secondsToExpire").isNumber());

        restaurantAct(id, "accept").andExpect(status().isOk()).andExpect(jsonPath("$.secondsToExpire").isEmpty());
    }

    @Test
    void unPedidoConTarjetaVencidoSeCancelaPorElSistemaYLiberaLaReserva() throws Exception {
        UUID id = placeOrder("e2");
        expireNow(id);

        assertThat(sweeper.sweep()).isEqualTo(1);

        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("RELEASED");
        verify(stripeGateway, times(1)).cancel("pi_test_1", "release-" + id);
        verify(stripeGateway, never()).capture(any(), any());
        assertThat(changesOf(id)).singleElement().satisfies(row -> {
            assertThat(row.get("actor_role")).isEqualTo("SYSTEM");
            assertThat(row.get("actor_id")).isNull();
            assertThat(row.get("reason")).isEqualTo(OrderExpiryPolicy.REASON);
        });
        assertThat(noticesFor(id)).contains("el restaurante no respondió a tiempo")
                .contains("porque no se respondió a tiempo");

        assertThat(sweeper.sweep()).as("un segundo barrido no hace nada").isZero();
        verify(stripeGateway, times(1)).cancel(any(), any());
    }

    @Test
    void unPedidoEnEfectivoVencidoSeCancelaSinLlamarALaPasarela() throws Exception {
        UUID id = placeCashOrder("e3");
        reset(stripeGateway);
        expireNow(id);

        sweeper.sweep();

        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("RELEASED");
        verify(stripeGateway, never()).cancel(any(), any());
    }

    @Test
    void unPedidoQueNoVencioOYaSeDecidioNoCambia() throws Exception {
        UUID vigente = placeOrder("e4a");
        UUID aceptado = placeOrder("e4b");
        UUID cancelado = placeOrder("e4c");
        restaurantAct(aceptado, "accept").andExpect(status().isOk());
        customerCancel(cancelado).andExpect(status().isOk());
        expireNow(aceptado);
        expireNow(cancelado);

        assertThat(sweeper.sweep()).isZero();

        assertThat(orderStatus(vigente)).isEqualTo("PENDING");
        assertThat(orderStatus(aceptado)).isEqualTo("ACCEPTED");
        assertThat(paymentStatus(aceptado)).isEqualTo("CAPTURED");
        assertThat(changesOf(cancelado)).hasSize(1);
        verify(stripeGateway, times(1)).cancel(any(), any());
    }

    @Test
    void aceptarORechazarUnPedidoVencidoNoBarridoEs409YQuedaCancelado() throws Exception {
        UUID aceptar = placeOrder("e5a");
        UUID rechazar = placeOrder("e5b");
        expireNow(aceptar);
        expireNow(rechazar);

        restaurantAct(aceptar, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"))
                .andExpect(jsonPath("$.detail").value("El pedido venció porque no se respondió a tiempo"));
        restaurantAct(rechazar, "reject").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));

        for (UUID id : new UUID[] {aceptar, rechazar}) {
            assertThat(orderStatus(id)).isEqualTo("CANCELLED");
            assertThat(paymentStatus(id)).isEqualTo("RELEASED");
            assertThat(changesOf(id)).singleElement()
                    .satisfies(row -> assertThat(row.get("actor_role")).isEqualTo("SYSTEM"));
        }
        verify(stripeGateway, never()).capture(any(), any());
    }

    @Test
    void decidirSobreUnPedidoYaVencidoEs409SinSegundaLiberacion() throws Exception {
        UUID id = placeOrder("e6");
        expireNow(id);
        sweeper.sweep();

        restaurantAct(id, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));
        restaurantAct(id, "reject").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));

        assertThat(changesOf(id)).hasSize(1);
        verify(stripeGateway, times(1)).cancel(any(), any());
    }

    @Test
    void siLaPasarelaFallaElPedidoSiguePendienteYElSiguienteBarridoLoVence() throws Exception {
        UUID id = placeOrder("e7");
        expireNow(id);
        doThrow(new PaymentUnavailableException()).when(stripeGateway).cancel(any(), any());

        assertThat(sweeper.sweep()).isZero();
        assertThat(orderStatus(id)).isEqualTo("PENDING");
        assertThat(paymentStatus(id)).isEqualTo("AUTHORIZED");

        reset(stripeGateway);
        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("RELEASED");
    }

    @Test
    void alArrancarSeRellenaElVencimientoDeLosPedidosAnterioresYLosAntiguosVencen() throws Exception {
        UUID antiguo = placeOrder("e8a");
        UUID reciente = placeOrder("e8b");
        Instant hace1h = Instant.now().minus(Duration.ofHours(1));
        jdbc.update("update pedidos set expires_at = null, created_at = ? where id = ?", Timestamp.from(hace1h),
                antiguo);
        jdbc.update("update pedidos set expires_at = null where id = ?", reciente);

        assertThat(backfill.backfill()).isEqualTo(2);
        sweeper.sweep();

        assertThat(orderStatus(antiguo)).isEqualTo("CANCELLED");
        assertThat(orderStatus(reciente)).isEqualTo("PENDING");
        Timestamp created = jdbc.queryForObject("select created_at from pedidos where id = ?", Timestamp.class,
                reciente);
        Timestamp expires = jdbc.queryForObject("select expires_at from pedidos where id = ?", Timestamp.class,
                reciente);
        assertThat(Duration.between(created.toInstant(), expires.toInstant())).isEqualTo(Duration.ofMinutes(15));
    }
}
