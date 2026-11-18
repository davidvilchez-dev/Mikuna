package com.mikuna.backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.HistoryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-31: una única calificación de 1 a 5 para un pedido propio entregado. */
class RatingIntegrationTest extends HistoryTestSupport {

    @Test
    void calificaUnPedidoEntregadoYGuardaClienteValorYFecha() throws Exception {
        UUID id = deliveredOrder("r-1");

        rate(token, id, "{\"value\":4}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(id.toString()))
                .andExpect(jsonPath("$.value").value(4))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(ratingRows(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select customer_id from calificaciones where order_id = ?", UUID.class, id))
                .isEqualTo(customer.getId());
        assertThat(jdbc.queryForObject("select created_at from calificaciones where order_id = ?",
                java.sql.Timestamp.class, id)).isNotNull();
    }

    @Test
    void aceptaLosExtremosUnoYCinco() throws Exception {
        UUID first = deliveredOrder("r-2");
        UUID second = deliveredOrder("r-3");

        rate(token, first, "{\"value\":1}").andExpect(status().isCreated());
        rate(token, second, "{\"value\":5}").andExpect(status().isCreated());
    }

    @Test
    void unSegundoIntentoEsConflictoYLaOriginalNoCambia() throws Exception {
        UUID id = deliveredOrder("r-4");
        rate(token, id, "{\"value\":2}").andExpect(status().isCreated());

        rate(token, id, "{\"value\":5}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATING_ALREADY_EXISTS"));
        rate(token, id, "{\"value\":2}").andExpect(status().isConflict());

        assertThat(ratingRows(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select value from calificaciones where order_id = ?", Integer.class, id))
                .isEqualTo(2);
    }

    @Test
    void unPedidoNoEntregadoSeRechaza() throws Exception {
        UUID pending = placeOrder("r-5");
        UUID rejected = placeOrder("r-6");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        UUID cancelled = placeOrder("r-7");
        customerCancel(cancelled).andExpect(status().isOk());
        UUID ready = readyOrder("r-8");
        for (UUID id : new UUID[] {pending, rejected, cancelled, ready}) {
            rate(token, id, "{\"value\":3}").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_DELIVERED"));
            assertThat(ratingRows(id)).isZero();
        }
    }

    @Test
    void unPedidoAjenoOInexistenteDaElMismoNoEncontrado() throws Exception {
        UUID id = deliveredOrder("r-9");
        customer("otro@mail.com");
        String other = loginAndGetToken("otro@mail.com");

        String foreign = rate(other, id, "{\"value\":3}").andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();
        String missing = rate(other, UUID.randomUUID(), "{\"value\":3}").andExpect(status().isNotFound()).andReturn()
                .getResponse().getContentAsString();

        // Solo difiere «instance» (la ruta con el id consultado): el resto de la respuesta es idéntico.
        assertThat(foreign.replace(id.toString(), "ID")).isEqualTo(missing.replaceAll("[0-9a-f-]{36}", "ID"));
        assertThat(ratingRows(id)).isZero();
    }

    @Test
    void losValoresInvalidosSeRechazanSinRegistrarNada() throws Exception {
        UUID id = deliveredOrder("r-10");
        for (String json : new String[] {"{\"value\":0}", "{\"value\":6}", "{\"value\":-1}", "{\"value\":3.5}",
                "{\"value\":\"abc\"}", "{}", "{\"value\":null}", "", "{\"value\":[3]}"}) {
            rate(token, id, json).andExpect(status().isBadRequest());
        }
        assertThat(ratingRows(id)).isZero();
        rate(token, id, "{\"value\":3}").andExpect(status().isCreated());
    }

    @Test
    void sinTokenEs401ConOtroRolEs403() throws Exception {
        UUID id = deliveredOrder("r-11");

        rate(null, id, "{\"value\":3}").andExpect(status().isUnauthorized());
        rate(ownerToken, id, "{\"value\":3}").andExpect(status().isForbidden());
        rate(courierToken, id, "{\"value\":3}").andExpect(status().isForbidden());
        assertThat(ratingRows(id)).isZero();
    }

    @Test
    void laCalificacionNoSePuedeEditarNiEliminar() throws Exception {
        UUID id = deliveredOrder("r-12");
        rate(token, id, "{\"value\":3}").andExpect(status().isCreated());
        String path = ORDERS + "/" + id + "/rating";
        String bearer = "Bearer " + token;

        for (var request : new org.springframework.test.web.servlet.RequestBuilder[] {
                put(path).header("Authorization", bearer).contentType("application/json").content("{\"value\":5}"),
                patch(path).header("Authorization", bearer).contentType("application/json").content("{\"value\":5}"),
                delete(path).header("Authorization", bearer)}) {
            int code = mvc.perform(request).andReturn().getResponse().getStatus();
            assertThat(code).isIn(404, 405);
        }
        assertThat(jdbc.queryForObject("select value from calificaciones where order_id = ?", Integer.class, id))
                .isEqualTo(3);
    }
}
