package com.mikuna.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Cambio de estado de un pedido: de qué estado a cuál, cuándo, quién (nulo si fue el sistema) y el motivo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusChangeEntry(String from, String to, Instant changedAt, UUID actorId, String actorName,
        String actorRole, String reason) {
}
