package com.mikuna.backend.catalog.dto;

import com.mikuna.backend.restaurant.model.AttentionStatus;
import java.util.UUID;

/**
 * Restaurante tal como lo ve el público. Nunca incluye dirección, teléfono, titular, estado de
 * aprobación ni datos de cuenta.
 */
public record PublicRestaurantResponse(UUID id, String name, String category, String description, String zone,
        AttentionStatus attention, String logoUrl, String coverUrl, Double distanceKm, int branchCount) {

    /**
     * Desde la instantánea de la caché (spec 015). Sin portada propia se usa la del primer plato disponible;
     * distanceKm es la distancia al punto de entrega (si se conoce) y branchCount, las sucursales de la marca.
     */
    public static PublicRestaurantResponse from(RestaurantSnapshot restaurant, AttentionStatus attention,
            Double distanceKm, int branchCount) {
        return new PublicRestaurantResponse(restaurant.id(), restaurant.name(), restaurant.category(),
                restaurant.description(), restaurant.zone(), attention, restaurant.logoUrl(),
                restaurant.coverUrl() != null ? restaurant.coverUrl() : restaurant.fallbackCoverUrl(), distanceKm,
                branchCount);
    }
}
