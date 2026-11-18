package com.mikuna.backend.history.repository;

import com.mikuna.backend.history.model.Rating;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByOrderId(UUID orderId);

    /** Calificaciones de un lote de pedidos: una consulta por página de historial. */
    List<Rating> findByOrderIdIn(Collection<UUID> orderIds);
}
