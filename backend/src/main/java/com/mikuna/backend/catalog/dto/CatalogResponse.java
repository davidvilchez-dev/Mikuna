package com.mikuna.backend.catalog.dto;

/** Catálogo público de un restaurante visible: sus datos públicos y una página de productos disponibles. */
public record CatalogResponse(PublicRestaurantResponse restaurant, PageResponse<PublicProductResponse> products) {
}
