package com.mikuna.backend.common.exception;

/** Las cuentas de administrador no se pueden desactivar, ni la propia (409, {@code ADMIN_ACCOUNT_PROTECTED}). */
public class AdminAccountProtectedException extends RuntimeException {

    public static final String CODE = "ADMIN_ACCOUNT_PROTECTED";

    public AdminAccountProtectedException() {
        super("Las cuentas de administrador no se pueden desactivar");
    }
}
