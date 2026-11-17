package com.mikuna.backend.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Datos de un producto (parte JSON «product» del multipart). {@code version} es obligatoria al editar
 * y no se admite al crear; el servicio lo comprueba. La imagen viaja en su propia parte.
 */
public record ProductRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 100, message = "El nombre admite de 2 a 100 caracteres")
        String name,

        @NotBlank(message = "La categoría es obligatoria")
        @Size(min = 2, max = 50, message = "La categoría admite de 2 a 50 caracteres")
        String category,

        @Size(max = 300, message = "La descripción admite hasta 300 caracteres")
        String description,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
        @DecimalMax(value = "9999.99", message = "El precio máximo es 9999.99")
        @Digits(integer = 4, fraction = 2, message = "El precio admite hasta 2 decimales")
        BigDecimal price,

        Long version) {
}
