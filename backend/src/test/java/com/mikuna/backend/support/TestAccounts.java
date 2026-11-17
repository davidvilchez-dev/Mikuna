package com.mikuna.backend.support;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.AccountStatus;
import com.mikuna.backend.account.model.Role;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Cuentas en memoria para pruebas unitarias (sin base de datos). */
public final class TestAccounts {

    private TestAccounts() {
    }

    public static Account account(Role role, String email, String passwordHash) {
        Account account = Account.create("Titular " + role, email, passwordHash, role);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }

    public static Account disabled(Account account) {
        ReflectionTestUtils.setField(account, "status", AccountStatus.DISABLED.name());
        return account;
    }
}
