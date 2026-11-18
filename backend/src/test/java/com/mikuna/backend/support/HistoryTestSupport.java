package com.mikuna.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Escenario de la spec 010: un repartidor y utilidades para llevar pedidos hasta Entregado y consultar historiales. */
public abstract class HistoryTestSupport extends DeliveryTestSupport {

    protected static final String CUSTOMER_HISTORY = ORDERS + "/history";
    protected static final String RESTAURANT_HISTORY = RESTAURANT_ORDERS + "/history";
    protected static final String COURIER_HISTORY = COURIER + "/deliveries/history";

    protected Account courierA;
    protected String courierToken;

    @BeforeEach
    void prepareHistoryScenario() throws Exception {
        courierA = courierAccount("repartidor-a@mail.com");
        courierToken = loginAndGetToken("repartidor-a@mail.com");
        setAvailable(courierToken, true);
    }

    /** Pedido llevado por la API hasta Entregado por el repartidor A. */
    protected UUID deliveredOrder(String key) throws Exception {
        UUID id = readyOrder(key);
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());
        courierAct(courierToken, id, "complete").andExpect(status().isOk());
        return id;
    }

    protected ResultActions history(String bearer, String path, String query) throws Exception {
        var request = get(path + query);
        return mvc.perform(bearer == null ? request : request.header("Authorization", "Bearer " + bearer));
    }

    protected ResultActions rate(String bearer, UUID orderId, String json) throws Exception {
        var request = post(ORDERS + "/" + orderId + "/rating").contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(bearer == null ? request : request.header("Authorization", "Bearer " + bearer));
    }

    protected int ratingRows(UUID orderId) {
        return jdbc.queryForObject("select count(*) from calificaciones where order_id = ?", Integer.class, orderId);
    }
}
