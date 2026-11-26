package com.mikuna.backend.delivery.service;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.common.exception.CourierNotCandidateException;
import com.mikuna.backend.common.exception.CourierStillEnabledException;
import com.mikuna.backend.common.exception.InvalidOrderTransitionException;
import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.delivery.dto.PendingDeliveryResponse;
import com.mikuna.backend.delivery.model.CourierAvailability;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operaciones del administrador sobre las entregas (RF-24 y FR-010a): asignar manualmente y liberar. */
@Service
public class DeliveryAdminService {

    private final OrderRepository orders;
    private final DeliveryRepository deliveries;
    private final CourierAvailabilityRepository availability;
    private final AccountRepository accounts;
    private final DeliveryAssignmentService assignment;
    private final OrderStatusService orderStatus;
    private final DeliveryDispatcher dispatcher;
    private final Clock clock;

    public DeliveryAdminService(OrderRepository orders, DeliveryRepository deliveries,
            CourierAvailabilityRepository availability, AccountRepository accounts,
            DeliveryAssignmentService assignment, OrderStatusService orderStatus, DeliveryDispatcher dispatcher,
            Clock clock) {
        this.clock = clock;
        this.orders = orders;
        this.deliveries = deliveries;
        this.availability = availability;
        this.accounts = accounts;
        this.assignment = assignment;
        this.orderStatus = orderStatus;
        this.dispatcher = dispatcher;
    }

    /** Pendientes de asignación con sus candidatos, y asignadas a repartidores inhabilitados (más antiguas primero). */
    @Transactional(readOnly = true)
    public List<PendingDeliveryResponse> listPending() {
        List<PendingDeliveryResponse> result = new ArrayList<>();
        for (Delivery delivery : deliveries.findPending(OrderStatus.READY)) {
            orders.findById(delivery.getOrderId()).ifPresent(order -> result.add(
                    response(order, delivery, null, false, candidates(delivery))));
        }
        for (Delivery delivery : deliveries.findAssignedToDisabledCourier(OrderStatus.ASSIGNED)) {
            orders.findById(delivery.getOrderId()).ifPresent(order -> result.add(
                    response(order, delivery, delivery.getCourierId(), true, List.of())));
        }
        return result;
    }

    /** READY a ASSIGNED a un candidato elegido por el administrador (FR-010); el responsable es su cuenta. */
    @Transactional
    public PendingDeliveryResponse assign(UUID adminId, UUID orderId, UUID courierId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
        Delivery delivery = deliveries.findByOrderId(orderId).orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() != OrderStatus.READY || delivery.getCourierId() != null) {
            throw new InvalidOrderTransitionException();
        }
        availability.findByIdForUpdate(courierId).orElseThrow(CourierNotCandidateException::new);
        if (availability.countCandidate(courierId, delivery.getId()) == 0) {
            throw new CourierNotCandidateException();
        }
        assignment.assign(order, delivery, courierId, adminId, ActorRole.ADMIN);
        return response(order, delivery, courierId, false, List.of());
    }

    /** ASSIGNED a READY cuando el repartidor está inhabilitado, sin contar como rechazo (FR-010a). */
    @Transactional
    public PendingDeliveryResponse release(UUID adminId, UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
        Delivery delivery = deliveries.findByOrderId(orderId).orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() != OrderStatus.ASSIGNED || delivery.getCourierId() == null) {
            throw new InvalidOrderTransitionException();
        }
        Account courier = accounts.findById(delivery.getCourierId()).orElse(null);
        if (courier != null && courier.isEnabled()) {
            throw new CourierStillEnabledException();
        }
        delivery.release();
        orderStatus.changeStatus(order, OrderStatus.READY, adminId, ActorRole.ADMIN, null);
        AfterCommit.run(() -> dispatcher.dispatch(orderId));
        return response(order, delivery, null, false, List.of());
    }

    /**
     * Pedido Asignado o Recogido que el administrador cancela (spec 014), con el pedido ya bloqueado por quien llama:
     * el repartidor vuelve a contar como libre y, tras el commit, se le ofrece otra entrega pendiente. La entrega
     * conserva su {@code courier_id} como registro; deja de estar activa porque el pedido ya no está Asignado ni
     * Recogido (research §6). Devuelve el repartidor liberado, o {@code null} si no había.
     */
    @Transactional
    public UUID freeCourierOfCancelledOrder(UUID orderId) {
        UUID courierId = deliveries.findByOrderId(orderId).map(Delivery::getCourierId).orElse(null);
        if (courierId == null) {
            return null;
        }
        availability.findByIdForUpdate(courierId).ifPresent(row -> {
            row.markFree(Instant.now(clock));
            availability.save(row);
        });
        AfterCommit.run(() -> dispatcher.courierBecameFree(courierId));
        return courierId;
    }

    private List<PendingDeliveryResponse.Candidate> candidates(Delivery delivery) {
        List<UUID> ids = availability.findCandidateIds(delivery.getId());
        Map<UUID, Account> names = accounts.findAllById(ids).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<UUID, CourierAvailability> rows = availability.findAllById(ids).stream()
                .collect(Collectors.toMap(CourierAvailability::getCourierId, Function.identity()));
        return ids.stream().filter(names::containsKey)
                .map(id -> new PendingDeliveryResponse.Candidate(id, names.get(id).getName(),
                        rows.get(id).getFreeSince()))
                .toList();
    }

    private static PendingDeliveryResponse response(Order order, Delivery delivery, UUID courierId,
            boolean courierDisabled, List<PendingDeliveryResponse.Candidate> candidates) {
        return new PendingDeliveryResponse(order.getId(), order.getStatus().name(), order.getRestaurantName(),
                order.getDeliveryDistrict(), delivery.getReadyAt(), courierId, courierDisabled, candidates);
    }
}
