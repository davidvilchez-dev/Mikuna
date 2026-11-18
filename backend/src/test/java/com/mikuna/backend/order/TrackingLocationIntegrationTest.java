package com.mikuna.backend.order;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.support.DeliveryTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF-28: solo el cliente dueño ve la ubicación del repartidor, y solo con el pedido Recogido. */
class TrackingLocationIntegrationTest extends DeliveryTestSupport {

    private String courierToken;

    @BeforeEach
    void courierReady() throws Exception {
        courierAccount("a@mail.com");
        courierToken = loginAndGetToken("a@mail.com");
        setAvailable(courierToken, true);
    }

    @Test
    void asignadoNoMuestraCoordenadasAunqueElRepartidorYaEnvieLaUbicacion() throws Exception {
        UUID id = readyOrder("tl-1");
        sendLocation(courierToken, -13.10, -74.20, Instant.now().minusSeconds(10)).andExpect(status().isNoContent());

        tracking(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.courierLocation").doesNotExist())
                .andExpect(jsonPath("$.locationUnavailableReason").value("NOT_PICKED_UP"));
    }

    @Test
    void recogidoMuestraLaUbicacionVigenteConSuHoraYSeActualiza() throws Exception {
        UUID id = readyOrder("tl-2");
        Instant base = Instant.now().minusSeconds(60);
        sendLocation(courierToken, -13.10, -74.20, base).andExpect(status().isNoContent());
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());

        tracking(token, id).andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.courierLocation.latitude").value(-13.10))
                .andExpect(jsonPath("$.courierLocation.longitude").value(-74.20))
                .andExpect(jsonPath("$.courierLocation.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.locationUnavailableReason").doesNotExist());

        sendLocation(courierToken, -13.25, -74.35, base.plusSeconds(30)).andExpect(status().isNoContent());

        tracking(token, id).andExpect(jsonPath("$.courierLocation.latitude").value(-13.25))
                .andExpect(jsonPath("$.courierLocation.longitude").value(-74.35));
    }

    @Test
    void recogidoSinUbicacionIndicaQueAunNoEstaDisponible() throws Exception {
        UUID id = readyOrder("tl-3");
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());

        tracking(token, id).andExpect(jsonPath("$.courierLocation").doesNotExist())
                .andExpect(jsonPath("$.locationUnavailableReason").value("NO_LOCATION_YET"));
    }

    @Test
    void entregadoDejaDeMostrarLaUbicacion() throws Exception {
        UUID id = readyOrder("tl-4");
        sendLocation(courierToken, -13.10, -74.20, Instant.now().minusSeconds(10)).andExpect(status().isNoContent());
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());
        courierAct(courierToken, id, "complete").andExpect(status().isOk());

        tracking(token, id).andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.courierLocation").doesNotExist())
                .andExpect(jsonPath("$.locationUnavailableReason").value("FINISHED"));
    }

    @Test
    void conOtroRepartidorTrasUnRechazoNoSeMuestraLaUbicacionDelAnterior() throws Exception {
        courierAccount("b@mail.com");
        String second = loginAndGetToken("b@mail.com");
        UUID id = readyOrder("tl-5");
        sendLocation(courierToken, -13.10, -74.20, Instant.now().minusSeconds(20)).andExpect(status().isNoContent());
        courierAct(courierToken, id, "reject", "{}").andExpect(status().isOk());
        setAvailable(second, true);
        courierAct(second, id, "pickup").andExpect(status().isOk());

        tracking(token, id).andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.courierLocation").doesNotExist())
                .andExpect(jsonPath("$.locationUnavailableReason").value("NO_LOCATION_YET"));
    }

    @Test
    void nadieMasVeLaUbicacion() throws Exception {
        UUID id = readyOrder("tl-6");
        sendLocation(courierToken, -13.10, -74.20, Instant.now().minusSeconds(10)).andExpect(status().isNoContent());
        courierAct(courierToken, id, "pickup").andExpect(status().isOk());
        customer("otro-cliente@mail.com");

        tracking(loginAndGetToken("otro-cliente@mail.com"), id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.courierLocation").doesNotExist());
        tracking(ownerToken, id).andExpect(status().isForbidden());
        tracking(courierToken, id).andExpect(status().isForbidden());
        tracking(adminToken(), id).andExpect(status().isForbidden());
        tracking(null, id).andExpect(status().isUnauthorized());
    }
}
