package com.mikuna.backend.restaurant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos generales del restaurante (RF-05/RF-07). Las mismas reglas se revalidan en el servicio. */
public record RestaurantRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 100, message = "El nombre admite de 2 a 100 caracteres") String name,
        @NotBlank(message = "La categoría es obligatoria")
        @Size(min = 2, max = 50, message = "La categoría admite de 2 a 50 caracteres") String category,
        @Size(max = 300, message = "La descripción admite hasta 300 caracteres") String description,
        @NotBlank(message = "La dirección es obligatoria")
        @Size(min = 5, max = 200, message = "La dirección admite de 5 a 200 caracteres") String address,
        @NotBlank(message = "La zona es obligatoria")
        @Size(min = 2, max = 100, message = "La zona admite de 2 a 100 caracteres") String zone,
        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^\\+?[0-9 ]{7,15}$", message = "Ingresa un teléfono válido (7 a 15 dígitos)") String phone,
        @DecimalMin(value = "-90", message = "La latitud no es válida") @DecimalMax(value = "90", message = "La latitud no es válida") Double latitude,
        @DecimalMin(value = "-180", message = "La longitud no es válida") @DecimalMax(value = "180", message = "La longitud no es válida") Double longitude) {

    /** Sin ubicación en el mapa (se puede marcar después). */
    public RestaurantRequest(String name, String category, String description, String address, String zone, String phone) {
        this(name, category, description, address, zone, phone, null, null);
    }
}
