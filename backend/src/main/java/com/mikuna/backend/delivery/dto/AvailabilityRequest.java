package com.mikuna.backend.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code PUT /api/courier/availability}. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AvailabilityRequest(@NotNull(message = "Indica si estás disponible") Boolean available) {
}
