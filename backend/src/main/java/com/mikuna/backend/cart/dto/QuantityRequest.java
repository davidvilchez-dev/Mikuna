package com.mikuna.backend.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuantityRequest(
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser un entero entre 1 y 20")
        @Max(value = 20, message = "La cantidad debe ser un entero entre 1 y 20")
        Integer quantity) {
}
