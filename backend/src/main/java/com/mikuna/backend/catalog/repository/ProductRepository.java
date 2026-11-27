package com.mikuna.backend.catalog.repository;

import com.mikuna.backend.catalog.model.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Producto propio y no retirado; uno ajeno o retirado es indistinguible de uno inexistente. */
    Optional<Product> findFirstByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc(UUID restaurantId);

    Optional<Product> findByIdAndRestaurantIdAndActiveTrue(UUID id, UUID restaurantId);

    /** Gestión: productos no retirados del restaurante, disponibles o no. */
    Page<Product> findByRestaurantIdAndActiveTrue(UUID restaurantId, Pageable pageable);

    /** Público: solo activos y disponibles. */
    Page<Product> findByRestaurantIdAndActiveTrueAndAvailableTrue(UUID restaurantId, Pageable pageable);

    /** Asistente (spec 013): platos activos y disponibles de varios restaurantes a la vez. */
    List<Product> findByRestaurantIdInAndActiveTrueAndAvailableTrue(Collection<UUID> restaurantIds);

    /** Público, todo el catálogo disponible de un restaurante en el orden de la carta (caché de la spec 015). */
    List<Product> findByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc(UUID restaurantId);

    /**
     * Portada de respaldo de varios restaurantes en una sola consulta (spec 015, research §7): la imagen del primer
     * plato activo y disponible en el orden de la carta, el mismo criterio que
     * {@link #findFirstByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc}.
     */
    @Query(value = """
            select distinct on (restaurant_id) restaurant_id as restaurantId, image_url as imageUrl
            from productos
            where restaurant_id in (:ids) and active = true and available = true
            order by restaurant_id, category, name""", nativeQuery = true)
    List<FirstImage> firstAvailableImageByRestaurant(@Param("ids") Collection<UUID> ids);

    interface FirstImage {
        UUID getRestaurantId();

        String getImageUrl();
    }
}
