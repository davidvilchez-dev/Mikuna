package com.mikuna.backend.common.exception;

/** La cuenta existe pero no está habilitada para acceder. */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("La cuenta no está habilitada");
    }
}
