package com.mikuna.backend.common.exception;

/** Credenciales incorrectas; el mensaje no revela si el correo existe. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Correo o contraseña incorrectos");
    }
}
