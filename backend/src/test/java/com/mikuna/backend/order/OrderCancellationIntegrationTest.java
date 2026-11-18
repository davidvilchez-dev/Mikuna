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
import com.mikuna.backend.support.OrderStatusTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-20: el cliente cancela solo un pedido propio Pendiente; no hay reembolsos tras la aceptación. */
class OrderCancellationIntegrationTest extends OrderStatusTestSupport {

    @Test
    void sinTokenEs401YConRolRestauranteEs403() throws Exception {
        UUID id = placeOrder("c1");
        act(null, ORDERS + "/" + id + "/cancel").andExpect(status().isUnauthorized());
        act(ownerToken, ORDERS + "/" + id + "/cancel").andExpect(status().isForbidden());
        assertThat(orderStatus(id)).isEqualTo("PENDING");
    }

    @Test
    void cancelarUnPendientePropioLiberaLaReservaSinCobro() throws Exception {
        UUID id = placeOrder("c2");

        act(token, ORDERS + "/" + id + "/cancel", "{\"reason\":\"Me equivoqué\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("RELEASED"));

        assertThat(paymentStatus(id)).isEqualTo("RELEASED");
        verify(stripeGateway, times(1)).cancel("pi_test_1", "release-" + id);
        verify(stripeGateway, never()).capture(any(), any());
        assertThat(changesOf(id)).singleElement().satisfies(row -> {
            assertThat(row.get("actor_role")).isEqualTo("CUSTOMER");
            assertThat(row.get("reason")).isEqualTo("Me equivoqué");
        });
    }

    @Test
    void trasLaAceptacionLaCancelacionSeRechazaSinReembolso() throws Exception {
        UUID id = placeOrder("c3");
        for (String step : new String[] {"accept", "start-preparation", "mark-ready"}) {
            restaurantAct(id, step).andExpect(status().isOk());
            customerCancel(id).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reembolsos")));
        }
        assertThat(orderStatus(id)).isEqualTo("READY");
        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
        verify(stripeGateway, never()).cancel(any(), any());
    }

    @Test
    void cancelarUnPedidoYaRechazadoOCanceladoEs409SinRepetirLaLiberacion() throws Exception {
        UUID rejected = placeOrder("c4a");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        customerCancel(rejected).andExpect(status().isConflict());

        UUID cancelled = placeOrder("c4b");
        customerCancel(cancelled).andExpect(status().isOk());
        customerCancel(cancelled).andExpect(status().isConflict());

        verify(stripeGateway, times(2)).cancel(any(), any());
        assertThat(orderStatus(rejected)).isEqualTo("REJECTED");
    }

    @Test
    void unPedidoDeOtroClienteEsInexistente() throws Exception {
        UUID id = placeOrder("c5");
        customer("otro-cliente@mail.com");
        String other = loginAndGetToken("otro-cliente@mail.com");

        act(other, ORDERS + "/" + id + "/cancel").andExpect(status().isNotFound());
        act(token, ORDERS + "/" + UUID.randomUUID() + "/cancel").andExpect(status().isNotFound());

        assertThat(orderStatus(id)).isEqualTo("PENDING");
        verify(stripeGateway, never()).cancel(any(), any());
    }

    @Test
    void siLaPasarelaFallaAlCancelarElPedidoSigueEnPendiente() throws Exception {
        UUID id = placeOrder("c6");
        doThrow(new PaymentUnavailableException()).when(stripeGateway).cancel(any(), any());

        customerCancel(id).andExpect(status().isServiceUnavailable());

        assertThat(orderStatus(id)).isEqualTo("PENDING");
        assertThat(paymentStatus(id)).isEqualTo("AUTHORIZED");
        assertThat(count("cambios_estado_pedido")).isZero();
    }
}
