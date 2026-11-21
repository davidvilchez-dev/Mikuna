package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.OrderTestSupport;
import com.mikuna.backend.support.TestOrders;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** RF-14, RF-15, RF-16 y RF-04: confirmar el pedido, reservar el pago y vaciar el carrito. */
class OrderConfirmationIntegrationTest extends OrderTestSupport {

    @Test
    void confirmaRegistraElPedidoPendienteReservaElMontoYVaciaElCarrito() throws Exception {
        confirm(token, "clave-1", body(TOTAL)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.payment.method").value("CARD"))
                .andExpect(jsonPath("$.restaurant.name").value("La Picantería"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(45.0))
                .andExpect(jsonPath("$.delivery.street").value("Jr. Lima 123"))
                .andExpect(jsonPath("$.delivery.district").value("Ayacucho"))
                .andExpect(jsonPath("$.totals.subtotal").value(45.0))
                .andExpect(jsonPath("$.totals.deliveryFee").value(5.0))
                .andExpect(jsonPath("$.totals.commission").value(2.25))
                .andExpect(jsonPath("$.totals.total").value(52.25));

        assertThat(count("pedidos")).isEqualTo(1);
        assertThat(count("pagos")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from pagos", String.class)).isEqualTo("AUTHORIZED");
        assertThat(count("transacciones_pago")).isEqualTo(1);
        assertThat(count("lineas_carrito")).as("carrito vaciado").isZero();
        assertThat(jdbc.queryForObject("select selected_address_id from carritos where customer_id = ?", UUID.class,
                customer.getId())).as("dirección conservada").isEqualTo(addressId);
        assertThat(jdbc.queryForObject("select restaurant_id from carritos where customer_id = ?", UUID.class,
                customer.getId())).isNull();

        ArgumentCaptor<Long> cents = ArgumentCaptor.forClass(Long.class);
        verify(stripeGateway).authorize(cents.capture(), eq("pen"), eq("pm_card_visa"),
                eq("confirm-" + customer.getId() + "-clave-1"), any());
        assertThat(cents.getValue()).isEqualTo(5225L);
    }

    @Test
    void elPedidoConservaPreciosYDireccionAunqueCambienDespues() throws Exception {
        confirm(token, "clave-2", body(TOTAL)).andExpect(status().isCreated());

        jdbc.update("update productos set price = 99.00, name = 'Otro nombre' where id = ?", lomo);
        jdbc.update("update direcciones set street = 'Otra calle 999' where id = ?", addressId);

        UUID orderId = jdbc.queryForObject("select id from pedidos", UUID.class);
        mvc.perform(get(ORDERS + "/" + orderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(22.5))
                .andExpect(jsonPath("$.delivery.street").value("Jr. Lima 123"))
                .andExpect(jsonPath("$.totals.total").value(52.25));
    }

    @Test
    void sinTokenResponde401YConOtroRolOCuentaInhabilitada403SinCrearPedido() throws Exception {
        confirm("token-invalido", "clave-3", body(TOTAL)).andExpect(status().isUnauthorized());

        String ownerToken = loginAndGetToken("local@mail.com");
        confirm(ownerToken, "clave-3", body(TOTAL)).andExpect(status().isForbidden());

        disable(customer.getId());
        confirm(token, "clave-3", body(TOTAL)).andExpect(status().isForbidden());

        assertThat(count("pedidos")).isZero();
        verify(stripeGateway, never()).authorize(anyLong(), any(), any(), any(), any());
    }

    @Test
    void carritoVacioOSinDireccionSeRechazanSinContactarALaPasarela() throws Exception {
        jdbc.update("update carritos set selected_address_id = null where id = ?", cartId);
        confirm(token, "clave-4", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ADDRESS"));

        jdbc.update("delete from lineas_carrito");
        confirm(token, "clave-5", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));

        verify(stripeGateway, never()).authorize(anyLong(), any(), any(), any(), any());
        assertThat(count("pedidos")).isZero();
    }

    @Test
    void metodoDePagoNoOfrecidoOCamposDesconocidosSeRechazanConValidacion() throws Exception {
        confirm(token, "clave-6", "{\"paymentMethod\":\"BIZUM\",\"paymentMethodId\":\"pm_x\",\"expectedTotal\":52.25}")
                .andExpect(status().isBadRequest());
        confirm(token, "clave-6", "{\"paymentMethod\":\"CARD\",\"paymentMethodId\":\"4242424242424242\","
                + "\"expectedTotal\":52.25}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentMethodId").exists());
        confirm(token, "clave-6", "{\"paymentMethod\":\"CARD\",\"paymentMethodId\":\"pm_x\",\"expectedTotal\":52.25,"
                + "\"total\":1.00}").andExpect(status().isBadRequest());
        confirm(token, "clave-6", "{\"paymentMethod\":\"CARD\",\"paymentMethodId\":\"pm_x\",\"expectedTotal\":52.25,"
                + "\"customerId\":\"" + UUID.randomUUID() + "\"}").andExpect(status().isBadRequest());
        confirm(token, null, body(TOTAL)).andExpect(status().isBadRequest());
        confirm(token, "clave con espacios", body(TOTAL)).andExpect(status().isBadRequest());

        assertThat(count("pedidos")).isZero();
        verify(stripeGateway, never()).authorize(anyLong(), any(), any(), any(), any());
    }

    @Test
    void elResumenIndicaSiSePuedeConfirmarYNuncaExponeLaClaveSecreta() throws Exception {
        mvc.perform(get("/api/customer/checkout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.blockers.length()").value(0))
                .andExpect(jsonPath("$.paymentMethods[0].code").value("CARD"))
                .andExpect(jsonPath("$.publishableKey").value("pk_test_ficticia_solo_pruebas"))
                .andExpect(jsonPath("$.cart.totals.total").value(52.25))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk_test_"))));

        jdbc.update("update carritos set selected_address_id = null where id = ?", cartId);
        mvc.perform(get("/api/customer/checkout").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.blockers[0]").value("NO_ADDRESS"));

        jdbc.update("delete from lineas_carrito");
        mvc.perform(get("/api/customer/checkout").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.blockers[0]").value("CART_EMPTY"));
    }

    @Test
    void elResumenReportaRestauranteYProductosNoDisponibles() throws Exception {
        jdbc.update("update restaurantes set available = false where id = ?", restaurantId);
        mvc.perform(get("/api/customer/checkout").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.blockers[0]").value("RESTAURANT_UNAVAILABLE"));

        jdbc.update("update restaurantes set available = true where id = ?", restaurantId);
        jdbc.update("update productos set available = false where id = ?", lomo);
        mvc.perform(get("/api/customer/checkout").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.blockers[0]").value("LINES_UNAVAILABLE"));
    }

    @Test
    void elPedidoYElPagoSobrevivenAlReinicioYUnSegundoPedidoEsIndependiente() throws Exception {
        confirm(token, "clave-a", body(TOTAL)).andExpect(status().isCreated());

        // El estado está en PostgreSQL: se vuelve a consultar sin depender de memoria de la aplicación.
        UUID first = jdbc.queryForObject("select id from pedidos", UUID.class);
        mvc.perform(get(ORDERS + "/" + first).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));

        // Segundo carrito del mismo cliente: pedido y reserva propios.
        TestOrders.line(jdbc, cartId, cebiche, 1);
        BigDecimal subtotal = new BigDecimal("25.00");
        BigDecimal total = subtotal.add(new BigDecimal("5.00")).add(new BigDecimal("1.25"));
        jdbc.update("update carritos set restaurant_id = ? where id = ?", restaurantId, cartId);
        confirm(token, "clave-b", body(total.toPlainString())).andExpect(status().isCreated());

        assertThat(count("pedidos")).isEqualTo(2);
        assertThat(count("pagos")).isEqualTo(2);
    }
}
