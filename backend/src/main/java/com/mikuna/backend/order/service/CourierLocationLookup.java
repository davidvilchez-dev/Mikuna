package com.mikuna.backend.order.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Lectura de la ubicación vigente del repartidor de un pedido; la implementa {@code delivery} (spec 009). */
public interface CourierLocationLookup {

    record Position(double latitude, double longitude, Instant recordedAt) {
    }

    /** Ubicación vigente del repartidor asignado a la entrega de ese pedido, si la hay. */
    Optional<Position> latestFor(UUID orderId);
}
