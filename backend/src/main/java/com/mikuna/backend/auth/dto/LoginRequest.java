package com.mikuna.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Ingresa tu correo") @Size(max = 320, message = "Correo demasiado largo") String email,
        @NotBlank(message = "Ingresa tu contraseña") @Size(max = 128, message = "Contraseña demasiado larga") String password) {
}
