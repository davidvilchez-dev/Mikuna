package com.mikuna.backend.history.service;

import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.exception.RestaurantNotEnabledException;
import com.mikuna.backend.history.dto.HistoryItem;
import com.mikuna.backend.history.repository.HistoryQueryRepository;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historiales de solo lectura (RF-30). El actor sale siempre del token. Ni el restaurante ni el repartidor necesitan
 * estar habilitados para recibir pedidos o entregas: una cuenta bloqueada ya no inicia sesión (RF-04).
 */
@Service
public class HistoryService {

    /** Estados que incluye el historial del repartidor (entregas a su cargo, concluidas o en curso). */
    public static final List<OrderStatus> COURIER_STATUSES =
            List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.DELIVERED);

    private final HistoryQueryRepository queries;
    private final HistoryAssembler assembler;
    private final RestaurantRepository restaurants;
    private final ZoneId zone;

    public HistoryService(HistoryQueryRepository queries, HistoryAssembler assembler,
            RestaurantRepository restaurants, @Value("${mikuna.timezone}") String timezone) {
        this.queries = queries;
        this.assembler = assembler;
        this.restaurants = restaurants;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryItem> customerHistory(UUID customerId, String status, String from, String to,
            Integer page, Integer size) {
        HistoryFilter filter = HistoryFilter.parse(status, from, to, page, size, zone);
        Page<Order> result = queries.findForCustomer(customerId, filter.statuses(HistoryFilter.allStatuses()),
                filter.fromInstant(), filter.toInstant(), PageRequest.of(filter.page(), filter.size()));
        return respond(result, HistoryAssembler.View.CUSTOMER);
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryItem> restaurantHistory(UUID ownerId, String status, String from, String to,
            Integer page, Integer size) {
        HistoryFilter filter = HistoryFilter.parse(status, from, to, page, size, zone);
        Restaurant restaurant = restaurants.findByOwnerId(ownerId).orElseThrow(RestaurantNotEnabledException::new);
        Page<Order> result = queries.findForRestaurant(restaurant.getId(),
                filter.statuses(HistoryFilter.allStatuses()), filter.fromInstant(), filter.toInstant(),
                PageRequest.of(filter.page(), filter.size()));
        return respond(result, HistoryAssembler.View.RESTAURANT);
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryItem> courierHistory(UUID courierId, String status, String from, String to,
            Integer page, Integer size) {
        HistoryFilter filter = HistoryFilter.parse(status, from, to, page, size, zone);
        Page<Order> result = queries.findForCourier(courierId, filter.statuses(COURIER_STATUSES),
                filter.fromInstant(), filter.toInstant(), PageRequest.of(filter.page(), filter.size()));
        return respond(result, HistoryAssembler.View.COURIER);
    }

    private PageResponse<HistoryItem> respond(Page<Order> result, HistoryAssembler.View view) {
        List<HistoryItem> items = assembler.assemble(result.getContent(), view);
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }
}
