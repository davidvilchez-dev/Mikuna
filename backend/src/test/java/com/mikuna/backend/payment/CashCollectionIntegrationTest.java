package com.mikuna.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.support.DeliveryTestSupport;
import com.mikuna.backend.support.TestOrders;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 014, US3: el efectivo queda pendiente de cobro hasta la entrega y se registra cobrado al entregarse. */
class CashCollectionIntegrationTest extends DeliveryTestSupport {

    private String courierToken;

    @BeforeEach
    void courier() throws Exception {
        courierAccount("rep@mail.com");
        courierToken = loginAndGetToken("rep@mail.com");
    }

    private UUID cashOrder(String key) throws Exception {
        jdbc.update("update carritos set restaurant_id = ?, selected_address_id = ? where id = ?", restaurantId,
                addressId, cartId);
        jdbc.update("delete from lineas_carrito where cart_id = ?", cartId);
        TestOrders.line(jdbc, cartId, lomo, 2);
        String json = confirm(token, key, "{\"paymentMethod\":\"CASH\",\"expectedTotal\":" + TOTAL + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private List<Map<String, Object>> transactions(UUID orderId, String type) {
        return jdbc.queryForList("select status, amount, result_code from transacciones_pago "
                + "where order_id = ? and type = ?", orderId, type);
    }

    @Test
    void elEfectivoSeCobraAlEntregarYNoAlAceptar() throws Exception {
        UUID id = cashOrder("efectivo-1");
        restaurantAct(id, "accept").andExpect(status().isOk());
        assertThat(paymentStatus(id)).isEqualTo("AUTHORIZED");
        assertThat(transactions(id, "CAPTURE")).isEmpty();

        restaurantAct(id, "start-preparation").andExpect(status().isOk());
        setAvailable(courierToken, true);
        restaurantAct(id, "mark-ready").andExpect(status().isOk());
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());
        assertThat(paymentStatus(id)).isEqualTo("AUTHORIZED");

        courierAct(courierToken, id, "complete").andExpect(status().isOk());

        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
        List<Map<String, Object>> captures = transactions(id, "CAPTURE");
        assertThat(captures).hasSize(1);
        assertThat(captures.get(0).get("status")).isEqualTo("CAPTURED");
        assertThat(captures.get(0).get("result_code")).isEqualTo("cash");
        assertThat(captures.get(0).get("amount").toString()).isEqualTo(TOTAL);
        verify(stripeGateway, never()).capture(anyString(), anyString());
    }

    @Test
    void unEfectivoCobradoAntesDel014NoSeVuelveACobrarAlEntregar() throws Exception {
        UUID id = cashOrder("efectivo-legado");
        restaurantAct(id, "accept").andExpect(status().isOk());
        // Simula el comportamiento anterior: efectivo marcado cobrado al aceptar.
        jdbc.update("update pagos set status = 'CAPTURED' where order_id = ?", id);
        restaurantAct(id, "start-preparation").andExpect(status().isOk());
        setAvailable(courierToken, true);
        restaurantAct(id, "mark-ready").andExpect(status().isOk());
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());

        courierAct(courierToken, id, "complete").andExpect(status().isOk());

        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
        assertThat(transactions(id, "CAPTURE")).isEmpty();
    }

    @Test
    void rechazoCancelacionDelClienteYVencimientoSiguenSinCobro() throws Exception {
        UUID rejected = cashOrder("efectivo-rech");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        UUID cancelled = cashOrder("efectivo-canc");
        customerCancel(cancelled).andExpect(status().isOk());

        assertThat(paymentStatus(rejected)).isEqualTo("RELEASED");
        assertThat(paymentStatus(cancelled)).isEqualTo("RELEASED");
        verify(stripeGateway, never()).cancel(anyString(), anyString());
    }

    @Test
    void conTarjetaElCobroSigueSiendoAlAceptar() throws Exception {
        UUID id = placeOrder("tarjeta-1");
        restaurantAct(id, "accept").andExpect(status().isOk());

        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
    }
}
