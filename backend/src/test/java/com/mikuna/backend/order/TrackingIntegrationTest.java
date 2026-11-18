package com.mikuna.backend.order;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.DeliveryTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-27: el cliente dueño consulta el estado y las etapas de su pedido; nadie más. */
class TrackingIntegrationTest extends DeliveryTestSupport {

    @Test
    void muestraElEstadoActualYUnaEtapaPorCadaCambioEnOrden() throws Exception {
        UUID id = placeOrder("t-1");
        tracking(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.stages.length()").value(1));
        restaurantAct(id, "accept").andExpect(status().isOk());
        restaurantAct(id, "start-preparation").andExpect(status().isOk());
        restaurantAct(id, "mark-ready").andExpect(status().isOk());

        tracking(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.stages.length()").value(4))
                .andExpect(jsonPath("$.stages[0].status").value("PENDING"))
                .andExpect(jsonPath("$.stages[1].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.stages[2].status").value("PREPARING"))
                .andExpect(jsonPath("$.stages[3].status").value("READY"))
                .andExpect(jsonPath("$.stages[3].at").isNotEmpty())
                .andExpect(jsonPath("$.stages[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.courierLocation").doesNotExist());
    }

    @Test
    void unPedidoRechazadoOCanceladoMuestraSuEstadoFinal() throws Exception {
        UUID rejected = placeOrder("t-2");
        restaurantAct(rejected, "reject").andExpect(status().isOk());
        UUID cancelled = placeOrder("t-3");
        customerCancel(cancelled).andExpect(status().isOk());

        tracking(token, rejected).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.locationUnavailableReason").value("FINISHED"));
        tracking(token, cancelled).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.locationUnavailableReason").value("FINISHED"));
    }

    @Test
    void unPedidoAjenoOInexistenteEs404() throws Exception {
        UUID id = placeOrder("t-4");
        customer("otro-cliente@mail.com");
        String other = loginAndGetToken("otro-cliente@mail.com");

        tracking(other, id).andExpect(status().isNotFound());
        tracking(token, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void sinSesionEs401YConOtroRolEs403() throws Exception {
        UUID id = placeOrder("t-5");
        courierAccount("a@mail.com");

        tracking(null, id).andExpect(status().isUnauthorized());
        tracking(ownerToken, id).andExpect(status().isForbidden());
        tracking(loginAndGetToken("a@mail.com"), id).andExpect(status().isForbidden());
        tracking(adminToken(), id).andExpect(status().isForbidden());
    }
}
