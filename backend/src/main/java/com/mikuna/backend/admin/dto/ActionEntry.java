package com.mikuna.backend.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** Acción administrativa sobre una cuenta: qué cambió, cuándo y qué administrador la hizo. */
public record ActionEntry(String action, String scope, Instant createdAt, UUID adminId, String adminName) {
}
