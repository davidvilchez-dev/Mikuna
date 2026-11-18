package com.mikuna.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Escenario de la spec 008: pedidos Listos para recoger, repartidores y administrador con sus tokens. */
public abstract class DeliveryTestSupport extends OrderStatusTestSupport {

    protected static final String COURIER = "/api/courier";
    protected static final String ADMIN_DELIVERIES = "/api/admin/deliveries";

    protected Account courierAccount(String email) {
        return registrationService.registerPublic("Repartidor " + email, email, PASSWORD, PASSWORD, Role.COURIER);
    }

    protected String adminToken() throws Exception {
        admin("admin@mail.com");
        return loginAndGetToken("admin@mail.com");
    }

    protected ResultActions putAvailability(String bearer, boolean available) throws Exception {
        return mvc.perform(put(COURIER + "/availability").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":" + available + "}"));
    }

    protected void setAvailable(String bearer, boolean available) throws Exception {
        putAvailability(bearer, available).andExpect(status().isOk());
    }

    protected ResultActions getAs(String bearer, String path) throws Exception {
        var request = get(path);
        return mvc.perform(bearer == null ? request : request.header("Authorization", "Bearer " + bearer));
    }

    protected ResultActions courierAct(String bearer, UUID orderId, String action) throws Exception {
        return act(bearer, COURIER + "/deliveries/" + orderId + "/" + action);
    }

    protected ResultActions courierAct(String bearer, UUID orderId, String action, String json) throws Exception {
        return act(bearer, COURIER + "/deliveries/" + orderId + "/" + action, json);
    }

    protected ResultActions adminAct(String bearer, UUID orderId, String action) throws Exception {
        return act(bearer, ADMIN_DELIVERIES + "/" + orderId + "/" + action);
    }

    protected ResultActions adminAssign(String bearer, UUID orderId, UUID courierId) throws Exception {
        return act(bearer, ADMIN_DELIVERIES + "/" + orderId + "/assign", "{\"courierId\":\"" + courierId + "\"}");
    }

    /** Un pedido llevado por la API hasta Listo para recoger (la asignación automática ya corrió tras el commit). */
    protected UUID readyOrder(String key) throws Exception {
        UUID id = placeOrder(key);
        restaurantAct(id, "accept").andExpect(status().isOk());
        restaurantAct(id, "start-preparation").andExpect(status().isOk());
        restaurantAct(id, "mark-ready").andExpect(status().isOk());
        return id;
    }

    protected UUID courierOf(UUID orderId) {
        List<UUID> ids = jdbc.query("select courier_id from entregas where order_id = ?",
                (rs, n) -> rs.getObject(1, UUID.class), orderId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    protected void freeSince(UUID courierId, Instant when) {
        jdbc.update("update repartidores_disponibilidad set free_since = ? where courier_id = ?",
                Timestamp.from(when), courierId);
    }

    /** Cambios de estado como «DE→A» en orden cronológico. */
    protected List<String> transitions(UUID orderId) {
        return changesOf(orderId).stream().map(c -> c.get("from_status") + "→" + c.get("to_status")).toList();
    }
}
