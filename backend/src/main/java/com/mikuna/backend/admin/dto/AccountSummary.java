package com.mikuna.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de un listado de cuentas. {@code id} es el identificador del recurso (cuenta en usuarios y repartidores,
 * restaurante en restaurantes); {@code accountId} es siempre la cuenta de acceso. Nunca incluye credenciales.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountSummary(UUID id, UUID accountId, String name, String email, String role,
        boolean accessEnabled, Boolean operationEnabled, String registrationStatus, Instant createdAt) {
}
