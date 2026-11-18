package com.mikuna.backend.delivery.service;

import com.mikuna.backend.common.exception.DeliveryConflictException;
import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.CourierAvailabilityRepository;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.service.OrderStatusService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asignación de entregas (RF-22, RNF-05). Toda asignación bloquea primero la fila del pedido y después la del
 * repartidor (orden fijo, sin interbloqueos), comprueba el estado esperado y asigna con una actualización
 * condicional ({@code courier_id IS NULL}) que debe afectar exactamente una fila. Los intentos automáticos se
 * invocan tras el commit del cambio que los origina, por eso usan {@code REQUIRES_NEW} (research §4).
 */
@Service
public class DeliveryAssignmentService {

    static final int MAX_PENDING = 20;

    private final OrderRepository orders;
    private final DeliveryRepository deliveries;
    private final CourierAvailabilityRepository availability;
    private final OrderStatusService orderStatus;
    private final Clock clock;

    public DeliveryAssignmentService(OrderRepository orders, DeliveryRepository deliveries,
            CourierAvailabilityRepository availability, OrderStatusService orderStatus, Clock clock) {
        this.orders = orders;
        this.deliveries = deliveries;
        this.availability = availability;
        this.orderStatus = orderStatus;
        this.clock = clock;
    }

    /** Pedido y entrega bloqueados y comprobados como asignables. */
    private record Assignable(Order order, Delivery delivery) {
    }

    /**
     * Asigna la entrega al candidato que lleva más tiempo libre (FR-003). Devuelve {@code false}, sin cambios, si
     * el pedido ya no está Listo, ya tiene repartidor o no hay candidato (queda pendiente, FR-004).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean assignAutomatically(UUID orderId) {
        Assignable target = lockAssignable(orderId);
        if (target == null) {
            return false;
        }
        for (UUID courierId : availability.findCandidateIds(target.delivery().getId())) {
            if (availability.lockSkipLocked(courierId).isEmpty()) {
                continue; // otra asignación lo tiene: se prueba con el siguiente
            }
            if (availability.countCandidate(courierId, target.delivery().getId()) == 0) {
                continue; // dejó de ser candidato entre la consulta y el bloqueo
            }
            assign(target.order(), target.delivery(), courierId, null, ActorRole.SYSTEM);
            return true;
        }
        return false;
    }

    /** Intenta asignar esa entrega pendiente a ese repartidor; {@code false} si no puede (FR-004). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean assignToCourier(UUID orderId, UUID courierId) {
        Assignable target = lockAssignable(orderId);
        if (target == null || availability.findByIdForUpdate(courierId).isEmpty()) {
            return false;
        }
        if (availability.countCandidate(courierId, target.delivery().getId()) == 0) {
            return false;
        }
        assign(target.order(), target.delivery(), courierId, null, ActorRole.SYSTEM);
        return true;
    }

    /** Pedidos con entrega pendiente de asignación, los más antiguos primero. */
    @Transactional(readOnly = true)
    public List<UUID> pendingOrderIds() {
        return deliveries.findPending(OrderStatus.READY).stream().limit(MAX_PENDING).map(Delivery::getOrderId)
                .toList();
    }

    /**
     * Asignación condicional y cambio de estado, con el pedido ya bloqueado por quien llama. Si la actualización
     * no afecta exactamente una fila, otra solicitud ya la asignó: no se cambia nada (RNF-05).
     */
    void assign(Order order, Delivery delivery, UUID courierId, UUID actorId, ActorRole role) {
        int rows = deliveries.assignIfFree(order.getId(), courierId, Instant.now(clock));
        if (rows != 1) {
            throw new DeliveryConflictException();
        }
        orderStatus.changeStatus(order, OrderStatus.ASSIGNED, actorId, role, null);
    }

    private Assignable lockAssignable(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.READY) {
            return null;
        }
        Delivery delivery = deliveries.findByOrderId(orderId).orElse(null);
        if (delivery == null || delivery.getCourierId() != null) {
            return null;
        }
        return new Assignable(order, delivery);
    }
}
