package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.order.model.OrderPlacedEvent;
import com.mikuna.backend.support.OrderTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Spec 009: confirmar un pedido publica un evento de pedido nuevo una sola vez, aunque se reenvíe la clave. */
@RecordApplicationEvents
class OrderPlacedEventIntegrationTest extends OrderTestSupport {

    @Autowired
    private ApplicationEvents events;

    @Test
    void confirmarPublicaElEventoUnaVezYElReenvioIdempotenteNoLoRepite() throws Exception {
        confirm(token, "placed-1", body(TOTAL)).andExpect(status().isCreated());
        confirm(token, "placed-1", body(TOTAL)).andExpect(status().isOk());

        assertThat(events.stream(OrderPlacedEvent.class).count()).isEqualTo(1);
    }
}
