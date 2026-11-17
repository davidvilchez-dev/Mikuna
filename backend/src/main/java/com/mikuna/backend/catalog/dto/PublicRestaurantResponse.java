package com.mikuna.backend.catalog.dto;

import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import java.util.UUID;

/**
 * Restaurante tal como lo ve el público. Nunca incluye dirección, teléfono, titular, estado de
 * aprobación ni datos de cuenta.
 */
public record PublicRestaurantResponse(UUID id, String name, String category, String description, String zone,
        AttentionStatus attention) {

    public static PublicRestaurantResponse from(Restaurant restaurant, AttentionStatus attention) {
        return new PublicRestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getCategory(),
                restaurant.getDescription(), restaurant.getZone(), attention);
    }
}
