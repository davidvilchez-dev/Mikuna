package com.mikuna.backend.common.security;

import com.mikuna.backend.account.model.Role;
import java.util.UUID;

/** Titular autenticado, construido con los datos vigentes en la base de datos. */
public record AccountPrincipal(UUID id, String email, Role role) {
}
