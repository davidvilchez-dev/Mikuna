package com.mikuna.backend.order.service;

import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancela por vencimiento los pedidos Pendientes sin respuesta del restaurante (spec 012). Cada pedido se procesa
 * en su propia transacción: bloquea la fila del pedido (el mismo bloqueo que aceptar, rechazar y cancelar) y,
 * con ella bloqueada, recomprueba estado y plazo antes de tocar el pago. Así, frente a una decisión simultánea o a
 * otra instancia que barre a la vez, solo una acción prevalece y la reserva se libera una sola vez. El orden de
 * bloqueo es siempre pedido → pago, sin interbloqueos.
 */
@Service
public class PendingOrderExpiryService {

    static final int BATCH = 100;

    private final OrderRepository orders;
    private final OrderStatusService statusService;
    private final Clock clock;

    public PendingOrderExpiryService(OrderRepository orders, OrderStatusService statusService, Clock clock) {
        this.orders = orders;
        this.statusService = statusService;
        this.clock = clock;
    }

    /** Hasta {@value #BATCH} pedidos Pendientes ya vencidos, los más antiguos primero. */
    @Transactional(readOnly = true)
    public List<UUID> dueOrderIds() {
        return orders.findExpiredIds(OrderStatus.PENDING, Instant.now(clock), PageRequest.of(0, BATCH));
    }

    /** Vence ese pedido si sigue Pendiente y su plazo terminó; devuelve si lo canceló. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(UUID orderId) {
        return orders.findByIdForUpdate(orderId).map(statusService::expireIfDue).orElse(false);
    }
}
