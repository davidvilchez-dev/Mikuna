package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.payment.service.StripeGateway.Authorization;
import com.mikuna.backend.payment.service.StripeGateway.Outcome;
import com.mikuna.backend.support.OrderTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** RF-14 y RF-16: fallos de la pasarela sin pedido ni reserva, carrito intacto y compensación. */
class OrderPaymentFailureIntegrationTest extends OrderTestSupport {

    private void assertNoOrderAndCartKept() {
        assertThat(count("pedidos")).isZero();
        assertThat(count("pagos")).isZero();
        assertThat(count("lineas_carrito")).as("el carrito se conserva").isEqualTo(1);
    }

    private List<Map<String, Object>> transactions() {
        return jdbc.queryForList("select order_id, type, status, result_code from transacciones_pago "
                + "order by created_at");
    }

    @Test
    void autorizacionRechazadaNoCreaPedidoYRegistraElFallo() throws Exception {
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any()))
                .thenReturn(new Authorization(Outcome.DECLINED, null, "insufficient_funds", "rechazada"));

        confirm(token, "f1", body(TOTAL)).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("tarjeta")));

        assertNoOrderAndCartKept();
        List<Map<String, Object>> rows = transactions();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("type", "AUTHORIZATION").containsEntry("status", "FAILED")
                .containsEntry("result_code", "insufficient_funds");
        assertThat(rows.get(0).get("order_id")).as("sin pedido, sin order_id").isNull();
        verify(stripeGateway, never()).cancel(any(), any());
    }

    @Test
    void tarjetaQueExigeVerificacionAdicionalSeTrataComoFalloYSeLiberaElIntento() throws Exception {
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any()))
                .thenReturn(new Authorization(Outcome.REQUIRES_ACTION, "pi_3ds", "requires_action", "3ds"));

        confirm(token, "f2", body(TOTAL)).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_ACTION_REQUIRED"));

        assertNoOrderAndCartKept();
        verify(stripeGateway).cancel(eq("pi_3ds"), any());
        assertThat(transactions()).extracting(row -> row.get("status")).contains("FAILED");
    }

    @Test
    void pasarelaNoDisponibleReintentaUnaVezYNoCreaPedidoNiReserva() throws Exception {
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any()))
                .thenThrow(new PaymentUnavailableException());

        confirm(token, "f3", body(TOTAL)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no se hizo ningún cobro")));

        assertNoOrderAndCartKept();
        verify(stripeGateway, times(2)).authorize(anyLong(), any(), any(), any(), any());
        assertThat(transactions()).extracting(row -> row.get("status")).containsExactly("FAILED");
    }

    @Test
    void siElPrimerIntentoSiLlegoAStripeElReintentoLoLiberaYLaConfirmacionFalla() throws Exception {
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any()))
                .thenThrow(new PaymentUnavailableException())
                .thenReturn(new Authorization(Outcome.REQUIRES_CAPTURE, "pi_tardio", null, null));

        confirm(token, "f4", body(TOTAL)).andExpect(status().isServiceUnavailable());

        assertNoOrderAndCartKept();
        verify(stripeGateway).cancel(eq("pi_tardio"), any());
    }

    @Test
    void siFallaElGuardadoTrasAutorizarSeLiberaLaReservaYElCarritoQuedaIntacto() throws Exception {
        // Tras autorizar, la dirección desaparece y el guardado del pedido falla dentro de la transacción.
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any())).thenAnswer(invocation -> {
            jdbc.update("delete from direcciones");
            return new Authorization(Outcome.REQUIRES_CAPTURE, "pi_huerfano", null, null);
        });

        confirm(token, "f5", body(TOTAL)).andExpect(status().isNotFound());

        assertNoOrderAndCartKept();
        verify(stripeGateway).cancel(eq("pi_huerfano"), any());
        List<Map<String, Object>> rows = transactions();
        assertThat(rows).extracting(row -> row.get("type")).contains("RELEASE");
        assertThat(rows).extracting(row -> row.get("status")).doesNotContain("AUTHORIZED");
    }

    @Test
    void losMensajesDeErrorNoExponenDatosDeTarjetaNiLaClaveSecreta() throws Exception {
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any()))
                .thenReturn(new Authorization(Outcome.DECLINED, null, "card_declined", "rechazada"));
        confirm(token, "f6", body(TOTAL)).andExpect(status().isPaymentRequired())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk_test"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("pm_card"))));
    }
}
