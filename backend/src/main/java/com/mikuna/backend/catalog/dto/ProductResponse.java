package com.mikuna.backend.catalog.dto;

import com.mikuna.backend.catalog.model.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Producto tal como lo ve su restaurante (incluye disponibilidad y versión). */
public record ProductResponse(UUID id, String name, String description, String category, BigDecimal price,
        String imageUrl, boolean available, long version, Instant createdAt, Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getCategory(), product.getPrice(), product.getImageUrl(), product.isAvailable(),
                product.getVersion(), product.getCreatedAt(), product.getUpdatedAt());
    }
}
