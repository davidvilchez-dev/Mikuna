package com.mikuna.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.mikuna.backend.account.model.Account;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Escenario común de las pruebas de pedidos: un cliente con un carrito listo (2 × Lomo saltado a 22.50, dirección
 * en zona). Con los valores de prueba (envío 5.00 y comisión 5 %): subtotal 45.00, comisión 2.25, total 52.25.
 */
public abstract class OrderTestSupport extends IntegrationTestSupport {

    protected static final String ORDERS = "/api/customer/orders";
    protected static final String TOTAL = "52.25";

    protected Account customer;
    protected String token;
    protected UUID restaurantId;
    protected UUID lomo;
    protected UUID cebiche;
    protected UUID addressId;
    protected UUID cartId;

    @BeforeEach
    void prepareOrderScenario() throws Exception {
        customer = customer("cliente@mail.com");
        token = loginAndGetToken("cliente@mail.com");
        restaurantId = TestProducts.approvedRestaurant(jdbc, restaurantOwner("local@mail.com").getId(),
                "La Picantería", "Criolla", "Ayacucho");
        lomo = TestProducts.product(jdbc, restaurantId, "Lomo saltado", "Fondos", "22.50", true, true);
        cebiche = TestProducts.product(jdbc, restaurantId, "Cebiche", "Entradas", "25.00", true, true);
        addressId = TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho");
        cartId = TestOrders.cart(jdbc, customer.getId(), restaurantId, addressId);
        TestOrders.line(jdbc, cartId, lomo, 2);
    }

    protected static String body(String total) {
        return "{\"paymentMethod\":\"CARD\",\"paymentMethodId\":\"pm_card_visa\",\"expectedTotal\":%s}"
                .formatted(total);
    }

    protected ResultActions confirm(String bearer, String key, String json) throws Exception {
        var request = post(ORDERS).header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(key == null ? request : request.header("Idempotency-Key", key));
    }

    protected int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
