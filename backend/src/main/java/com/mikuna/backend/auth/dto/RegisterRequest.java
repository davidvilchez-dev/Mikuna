package com.mikuna.backend.auth.dto;

import com.mikuna.backend.account.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro público. Longitud de nombre/correo y formato del correo se validan en el servicio
 * después de recortar y normalizar; cualquier campo adicional (status, id…) se rechaza con 400.
 */
public record RegisterRequest(
        @NotBlank(message = "Ingresa tu nombre") String name,
        @NotBlank(message = "Ingresa tu correo") String email,
        @NotEmpty(message = "Ingresa una contraseña")
        @Size(max = 128, message = "La contraseña es obligatoria y admite hasta 128 caracteres") String password,
        @NotNull(message = "Confirma tu contraseña") String passwordConfirmation,
        @NotNull(message = "Elige un tipo de cuenta") Role role) {
}
