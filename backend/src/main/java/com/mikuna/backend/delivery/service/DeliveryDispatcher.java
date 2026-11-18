package com.mikuna.backend.delivery.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lanza la asignación automática tras confirmarse el cambio que la origina (research §4). No tiene transacción
 * propia: cada intento corre en la suya. Si algo falla, la entrega queda pendiente de asignación y la recuperan el
 * siguiente disparo o el administrador; nunca se propaga el error a quien originó el cambio.
 */
@Component
public class DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);

    private final DeliveryAssignmentService assignment;

    public DeliveryDispatcher(DeliveryAssignmentService assignment) {
        this.assignment = assignment;
    }

    /** Pedido Listo o entrega rechazada o liberada: busca el mejor candidato para esa entrega. */
    public void dispatch(UUID orderId) {
        try {
            assignment.assignAutomatically(orderId);
        } catch (RuntimeException e) {
            log.warn("La asignación automática del pedido {} no tuvo éxito; queda pendiente", orderId, e);
        }
    }

    /** Un repartidor pasó a disponible o terminó una entrega: se le asigna la pendiente más antigua que pueda tomar. */
    public void courierBecameFree(UUID courierId) {
        try {
            List<UUID> pending = assignment.pendingOrderIds();
            for (UUID orderId : pending) {
                if (assignment.assignToCourier(orderId, courierId)) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo asignar una entrega pendiente al repartidor {}", courierId, e);
        }
    }
}
