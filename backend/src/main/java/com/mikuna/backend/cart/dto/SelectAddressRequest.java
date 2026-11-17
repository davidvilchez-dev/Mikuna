package com.mikuna.backend.cart.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SelectAddressRequest(@NotNull(message = "La dirección es obligatoria") UUID addressId) {
}
