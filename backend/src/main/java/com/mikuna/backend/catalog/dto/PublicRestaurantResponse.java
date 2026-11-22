package com.mikuna.backend.catalog.dto;

import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import java.util.UUID;

/**
 * Restaurante tal como lo ve el público. Nunca incluye dirección, teléfono, titular, estado de
 * aprobación ni datos de cuenta.
 */
public record PublicRestaurantResponse(UUID id, String name, String category, String description, String zone,
        AttentionStatus attention, String logoUrl, String coverUrl, Double distanceKm, int branchCount) {

    public static PublicRestaurantResponse from(Restaurant restaurant, AttentionStatus attention,
            String fallbackCoverUrl) {
        return from(restaurant, attention, fallbackCoverUrl, null, 1);
    }

    /** distanceKm es la distancia al punto de entrega (si se conoce); branchCount, las sucursales de la marca. */
    public static PublicRestaurantResponse from(Restaurant restaurant, AttentionStatus attention,
            String fallbackCoverUrl, Double distanceKm, int branchCount) {
        return new PublicRestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getCategory(),
                restaurant.getDescription(), restaurant.getZone(), attention, restaurant.getLogoUrl(),
                restaurant.getCoverUrl() != null ? restaurant.getCoverUrl() : fallbackCoverUrl, distanceKm, branchCount);
    }
}
