package com.mikuna.backend.delivery.dto;

import com.mikuna.backend.delivery.model.CourierAvailability;
import java.time.Instant;

/** Disponibilidad vigente del repartidor; sin fila equivale a no disponible. */
public record AvailabilityResponse(boolean available, Instant freeSince) {

    public static AvailabilityResponse of(CourierAvailability row) {
        return row == null ? new AvailabilityResponse(false, null)
                : new AvailabilityResponse(row.isAvailable(), row.isAvailable() ? row.getFreeSince() : null);
    }
}
