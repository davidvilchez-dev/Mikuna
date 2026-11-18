package com.mikuna.backend.delivery.service;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.model.OrderReadyEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Crea la entrega al quedar el pedido Listo para recoger y, ya confirmado, intenta asignarla (RF-22). */
@Component
public class OrderReadyListener {

    private final DeliveryRepository deliveries;
    private final DeliveryDispatcher dispatcher;
    private final Clock clock;

    public OrderReadyListener(DeliveryRepository deliveries, DeliveryDispatcher dispatcher, Clock clock) {
        this.deliveries = deliveries;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    /** En la misma transacción que el cambio a Listo: la entrega existe si y solo si el pedido quedó Listo. */
    @EventListener
    public void createDelivery(OrderReadyEvent event) {
        if (deliveries.findByOrderId(event.orderId()).isEmpty()) {
            deliveries.save(new Delivery(event.orderId(), Instant.now(clock)));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchAfterCommit(OrderReadyEvent event) {
        dispatcher.dispatch(event.orderId());
    }
}
