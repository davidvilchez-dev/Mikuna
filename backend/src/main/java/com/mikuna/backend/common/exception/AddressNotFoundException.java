package com.mikuna.backend.common.exception;

/** Dirección inexistente o de otro cliente; se responde 404 sin distinguir los casos. */
public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException() {
        super("Dirección no encontrada");
    }
}
