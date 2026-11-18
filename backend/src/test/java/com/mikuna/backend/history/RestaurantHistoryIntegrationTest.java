package com.mikuna.backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.HistoryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-30 y RF-31: el restaurante ve solo los pedidos que recibió, con su calificación y el nombre del cliente. */
class RestaurantHistoryIntegrationTest extends HistoryTestSupport {

    @Test
    void sinTokenEs401YConOtroRolEs403() throws Exception {
        history(null, RESTAURANT_HISTORY, "").andExpect(status().isUnauthorized());
        history(token, RESTAURANT_HISTORY, "").andExpect(status().isForbidden());
        history(courierToken, RESTAURANT_HISTORY, "").andExpect(status().isForbidden());
    }

    @Test
    void veSusPedidosEnTodosLosEstadosConLaCalificacionCuandoExiste() throws Exception {
        UUID delivered = deliveredOrder("rh-1");
        rate(token, delivered, "{\"value\":5}").andExpect(status().isCreated());
        UUID rejected = placeOrder("rh-2");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        UUID unrated = deliveredOrder("rh-3");

        history(ownerToken, RESTAURANT_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].orderId").value(unrated.toString()))
                .andExpect(jsonPath("$.items[0].rating").doesNotExist())
                .andExpect(jsonPath("$.items[1].orderId").value(rejected.toString()))
                .andExpect(jsonPath("$.items[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[2].orderId").value(delivered.toString()))
                .andExpect(jsonPath("$.items[2].rating").value(5))
                .andExpect(jsonPath("$.items[2].customerName").value("Cliente"))
                .andExpect(jsonPath("$.items[2].payment.status").value("CAPTURED"))
                .andExpect(jsonPath("$.items[2].totals.total").value(52.25))
                .andExpect(jsonPath("$.items[2].restaurant").doesNotExist())
                .andExpect(jsonPath("$.items[2].canRate").value(false));
    }

    @Test
    void laRespuestaNoIncluyeDireccionNiTelefonoDelCliente() throws Exception {
        deliveredOrder("rh-4");

        String body = history(ownerToken, RESTAURANT_HISTORY, "").andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        assertThat(body).contains("\"customerName\":\"Cliente\"");
        assertThat(body).doesNotContain("deliveryAddress").doesNotContain("Jr. Lima 123")
                .doesNotContain("street").doesNotContain("phone").doesNotContain("contactPhone");
    }

    @Test
    void otroRestauranteNoVeLosPedidosAjenos() throws Exception {
        deliveredOrder("rh-5");
        placeOrder("rh-6");
        String other = otherOwnerToken();

        history(other, RESTAURANT_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        history(ownerToken, RESTAURANT_HISTORY, "").andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void unRestauranteInhabilitadoParaPedidosConservaSuHistorial() throws Exception {
        deliveredOrder("rh-7");
        jdbc.update("update restaurantes set available = false, status = 'REJECTED' where id = ?", restaurantId);

        history(ownerToken, RESTAURANT_HISTORY, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void unaCuentaBloqueadaNoPuedeConsultarNada() throws Exception {
        deliveredOrder("rh-8");
        disable(accounts.findByEmail("local@mail.com").orElseThrow().getId());

        int code = history(ownerToken, RESTAURANT_HISTORY, "").andReturn().getResponse().getStatus();

        assertThat(code).isIn(401, 403);
    }

    @Test
    void unaCuentaSinEstablecimientoRegistradoEs403() throws Exception {
        restauranteSinRegistro();
    }

    private void restauranteSinRegistro() throws Exception {
        restaurantOwner("sin-local@mail.com");
        String bearer = loginAndGetToken("sin-local@mail.com");

        history(bearer, RESTAURANT_HISTORY, "").andExpect(status().isForbidden());
    }
}
