package com.mikuna.backend.restaurant.dto;

import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Vista del administrador: datos del restaurante más nombre y correo del titular. */
public record AdminRestaurantResponse(UUID id, String name, String category, String description, String address,
        String zone, String phone, RestaurantStatus status, Instant submittedAt, String rejectionReason,
        Instant resolvedAt, boolean available, List<OpeningHourDto> schedule, boolean acceptingOrders,
        String ownerName, String ownerEmail) {

    public static AdminRestaurantResponse from(Restaurant restaurant, boolean acceptingOrders, String ownerName,
            String ownerEmail) {
        return new AdminRestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getCategory(),
                restaurant.getDescription(), restaurant.getAddress(), restaurant.getZone(), restaurant.getPhone(),
                restaurant.getStatus(), restaurant.getSubmittedAt(), restaurant.getRejectionReason(),
                restaurant.getResolvedAt(), restaurant.isAvailable(), RestaurantResponse.scheduleOf(restaurant),
                acceptingOrders, ownerName, ownerEmail);
    }
}
