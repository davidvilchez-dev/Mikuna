package com.mikuna.backend.common.exception;

/** La imagen supera el tamaño máximo; se responde 413. */
public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException() {
        super("La imagen supera los 2 MB");
    }
}
