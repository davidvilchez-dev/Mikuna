package com.mikuna.backend.catalog.dto;

import java.util.List;

/** Instantánea de restaurantes visibles; envoltorio tipado para serializar sin tipos polimórficos (research §2). */
public record RestaurantSnapshotList(List<RestaurantSnapshot> items) {
}
