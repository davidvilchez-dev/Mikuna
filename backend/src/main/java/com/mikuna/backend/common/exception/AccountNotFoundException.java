package com.mikuna.backend.common.exception;

/** Cuenta, restaurante o repartidor inexistente para el administrador (404). */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("Cuenta no encontrada");
    }
}
