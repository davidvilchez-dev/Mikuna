package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.TestProducts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.mikuna.backend.support.OrderTestSupport;

/** RF-14: al confirmar se revalida todo; si algo falla no hay pedido ni reserva y el carrito se conserva. */
class OrderValidationIntegrationTest extends OrderTestSupport {

    private void assertRejectedWithoutSideEffects() {
        verify(stripeGateway, never()).authorize(anyLong(), any(), any(), any(), any());
        assertThat(count("pedidos")).isZero();
        assertThat(count("pagos")).isZero();
        assertThat(count("transacciones_pago")).isZero();
        assertThat(count("lineas_carrito")).as("el carrito se conserva").isEqualTo(1);
    }

    @Test
    void restauranteEnPausaFueraDeHorarioONoAprobadoSeRechaza() throws Exception {
        jdbc.update("update restaurantes set available = false where id = ?", restaurantId);
        confirm(token, "k1", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"));

        jdbc.update("update restaurantes set available = true where id = ?", restaurantId);
        TestProducts.closeAllDays(jdbc, restaurantId);
        confirm(token, "k2", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"));

        jdbc.update("update restaurantes set status = 'PENDING' where id = ?", restaurantId);
        confirm(token, "k3", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"));

        assertRejectedWithoutSideEffects();
    }

    @Test
    void restauranteConCuentaInhabilitadaSeRechaza() throws Exception {
        UUID ownerId = jdbc.queryForObject("select owner_id from restaurantes where id = ?", UUID.class,
                restaurantId);
        disable(ownerId);
        confirm(token, "k4", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"));
        assertRejectedWithoutSideEffects();
    }

    @Test
    void productoNoDisponibleORetiradoSeRechazaListandoElProducto() throws Exception {
        jdbc.update("update productos set available = false where id = ?", lomo);
        confirm(token, "k5", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Lomo saltado")));

        jdbc.update("update productos set available = true, active = null, withdrawn_at = now() where id = ?", lomo);
        confirm(token, "k6", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"));

        assertRejectedWithoutSideEffects();
    }

    @Test
    void cantidadFueraDeLosLimitesSeRechaza() throws Exception {
        jdbc.update("update lineas_carrito set quantity = 21");
        confirm(token, "k7", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"));

        jdbc.update("update lineas_carrito set quantity = 0");
        confirm(token, "k8", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"));

        assertRejectedWithoutSideEffects();
    }

    @Test
    void direccionFueraDeLaZonaSeRechazaSinLimpiarLaSeleccion() throws Exception {
        jdbc.update("update direcciones set district = 'Lima' where id = ?", addressId);
        confirm(token, "k9", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ORDERABLE"));

        assertRejectedWithoutSideEffects();
        assertThat(jdbc.queryForObject("select selected_address_id from carritos where id = ?", UUID.class, cartId))
                .as("la confirmación rechaza, no limpia en silencio").isEqualTo(addressId);
    }

    @Test
    void siCambioUnPrecioSePideConfirmarDeNuevoConElResumenVigente() throws Exception {
        jdbc.update("update productos set price = 30.00 where id = ?", lomo);
        // 2 x 30.00 = 60.00 + 5.00 + comisión 3.00 = 68.00
        confirm(token, "k10", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMOUNTS_CHANGED"))
                .andExpect(jsonPath("$.checkout.cart.totals.total").value(68.0))
                .andExpect(jsonPath("$.checkout.ready").value(true));

        assertRejectedWithoutSideEffects();

        confirm(token, "k11", body("68.00")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.totals.total").value(68.0));
    }

    @Test
    void elMontoReservadoEsElRecalculadoAunqueElClienteEnvieOtrosImportes() throws Exception {
        confirm(token, "k12", "{\"paymentMethod\":\"CARD\",\"paymentMethodId\":\"pm_card_visa\","
                + "\"expectedTotal\":" + TOTAL + ",\"subtotal\":1.00,\"deliveryFee\":0,\"commission\":0}")
                .andExpect(status().isBadRequest());
        assertRejectedWithoutSideEffects();

        confirm(token, "k13", body(TOTAL)).andExpect(status().isCreated());
        verify(stripeGateway).authorize(org.mockito.ArgumentMatchers.eq(5225L), any(), any(), any(), any());
    }
}
