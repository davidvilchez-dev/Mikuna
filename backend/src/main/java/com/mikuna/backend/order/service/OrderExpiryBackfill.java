package com.mikuna.backend.order.service;

import com.mikuna.backend.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Al arrancar, fija el vencimiento de los pedidos anteriores a la spec 012 como creación + plazo vigente; los
 * Pendientes muy antiguos vencen así en el primer barrido (research §5).
 */
@Component
public class OrderExpiryBackfill {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryBackfill.class);

    private final OrderRepository orders;
    private final OrderExpiryPolicy policy;

    public OrderExpiryBackfill(OrderRepository orders, OrderExpiryPolicy policy) {
        this.orders = orders;
        this.policy = policy;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public int backfill() {
        int rows = orders.backfillExpiresAt(policy.timeout().toSeconds());
        if (rows > 0) {
            log.info("Vencimiento fijado en {} pedidos anteriores", rows);
        }
        return rows;
    }
}
