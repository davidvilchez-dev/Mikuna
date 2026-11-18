package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.OrderStatusTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-19: el restaurante propietario avanza Aceptado, En preparación, Listo para recoger sin saltos ni retrocesos. */
class OrderProgressIntegrationTest extends OrderStatusTestSupport {

    @Test
    void sinTokenEs401YConRolClienteEs403() throws Exception {
        UUID id = placeOrder("p1");
        act(null, RESTAURANT_ORDERS + "/" + id + "/start-preparation").andExpect(status().isUnauthorized());
        act(token, RESTAURANT_ORDERS + "/" + id + "/mark-ready").andExpect(status().isForbidden());
    }

    @Test
    void laSecuenciaCompletaDejaElPagoCobradoSinLlamadasAdicionalesAStripe() throws Exception {
        UUID id = placeOrder("p2");

        restaurantAct(id, "accept").andExpect(status().isOk());
        restaurantAct(id, "start-preparation").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));
        restaurantAct(id, "mark-ready").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"));

        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
        verify(stripeGateway, times(1)).capture(any(), any());
        verify(stripeGateway, never()).cancel(any(), any());
        list(ownerToken, "?status=READY").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
        list(ownerToken, "?status=PREPARING").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void saltosYRetrocesosDanConflictoYNoCambianNada() throws Exception {
        UUID id = placeOrder("p3");

        restaurantAct(id, "start-preparation").andExpect(status().isConflict());
        restaurantAct(id, "mark-ready").andExpect(status().isConflict());
        restaurantAct(id, "accept").andExpect(status().isOk());
        restaurantAct(id, "mark-ready").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        restaurantAct(id, "start-preparation").andExpect(status().isOk());
        restaurantAct(id, "start-preparation").andExpect(status().isConflict());
        restaurantAct(id, "accept").andExpect(status().isConflict());
        restaurantAct(id, "mark-ready").andExpect(status().isOk());
        restaurantAct(id, "start-preparation").andExpect(status().isConflict());
        restaurantAct(id, "mark-ready").andExpect(status().isConflict());

        assertThat(orderStatus(id)).isEqualTo("READY");
        assertThat(changesOf(id)).hasSize(3);
    }

    @Test
    void avanzarUnPedidoPendienteRechazadoOCanceladoSeRechaza() throws Exception {
        UUID pending = placeOrder("p4a");
        UUID rejected = placeOrder("p4b");
        UUID cancelled = placeOrder("p4c");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        customerCancel(cancelled).andExpect(status().isOk());

        for (UUID id : new UUID[] {pending, rejected, cancelled}) {
            restaurantAct(id, "start-preparation").andExpect(status().isConflict());
            restaurantAct(id, "mark-ready").andExpect(status().isConflict());
        }
        assertThat(orderStatus(pending)).isEqualTo("PENDING");
        assertThat(orderStatus(rejected)).isEqualTo("REJECTED");
        assertThat(orderStatus(cancelled)).isEqualTo("CANCELLED");
    }

    @Test
    void unRestauranteAjenoNoPuedeAvanzarElPedido() throws Exception {
        UUID id = placeOrder("p5");
        restaurantAct(id, "accept").andExpect(status().isOk());
        String other = otherOwnerToken();

        act(other, RESTAURANT_ORDERS + "/" + id + "/start-preparation").andExpect(status().isNotFound());
        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");
    }
}
