package com.mikuna.backend.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestCarts;
import com.mikuna.backend.support.TestProducts;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * RF-13: importes calculados solo en el servidor (valores de prueba: envío 5.00 y comisión 5 %), con precios
 * vigentes, dos decimales y recálculo tras cada cambio.
 */
class CartTotalsIntegrationTest extends IntegrationTestSupport {

    private static final String CART = "/api/customer/cart";

    private Account customer;
    private String token;
    private UUID restaurantId;
    private UUID lomo;
    private UUID cebiche;

    @BeforeEach
    void prepare() throws Exception {
        customer = customer("cliente@mail.com");
        token = loginAndGetToken("cliente@mail.com");
        restaurantId = TestProducts.approvedRestaurant(jdbc, restaurantOwner("local@mail.com").getId(),
                "La Picantería", "Criolla", "Ayacucho");
        lomo = TestProducts.product(jdbc, restaurantId, "Lomo saltado", "Fondos", "22.50", true, true);
        cebiche = TestProducts.product(jdbc, restaurantId, "Cebiche", "Entradas", "25.00", true, true);
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
        builder = builder.header("Authorization", "Bearer " + token);
        return mvc.perform(body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions add(UUID product, int quantity) throws Exception {
        return send(post(CART + "/lines"), "{\"productId\":\"%s\",\"quantity\":%d}".formatted(product, quantity));
    }

    private void selectAddress(UUID address) throws Exception {
        send(put(CART + "/address"), "{\"addressId\":\"%s\"}".formatted(address)).andExpect(status().isOk());
    }

    @Test
    void conDireccionCalculaSubtotalEnvioComisionYTotal() throws Exception {
        add(lomo, 2); // 45.00
        add(cebiche, 1); // 25.00
        selectAddress(TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho"));

        send(get(CART), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.subtotal").value(70.0))
                .andExpect(jsonPath("$.totals.deliveryFee").value(5.0))
                .andExpect(jsonPath("$.totals.commission").value(3.5))
                .andExpect(jsonPath("$.totals.total").value(78.5))
                .andExpect(jsonPath("$.totals.pending").doesNotExist());
    }

    @Test
    void losImportesSalenSiempreConDosDecimales() throws Exception {
        add(lomo, 2);
        selectAddress(TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho"));

        String body = send(get(CART), null).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"subtotal\":45.00").contains("\"deliveryFee\":5.00")
                .contains("\"commission\":2.25").contains("\"total\":52.25").contains("\"lineTotal\":45.00");
        BigDecimal subtotal = new BigDecimal(JsonPath.read(body, "$.totals.subtotal").toString());
        BigDecimal fee = new BigDecimal(JsonPath.read(body, "$.totals.deliveryFee").toString());
        BigDecimal commission = new BigDecimal(JsonPath.read(body, "$.totals.commission").toString());
        BigDecimal total = new BigDecimal(JsonPath.read(body, "$.totals.total").toString());
        assertThat(total).isEqualByComparingTo(subtotal.add(fee).add(commission));
    }

    @Test
    void sinDireccionSoloHaySubtotalYElRestoQuedaPendiente() throws Exception {
        add(lomo, 2);

        send(get(CART), null).andExpect(jsonPath("$.totals.subtotal").value(45.0))
                .andExpect(jsonPath("$.totals.deliveryFee").doesNotExist())
                .andExpect(jsonPath("$.totals.commission").doesNotExist())
                .andExpect(jsonPath("$.totals.total").doesNotExist())
                .andExpect(jsonPath("$.totals.pending").value("ADDRESS"));
    }

    @Test
    void conElCarritoVacioTodoEsCero() throws Exception {
        send(get(CART), null).andExpect(status().isOk()).andExpect(jsonPath("$.totals.subtotal").value(0.0))
                .andExpect(jsonPath("$.totals.total").value(0.0)).andExpect(jsonPath("$.totals.pending").value("EMPTY"));
    }

    @Test
    void recalculaTrasCadaCambioDelCarritoYDeLaDireccion() throws Exception {
        UUID near = TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho");
        UUID other = TestCarts.address(jdbc, customer.getId(), "Av. Dos 45", "San Juan Bautista");
        String body = add(lomo, 1).andReturn().getResponse().getContentAsString(); // 22.50
        selectAddress(near);

        add(cebiche, 2).andExpect(jsonPath("$.totals.subtotal").value(72.5)) // + 50.00
                .andExpect(jsonPath("$.totals.total").value(81.13)); // 72.50 + 5.00 + 3.63 (3.625 redondea hacia arriba)

        String first = JsonPath.read(body, "$.lines[0].id");
        send(put(CART + "/lines/" + first), "{\"quantity\":4}").andExpect(jsonPath("$.totals.subtotal").value(140.0));
        send(delete(CART + "/lines/" + first), null).andExpect(jsonPath("$.totals.subtotal").value(50.0))
                .andExpect(jsonPath("$.totals.commission").value(2.5));
        send(put(CART + "/address"), "{\"addressId\":\"%s\"}".formatted(other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(57.5));
    }

    @Test
    void usaElPrecioVigenteYNuncaUnoEnviadoPorElCliente() throws Exception {
        add(lomo, 2).andExpect(jsonPath("$.lines[0].unitPrice").value(22.5));

        jdbc.update("update productos set price = 30.00 where id = ?", lomo);

        send(get(CART), null).andExpect(jsonPath("$.lines[0].unitPrice").value(30.0))
                .andExpect(jsonPath("$.totals.subtotal").value(60.0));
    }

    @Test
    void elClienteNoPuedeForzarImportes() throws Exception {
        add(lomo, 1);
        String line = JsonPath.read(send(get(CART), null).andReturn().getResponse().getContentAsString(),
                "$.lines[0].id");

        for (String field : new String[] {"total", "subtotal", "deliveryFee", "commission", "price", "unitPrice",
                "lineTotal"}) {
            send(put(CART + "/lines/" + line), "{\"quantity\":2,\"" + field + "\":0.01}")
                    .andExpect(status().isBadRequest());
            send(post(CART + "/lines"), "{\"productId\":\"%s\",\"quantity\":1,\"%s\":0.01}".formatted(cebiche, field))
                    .andExpect(status().isBadRequest());
        }
        send(get(CART), null).andExpect(jsonPath("$.lines[0].quantity").value(1))
                .andExpect(jsonPath("$.lines.length()").value(1));
    }

    @Test
    void unaLineaQueDejaDeSerElegibleNoSumaYSeMarca() throws Exception {
        add(lomo, 1);
        add(cebiche, 1);
        jdbc.update("update productos set available = false where id = ?", cebiche);

        send(get(CART), null).andExpect(jsonPath("$.lines[1].available").value(false))
                .andExpect(jsonPath("$.lines[1].reason").value("PRODUCT_UNAVAILABLE"))
                .andExpect(jsonPath("$.lines[1].lineTotal").doesNotExist())
                .andExpect(jsonPath("$.totals.subtotal").value(22.5))
                .andExpect(jsonPath("$.notices[0]").value("«Cebiche» no está disponible y no se incluye en el total"));

        jdbc.update("update productos set available = true where id = ?", cebiche);
        jdbc.update("update restaurantes set available = false where id = ?", restaurantId);
        send(get(CART), null).andExpect(jsonPath("$.lines[0].reason").value("RESTAURANT_UNAVAILABLE"))
                .andExpect(jsonPath("$.totals.subtotal").value(0.0))
                .andExpect(jsonPath("$.totals.pending").value("EMPTY"))
                .andExpect(jsonPath("$.restaurant.attention").value("PAUSED"));
    }

    @Test
    void unProductoRetiradoSeMarcaComoRetirado() throws Exception {
        add(lomo, 1);
        jdbc.update("update productos set active = null where id = ?", lomo);

        send(get(CART), null).andExpect(jsonPath("$.lines[0].reason").value("PRODUCT_WITHDRAWN"))
                .andExpect(jsonPath("$.totals.pending").value("EMPTY"));
    }

    @Test
    void unaDireccionQueSalioDeLaZonaSeRetiraDeLaSeleccionConAviso() throws Exception {
        UUID address = TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho");
        add(lomo, 1);
        selectAddress(address);
        jdbc.update("update direcciones set district = 'Huanta' where id = ?", address);

        send(get(CART), null).andExpect(status().isOk()).andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.totals.pending").value("ADDRESS"))
                .andExpect(jsonPath("$.notices[0]").value(
                        "Tu dirección seleccionada ya no está en la zona de reparto; elige otra"));
        assertThat(jdbc.queryForObject("select selected_address_id from carritos", UUID.class)).isNull();
    }
}
