package com.mikuna.backend.common.exception;

/** El servicio de imágenes no responde o no está configurado; se responde 503 sin guardar nada. */
public class ImageUnavailableException extends RuntimeException {

    public ImageUnavailableException() {
        super("El servicio de imágenes no está disponible");
    }
}
