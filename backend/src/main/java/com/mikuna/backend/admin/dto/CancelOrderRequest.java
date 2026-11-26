package com.mikuna.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo obligatorio de la cancelación administrativa (spec 014); lo leen cliente, restaurante y repartidor. */
public record CancelOrderRequest(
        @NotBlank(message = "Indica el motivo de la cancelación")
        @Size(max = 200, message = "El motivo admite hasta 200 caracteres") String reason) {
}
