package com.mikuna.backend.order.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido periódico de pedidos vencidos (spec 012, research §1). El estado vive en la base de datos, así que tras
 * un reinicio el primer barrido cancela lo que venció durante la caída. El fallo de un pedido (por ejemplo, la
 * pasarela no responde) no detiene a los demás: ese pedido sigue Pendiente y se reintenta en el siguiente barrido.
 */
@Component
public class PendingOrderExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpirySweeper.class);

    private final PendingOrderExpiryService expiry;

    public PendingOrderExpirySweeper(PendingOrderExpiryService expiry) {
        this.expiry = expiry;
    }

    /** Devuelve cuántos pedidos canceló en esta pasada. */
    @Scheduled(initialDelayString = "${mikuna.orders.expiry-initial-delay}",
            fixedDelayString = "${mikuna.orders.expiry-sweep}")
    public int sweep() {
        int expired = 0;
        for (UUID orderId : expiry.dueOrderIds()) {
            try {
                if (expiry.expire(orderId)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo vencer el pedido {}; se reintentará en el siguiente barrido", orderId, e);
            }
        }
        if (expired > 0) {
            log.info("Pedidos cancelados por falta de respuesta del restaurante: {}", expired);
        }
        return expired;
    }
}
