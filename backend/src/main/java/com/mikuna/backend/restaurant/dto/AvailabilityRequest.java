package com.mikuna.backend.restaurant.dto;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull(message = "Indica si estás disponible") Boolean available) {
}
