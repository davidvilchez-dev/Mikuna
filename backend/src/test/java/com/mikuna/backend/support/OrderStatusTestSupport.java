package com.mikuna.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Escenario de la spec 007: un restaurante con su dueño autenticado y utilidades para crear y operar pedidos. */
public abstract class OrderStatusTestSupport extends OrderTestSupport {

    protected static final String RESTAURANT_ORDERS = "/api/restaurant/orders";

    protected String ownerToken;

    @BeforeEach
    void loginOwner() throws Exception {
        ownerToken = loginAndGetToken("local@mail.com");
    }

    /** Confirma un pedido del cliente de la base (reponiendo el carrito) y devuelve su identificador. */
    protected UUID placeOrder(String key) throws Exception {
        jdbc.update("update carritos set restaurant_id = ?, selected_address_id = ? where id = ?", restaurantId,
                addressId, cartId);
        jdbc.update("delete from lineas_carrito where cart_id = ?", cartId);
        TestOrders.line(jdbc, cartId, lomo, 2);
        String json = confirm(token, key, body(TOTAL)).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    protected ResultActions act(String bearer, String path) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(bearer == null ? request : request.header("Authorization", "Bearer " + bearer));
    }

    protected ResultActions act(String bearer, String path, String json) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions restaurantAct(UUID orderId, String action) throws Exception {
        return act(ownerToken, RESTAURANT_ORDERS + "/" + orderId + "/" + action);
    }

    protected ResultActions customerCancel(UUID orderId) throws Exception {
        return act(token, ORDERS + "/" + orderId + "/cancel");
    }

    protected ResultActions list(String bearer, String query) throws Exception {
        return mvc.perform(get(RESTAURANT_ORDERS + query).header("Authorization", "Bearer " + bearer));
    }

    protected String orderStatus(UUID orderId) {
        return jdbc.queryForObject("select status from pedidos where id = ?", String.class, orderId);
    }

    protected String paymentStatus(UUID orderId) {
        return jdbc.queryForObject("select status from pagos where order_id = ?", String.class, orderId);
    }

    protected List<Map<String, Object>> changesOf(UUID orderId) {
        return jdbc.queryForList("select from_status, to_status, actor_id, actor_role, reason, changed_at "
                + "from cambios_estado_pedido where order_id = ? order by changed_at, to_status", orderId);
    }

    /** Segundo restaurante aprobado, con su dueño autenticado, para probar la pertenencia. */
    protected String otherOwnerToken() throws Exception {
        TestProducts.approvedRestaurant(jdbc, restaurantOwner("otro@mail.com").getId(), "Otro Local", "Criolla",
                "Ayacucho");
        return loginAndGetToken("otro@mail.com");
    }
}
