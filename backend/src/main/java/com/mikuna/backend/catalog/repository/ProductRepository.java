package com.mikuna.backend.catalog.repository;

import com.mikuna.backend.catalog.model.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Producto propio y no retirado; uno ajeno o retirado es indistinguible de uno inexistente. */
    Optional<Product> findByIdAndRestaurantIdAndActiveTrue(UUID id, UUID restaurantId);

    /** Gestión: productos no retirados del restaurante, disponibles o no. */
    Page<Product> findByRestaurantIdAndActiveTrue(UUID restaurantId, Pageable pageable);

    /** Público: solo activos y disponibles. */
    Page<Product> findByRestaurantIdAndActiveTrueAndAvailableTrue(UUID restaurantId, Pageable pageable);
}
