package com.mikuna.backend.catalog.dto;

import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.ScheduleSlot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Datos de un restaurante visible guardados en la caché pública (spec 015, data-model.md). No lleva el estado de
 * atención: se calcula en cada consulta con la hora actual (FR-002). Tampoco dirección, teléfono, estado de
 * aprobación ni datos de cuenta; {@code ownerId}, coordenadas y fecha de envío solo sirven para agrupar y
 * ordenar, y nunca se devuelven.
 */
public record RestaurantSnapshot(UUID id, UUID ownerId, String name, String category, String description,
        String zone, String logoUrl, String coverUrl, String fallbackCoverUrl, Double latitude, Double longitude,
        Instant submittedAt, boolean available, List<ScheduleSlot> schedule) {

    /** {@code fallbackCoverUrl}: imagen del primer plato disponible; solo se guarda si no hay portada propia. */
    public static RestaurantSnapshot from(Restaurant restaurant, String fallbackCoverUrl) {
        return new RestaurantSnapshot(restaurant.getId(), restaurant.getOwnerId(), restaurant.getName(),
                restaurant.getCategory(), restaurant.getDescription(), restaurant.getZone(), restaurant.getLogoUrl(),
                restaurant.getCoverUrl(), restaurant.getCoverUrl() != null ? null : fallbackCoverUrl,
                restaurant.getLatitude(), restaurant.getLongitude(), restaurant.getSubmittedAt(),
                restaurant.isAvailable(), restaurant.getSchedule().stream().map(ScheduleSlot::of).toList());
    }
}
