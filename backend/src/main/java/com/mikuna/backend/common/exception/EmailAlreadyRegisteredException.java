package com.mikuna.backend.common.exception;

/** El correo normalizado ya pertenece a una cuenta (con cualquier rol). */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("El correo ya está registrado");
    }
}
