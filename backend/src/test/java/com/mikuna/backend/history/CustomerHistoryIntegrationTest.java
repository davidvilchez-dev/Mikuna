package com.mikuna.backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.HistoryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-30: el cliente ve todos sus pedidos, con su estado final, productos, importes y pago, y ninguno ajeno. */
class CustomerHistoryIntegrationTest extends HistoryTestSupport {

    @Test
    void sinTokenEs401YConOtroRolEs403() throws Exception {
        history(null, CUSTOMER_HISTORY, "").andExpect(status().isUnauthorized());
        history(ownerToken, CUSTOMER_HISTORY, "").andExpect(status().isForbidden());
        history(courierToken, CUSTOMER_HISTORY, "").andExpect(status().isForbidden());
        history(adminToken(), CUSTOMER_HISTORY, "").andExpect(status().isForbidden());
    }

    @Test
    void incluyeEntregadosRechazadosYCanceladosConSusDatos() throws Exception {
        UUID delivered = deliveredOrder("h-1");
        UUID rejected = placeOrder("h-2");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        UUID cancelled = placeOrder("h-3");
        customerCancel(cancelled).andExpect(status().isOk());

        history(token, CUSTOMER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].orderId").value(cancelled.toString()))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].payment.status").value("RELEASED"))
                .andExpect(jsonPath("$.items[1].orderId").value(rejected.toString()))
                .andExpect(jsonPath("$.items[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[2].orderId").value(delivered.toString()))
                .andExpect(jsonPath("$.items[2].status").value("DELIVERED"))
                .andExpect(jsonPath("$.items[2].payment.method").value("CARD"))
                .andExpect(jsonPath("$.items[2].payment.status").value("CAPTURED"))
                .andExpect(jsonPath("$.items[2].restaurant.name").value("La Picantería"))
                .andExpect(jsonPath("$.items[2].lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.items[2].lines[0].unitPrice").value(22.5))
                .andExpect(jsonPath("$.items[2].lines[0].quantity").value(2))
                .andExpect(jsonPath("$.items[2].lines[0].lineTotal").value(45.0))
                .andExpect(jsonPath("$.items[2].totals.subtotal").value(45.0))
                .andExpect(jsonPath("$.items[2].totals.deliveryFee").value(5.0))
                .andExpect(jsonPath("$.items[2].totals.commission").value(2.25))
                .andExpect(jsonPath("$.items[2].totals.total").value(52.25))
                .andExpect(jsonPath("$.items[2].canRate").value(true))
                .andExpect(jsonPath("$.items[0].canRate").value(false))
                .andExpect(jsonPath("$.items[2].customerName").doesNotExist());
    }

    @Test
    void unClienteNoVeLosPedidosDeOtro() throws Exception {
        deliveredOrder("h-4");
        placeOrder("h-5");
        customer("otro@mail.com");
        String other = loginAndGetToken("otro@mail.com");

        history(other, CUSTOMER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(0));
        history(token, CUSTOMER_HISTORY, "").andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void sinPedidosDevuelveUnListadoVacioNoUnError() throws Exception {
        history(token, CUSTOMER_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void laRutaLiteralHistoryNoSeInterpretaComoIdDePedido() throws Exception {
        history(token, ORDERS + "/history", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        history(token, ORDERS + "/" + UUID.randomUUID(), "").andExpect(status().isNotFound());
    }

    @Test
    void laCalificacionEmitidaSeMuestraEnElPedido() throws Exception {
        UUID delivered = deliveredOrder("h-6");
        rate(token, delivered, "{\"value\":4}").andExpect(status().isCreated());

        history(token, CUSTOMER_HISTORY, "").andExpect(jsonPath("$.items[0].rating").value(4))
                .andExpect(jsonPath("$.items[0].canRate").value(false));
        assertThat(ratingRows(delivered)).isEqualTo(1);
    }
}
