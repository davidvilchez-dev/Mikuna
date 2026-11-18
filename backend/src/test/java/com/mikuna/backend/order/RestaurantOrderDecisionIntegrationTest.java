package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.common.exception.ReservationExpiredException;
import com.mikuna.backend.support.OrderStatusTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-18, RF-04 y RNF-07: el restaurante propietario acepta (cobro) o rechaza (liberación) pedidos pendientes. */
class RestaurantOrderDecisionIntegrationTest extends OrderStatusTestSupport {

    @Test
    void sinTokenEs401YConRolClienteOCuentaDeshabilitadaEs403() throws Exception {
        UUID id = placeOrder("d1");
        act(null, RESTAURANT_ORDERS + "/" + id + "/accept").andExpect(status().isUnauthorized());
        list(token, "").andExpect(status().isForbidden());
        act(token, RESTAURANT_ORDERS + "/" + id + "/reject").andExpect(status().isForbidden());

        disable(accounts.findByEmail("local@mail.com").orElseThrow().getId());
        restaurantAct(id, "accept").andExpect(status().isForbidden());
        assertThat(orderStatus(id)).isEqualTo("PENDING");
        verify(stripeGateway, never()).capture(any(), any());
    }

    @Test
    void listaSoloLosPedidosPendientesDelRestaurantePropio() throws Exception {
        UUID first = placeOrder("d2a");
        UUID second = placeOrder("d2b");
        restaurantAct(second, "reject").andExpect(status().isOk());
        String other = otherOwnerToken();

        list(ownerToken, "").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].payment.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$[0].lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$[0].totals.total").value(52.25));
        list(ownerToken, "?status=REJECTED").andExpect(status().isBadRequest());
        list(ownerToken, "?status=NOPE").andExpect(status().isBadRequest());
        list(other, "").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aceptarCobraElMontoReservadoUnaSolaVez() throws Exception {
        UUID id = placeOrder("d3");

        restaurantAct(id, "accept").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"));

        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");
        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
        verify(stripeGateway, times(1)).capture("pi_test_1", "capture-" + id);
        verify(stripeGateway, never()).cancel(any(), any());
        assertThat(jdbc.queryForObject("select count(*) from transacciones_pago where order_id = ? "
                + "and type = 'CAPTURE' and status = 'CAPTURED' and amount = 52.25", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void rechazarLiberaLaReservaSinCobroYGuardaElMotivo() throws Exception {
        UUID id = placeOrder("d4");

        act(ownerToken, RESTAURANT_ORDERS + "/" + id + "/reject", "{\"reason\":\"Sin ingredientes\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.payment.status").value("RELEASED"));

        assertThat(paymentStatus(id)).isEqualTo("RELEASED");
        verify(stripeGateway, times(1)).cancel("pi_test_1", "release-" + id);
        verify(stripeGateway, never()).capture(any(), any());
        assertThat(changesOf(id)).singleElement().satisfies(row -> assertThat(row.get("reason"))
                .isEqualTo("Sin ingredientes"));
    }

    @Test
    void unPedidoDeOtroRestauranteEsInexistenteYNoCambiaNiCobra() throws Exception {
        UUID id = placeOrder("d5");
        String other = otherOwnerToken();

        act(other, RESTAURANT_ORDERS + "/" + id + "/accept").andExpect(status().isNotFound());
        act(other, RESTAURANT_ORDERS + "/" + id + "/reject").andExpect(status().isNotFound());
        act(ownerToken, RESTAURANT_ORDERS + "/" + UUID.randomUUID() + "/accept").andExpect(status().isNotFound());

        assertThat(orderStatus(id)).isEqualTo("PENDING");
        assertThat(paymentStatus(id)).isEqualTo("AUTHORIZED");
        assertThat(count("cambios_estado_pedido")).isZero();
        verify(stripeGateway, never()).capture(any(), any());
        verify(stripeGateway, never()).cancel(any(), any());
    }

    @Test
    void decidirDeNuevoUnPedidoYaDecididoEs409SinSegundaLlamadaAStripe() throws Exception {
        UUID id = placeOrder("d6");
        restaurantAct(id, "accept").andExpect(status().isOk());

        restaurantAct(id, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        restaurantAct(id, "reject").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        verify(stripeGateway, times(1)).capture(any(), any());
        verify(stripeGateway, never()).cancel(any(), any());
        assertThat(changesOf(id)).hasSize(1);
    }

    @Test
    void unMotivoDeMasDe200CaracteresOCamposDesconocidosSonInvalidos() throws Exception {
        UUID id = placeOrder("d7");
        act(ownerToken, RESTAURANT_ORDERS + "/" + id + "/reject", "{\"reason\":\"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest());
        act(ownerToken, RESTAURANT_ORDERS + "/" + id + "/reject", "{\"reason\":\"ok\",\"status\":\"ACCEPTED\"}")
                .andExpect(status().isBadRequest());
        assertThat(orderStatus(id)).isEqualTo("PENDING");
    }

    @Test
    void conUnaReservaVencidaElPedidoQuedaCanceladoSinCobroYSeResponde409() throws Exception {
        UUID id = placeOrder("d8");
        doThrow(new ReservationExpiredException()).when(stripeGateway).capture(any(), any());

        restaurantAct(id, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));

        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("RELEASED");
        assertThat(changesOf(id)).singleElement().satisfies(row -> {
            assertThat(row.get("actor_role")).isEqualTo("SYSTEM");
            assertThat(row.get("actor_id")).isNull();
            assertThat(row.get("reason")).isEqualTo("reserva vencida");
        });
        assertThat(jdbc.queryForObject("select count(*) from transacciones_pago where order_id = ? "
                + "and type = 'RELEASE' and result_code = 'reservation_expired'", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void siLaPasarelaCaeAlAceptarOAlRechazarElPedidoSigueEnPendienteYSePuedeReintentar() throws Exception {
        UUID id = placeOrder("d9");
        doThrow(new PaymentUnavailableException()).when(stripeGateway).capture(any(), any());
        doThrow(new PaymentUnavailableException()).when(stripeGateway).cancel(any(), any());

        restaurantAct(id, "accept").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"));
        restaurantAct(id, "reject").andExpect(status().isServiceUnavailable());

        assertThat(orderStatus(id)).isEqualTo("PENDING");
        assertThat(paymentStatus(id)).isEqualTo("AUTHORIZED");
        assertThat(count("cambios_estado_pedido")).isZero();

        org.mockito.Mockito.reset(stripeGateway);
        restaurantAct(id, "accept").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
    }

    @Test
    void unRestauranteYaNoAprobadoNoPuedeDecidir() throws Exception {
        UUID id = placeOrder("d10");
        jdbc.update("update restaurantes set status = 'REJECTED' where id = ?", restaurantId);

        restaurantAct(id, "accept").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_ENABLED"));
        list(ownerToken, "").andExpect(status().isForbidden());
        assertThat(orderStatus(id)).isEqualTo("PENDING");
    }
}
