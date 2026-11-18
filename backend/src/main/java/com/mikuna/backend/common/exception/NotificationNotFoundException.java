package com.mikuna.backend.common.exception;

/** Aviso inexistente o de otro usuario (404): no se revela su existencia. */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Aviso no encontrado");
    }
}
