package com.mikuna.backend.catalog.dto;

import java.util.List;

/** Opciones de filtro: categorías y zonas existentes entre los restaurantes visibles. */
public record FiltersResponse(List<String> categories, List<String> zones) {
}
