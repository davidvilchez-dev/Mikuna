package com.mikuna.backend.catalog.dto;

import com.mikuna.backend.catalog.model.Product;
import java.math.BigDecimal;
import java.util.UUID;

/** Producto tal como lo ve el público: sin disponibilidad, versión, restaurante ni identificadores de imagen. */
public record PublicProductResponse(UUID id, String name, String description, String category, BigDecimal price,
        String imageUrl) {

    public static PublicProductResponse from(Product product) {
        return new PublicProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getCategory(), product.getPrice(), product.getImageUrl());
    }
}
