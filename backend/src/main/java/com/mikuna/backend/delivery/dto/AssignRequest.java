package com.mikuna.backend.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Cuerpo de la asignación manual: el repartidor elegido por el administrador. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AssignRequest(@NotNull(message = "Indica el repartidor") UUID courierId) {
}
