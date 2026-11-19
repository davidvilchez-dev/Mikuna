package com.mikuna.backend.address.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos de una dirección de entrega. El cliente propietario nunca viaja en el cuerpo. */
public record AddressRequest(
        @Size(max = 50, message = "La etiqueta admite hasta 50 caracteres")
        String label,

        @NotBlank(message = "La dirección es obligatoria")
        @Size(min = 5, max = 150, message = "La dirección admite de 5 a 150 caracteres")
        String street,

        @NotBlank(message = "El distrito es obligatorio")
        @Size(min = 2, max = 100, message = "El distrito admite de 2 a 100 caracteres")
        String district,

        @Size(max = 200, message = "La referencia admite hasta 200 caracteres")
        String reference,

        @Pattern(regexp = "^$|^[0-9 +()-]{7,20}$", message = "El teléfono debe tener de 7 a 15 dígitos")
        String contactPhone,

        @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
        Double latitude,

        @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
        Double longitude) {

    /** El punto del mapa se envía completo o no se envía: media coordenada no ubica nada. */
    @JsonIgnore
    @AssertTrue(message = "La ubicación en el mapa debe incluir latitud y longitud")
    public boolean isLocationComplete() {
        return (latitude == null) == (longitude == null);
    }
}
