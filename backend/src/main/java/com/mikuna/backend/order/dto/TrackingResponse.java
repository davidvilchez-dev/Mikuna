package com.mikuna.backend.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seguimiento del pedido para su cliente (RF-27, RF-28): estado actual, etapas cumplidas y, solo con el pedido
 * Recogido, la ubicación vigente del repartidor. Las etapas no exponen quién las originó.
 */
public record TrackingResponse(UUID orderId, String status, List<Stage> stages, CourierLocation courierLocation,
        String locationUnavailableReason, Point destination) {

    public static final String NOT_PICKED_UP = "NOT_PICKED_UP";
    public static final String FINISHED = "FINISHED";
    public static final String NO_LOCATION_YET = "NO_LOCATION_YET";

    public record Stage(String status, Instant at) {
    }

    /** Punto de entrega que marcó el cliente (puede faltar en direcciones antiguas). */
    public record Point(double latitude, double longitude) {
    }

    public record CourierLocation(double latitude, double longitude, Instant updatedAt) {
    }
}
