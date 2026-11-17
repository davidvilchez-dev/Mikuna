package com.mikuna.backend.catalog.dto;

import jakarta.validation.constraints.NotNull;

/** Cambio de disponibilidad de un producto sobre la versión que el cliente vio. */
public record AvailabilityRequest(
        @NotNull(message = "Indica si el producto está disponible") Boolean available,
        @NotNull(message = "La versión es obligatoria") Long version) {
}
