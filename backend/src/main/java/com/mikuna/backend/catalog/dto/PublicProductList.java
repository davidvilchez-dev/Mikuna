package com.mikuna.backend.catalog.dto;

import java.util.List;

/** Productos activos y disponibles de un restaurante, ya ordenados; envoltorio tipado para la caché (spec 015). */
public record PublicProductList(List<PublicProductResponse> items) {
}
