package com.mikuna.backend.restaurant.dto;

import com.mikuna.backend.restaurant.model.OpeningHour;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Datos del restaurante para su titular; nunca incluye ownerId ni resolvedBy. */
public record RestaurantResponse(UUID id, String name, String category, String description, String address,
        String zone, String phone, RestaurantStatus status, Instant submittedAt, String rejectionReason,
        Instant resolvedAt, boolean available, List<OpeningHourDto> schedule, boolean acceptingOrders, String logoUrl, String coverUrl) {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    public static RestaurantResponse from(Restaurant restaurant, boolean acceptingOrders) {
        return new RestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getCategory(),
                restaurant.getDescription(), restaurant.getAddress(), restaurant.getZone(),
                restaurant.getPhone(), restaurant.getStatus(), restaurant.getSubmittedAt(),
                restaurant.getRejectionReason(), restaurant.getResolvedAt(), restaurant.isAvailable(),
                scheduleOf(restaurant), acceptingOrders, restaurant.getLogoUrl(), restaurant.getCoverUrl());
    }

    static List<OpeningHourDto> scheduleOf(Restaurant restaurant) {
        return restaurant.getSchedule().stream()
                .sorted(Comparator.comparing(OpeningHour::getDay))
                .map(hour -> new OpeningHourDto(hour.getDay().name(),
                        hour.getOpensAt() == null ? null : hour.getOpensAt().format(HOUR),
                        hour.getClosesAt() == null ? null : hour.getClosesAt().format(HOUR)))
                .toList();
    }
}
