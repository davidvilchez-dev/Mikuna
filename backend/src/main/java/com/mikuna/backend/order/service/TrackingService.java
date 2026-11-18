package com.mikuna.backend.order.service;

import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.order.dto.TrackingResponse;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChange;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.repository.OrderStatusChangeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seguimiento del pedido propio (RF-27, RF-28). El cliente sale del token; un pedido ajeno o inexistente es 404.
 * La ubicación del repartidor solo se devuelve con el pedido Recogido (FR-007, FR-009).
 */
@Service
public class TrackingService {

    private final OrderRepository orders;
    private final OrderStatusChangeRepository changes;
    private final CourierLocationLookup locations;

    public TrackingService(OrderRepository orders, OrderStatusChangeRepository changes,
            CourierLocationLookup locations) {
        this.orders = orders;
        this.changes = changes;
        this.locations = locations;
    }

    @Transactional(readOnly = true)
    public TrackingResponse get(UUID customerId, UUID orderId) {
        Order order = orders.findByIdAndCustomerId(orderId, customerId).orElseThrow(OrderNotFoundException::new);
        List<TrackingResponse.Stage> stages = stages(order);
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.PICKED_UP) {
            Optional<CourierLocationLookup.Position> position = locations.latestFor(orderId);
            if (position.isPresent()) {
                CourierLocationLookup.Position p = position.get();
                return new TrackingResponse(orderId, status.name(), stages,
                        new TrackingResponse.CourierLocation(p.latitude(), p.longitude(), p.recordedAt()), null);
            }
            return new TrackingResponse(orderId, status.name(), stages, null, TrackingResponse.NO_LOCATION_YET);
        }
        String reason = isFinished(status) ? TrackingResponse.FINISHED : TrackingResponse.NOT_PICKED_UP;
        return new TrackingResponse(orderId, status.name(), stages, null, reason);
    }

    private static boolean isFinished(OrderStatus status) {
        return status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED
                || status == OrderStatus.REJECTED;
    }

    /**
     * Una etapa por estado alcanzado, con la fecha de su última vez. El retroceso por rechazo del repartidor
     * (Asignado a Listo) no es una etapa nueva: la entrega solo se reasigna.
     */
    private List<TrackingResponse.Stage> stages(Order order) {
        Map<OrderStatus, Instant> reached = new LinkedHashMap<>();
        reached.put(OrderStatus.PENDING, order.getCreatedAt());
        for (OrderStatusChange change : changes.findByOrderIdOrderByChangedAtAsc(order.getId())) {
            if (change.getFromStatus() == OrderStatus.ASSIGNED && change.getToStatus() == OrderStatus.READY) {
                continue;
            }
            reached.put(change.getToStatus(), change.getChangedAt());
        }
        List<TrackingResponse.Stage> stages = new ArrayList<>();
        reached.forEach((status, at) -> stages.add(new TrackingResponse.Stage(status.name(), at)));
        return stages;
    }
}
