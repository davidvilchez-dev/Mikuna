package com.mikuna.backend.auth.dto;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.AccountStatus;
import com.mikuna.backend.account.model.Role;
import java.util.UUID;

/** Datos públicos de la cuenta; nunca incluye la contraseña ni su hash. */
public record AccountResponse(
        UUID id, String name, String email, Role role, AccountStatus status) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getEmail(), account.getRole(),
                account.getStatus());
    }
}
