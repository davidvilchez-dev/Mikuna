package com.mikuna.backend.notification.service;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.notification.model.Notification;
import com.mikuna.backend.notification.model.NotificationType;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderPlacedEvent;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChangedEvent;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla fija de destinatarios de los avisos (FR-010, research §6). La parte pura ({@link #plan}) decide a quién
 * y qué se avisa; el resto solo reúne los participantes del pedido. Quien origina la acción no recibe su aviso.
 */
@Component
public class NotificationRecipients {

    /** Aviso por registrar: destinatario, tipo y mensaje ya redactado. */
    public record Draft(UUID recipientId, NotificationType type, String message) {
    }

    /** Participantes del pedido; el repartidor es nulo si no hay uno asignado. */
    public record Participants(UUID orderId, UUID customerId, UUID restaurantOwnerId, UUID courierId) {
    }

    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final DeliveryRepository deliveries;

    public NotificationRecipients(OrderRepository orders, RestaurantRepository restaurants,
            DeliveryRepository deliveries) {
        this.orders = orders;
        this.restaurants = restaurants;
        this.deliveries = deliveries;
    }

    /**
     * Se lee en una transacción nueva: tras el commit de origen su contexto de persistencia puede conservar
     * entidades anteriores a una actualización masiva (por ejemplo, la entrega sin repartidor).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Draft> forStatusChange(OrderStatusChangedEvent event) {
        return participants(event.orderId()).map(p -> plan(event, p)).orElse(List.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Draft> forPlaced(OrderPlacedEvent event) {
        return participants(event.orderId())
                .map(p -> List.of(new Draft(p.restaurantOwnerId(), NotificationType.NEW_ORDER,
                        "Nuevo pedido " + shortId(p.orderId()) + " por atender")))
                .filter(drafts -> drafts.get(0).recipientId() != null).orElse(List.of());
    }

    /** Avisos de un cambio de estado según la tabla de destinatarios, sin el aviso de quien lo originó. */
    public static List<Draft> plan(OrderStatusChangedEvent event, Participants p) {
        String ref = "pedido " + shortId(p.orderId());
        List<Draft> drafts = new ArrayList<>();
        OrderStatus to = event.to();
        switch (to) {
            case ACCEPTED -> add(drafts, p.customerId(), NotificationType.ACCEPTED,
                    "El restaurante aceptó tu " + ref);
            case REJECTED -> add(drafts, p.customerId(), NotificationType.REJECTED,
                    "El restaurante rechazó tu " + ref);
            case PREPARING -> add(drafts, p.customerId(), NotificationType.PREPARING,
                    "Tu " + ref + " está en preparación");
            case READY -> {
                // El retroceso por rechazo del repartidor solo reasigna la entrega: no avisa a nadie.
                if (event.from() != OrderStatus.ASSIGNED) {
                    add(drafts, p.customerId(), NotificationType.READY, "Tu " + ref + " está listo para recoger");
                }
            }
            case CANCELLED -> {
                add(drafts, p.customerId(), NotificationType.CANCELLED, "Se canceló tu " + ref);
                add(drafts, p.restaurantOwnerId(), NotificationType.CANCELLED, "Se canceló el " + ref);
                add(drafts, p.courierId(), NotificationType.CANCELLED, "Se canceló el " + ref + " asignado");
            }
            case ASSIGNED -> {
                add(drafts, p.customerId(), NotificationType.ASSIGNED, "Un repartidor tomó tu " + ref);
                add(drafts, p.restaurantOwnerId(), NotificationType.ASSIGNED,
                        "Un repartidor fue asignado al " + ref);
                add(drafts, p.courierId(), NotificationType.ASSIGNED, "Se te asignó el " + ref);
            }
            case PICKED_UP -> {
                add(drafts, p.customerId(), NotificationType.PICKED_UP, "Tu " + ref + " va en camino");
                add(drafts, p.restaurantOwnerId(), NotificationType.PICKED_UP,
                        "El repartidor recogió el " + ref);
            }
            case DELIVERED -> {
                add(drafts, p.customerId(), NotificationType.DELIVERED, "Tu " + ref + " fue entregado");
                add(drafts, p.restaurantOwnerId(), NotificationType.DELIVERED, "Se entregó el " + ref);
            }
            default -> {
                // PENDING no es un cambio: el pedido nuevo se avisa con OrderPlacedEvent.
            }
        }
        return drafts.stream().filter(d -> !d.recipientId().equals(event.actorId())).toList();
    }

    private static void add(List<Draft> drafts, UUID recipientId, NotificationType type, String message) {
        if (recipientId != null) {
            drafts.add(new Draft(recipientId, type, truncate(message)));
        }
    }

    private static String truncate(String message) {
        return message.length() <= Notification.MESSAGE_MAX ? message
                : message.substring(0, Notification.MESSAGE_MAX);
    }

    static String shortId(UUID orderId) {
        return "#" + orderId.toString().substring(0, 8);
    }

    private Optional<Participants> participants(UUID orderId) {
        Optional<Order> order = orders.findById(orderId);
        if (order.isEmpty()) {
            return Optional.empty();
        }
        UUID owner = restaurants.findById(order.get().getRestaurantId()).map(Restaurant::getOwnerId).orElse(null);
        UUID courier = deliveries.findByOrderId(orderId).map(Delivery::getCourierId).orElse(null);
        return Optional.of(new Participants(orderId, order.get().getCustomerId(), owner, courier));
    }
}
