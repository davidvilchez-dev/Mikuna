package com.mikuna.backend.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 300, message = "El motivo admite hasta 300 caracteres") String reason) {
}
