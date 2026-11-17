package com.mikuna.backend.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Producto y cantidad a agregar. Nunca precios, importes ni restaurante: salen del catálogo vigente. */
public record AddLineRequest(
        @NotNull(message = "El producto es obligatorio") UUID productId,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser un entero entre 1 y 20")
        @Max(value = 20, message = "La cantidad debe ser un entero entre 1 y 20")
        Integer quantity) {
}
