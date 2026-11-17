package com.mikuna.backend.common.exception;

/** Dirección fuera de la zona de reparto cubierta; se responde 400 con el error del campo «district». */
public class OutsideDeliveryZoneException extends RegistrationValidationException {

    public OutsideDeliveryZoneException() {
        super("district", "Esa dirección está fuera de la zona de reparto");
    }
}
