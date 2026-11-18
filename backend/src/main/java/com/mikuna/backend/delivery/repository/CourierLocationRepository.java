package com.mikuna.backend.delivery.repository;

import com.mikuna.backend.delivery.model.CourierLocation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourierLocationRepository extends JpaRepository<CourierLocation, UUID> {

    /**
     * Inserta o reemplaza la ubicación vigente del repartidor en una sola sentencia: solo prospera si es más
     * reciente que la guardada o si corresponde a otro pedido, así una petición tardía nunca pisa una nueva.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into ubicaciones_repartidor (courier_id, order_id, latitude, longitude, recorded_at) "
            + "values (:courierId, :orderId, :latitude, :longitude, :recordedAt) "
            + "on conflict (courier_id) do update set order_id = excluded.order_id, "
            + "latitude = excluded.latitude, longitude = excluded.longitude, recorded_at = excluded.recorded_at "
            + "where ubicaciones_repartidor.recorded_at < excluded.recorded_at "
            + "or ubicaciones_repartidor.order_id <> excluded.order_id", nativeQuery = true)
    int upsert(@Param("courierId") UUID courierId, @Param("orderId") UUID orderId,
            @Param("latitude") double latitude, @Param("longitude") double longitude,
            @Param("recordedAt") Instant recordedAt);

    Optional<CourierLocation> findByCourierIdAndOrderId(UUID courierId, UUID orderId);

    @Modifying(flushAutomatically = true)
    @Query("delete from CourierLocation l where l.orderId = :orderId")
    int deleteByOrderId(@Param("orderId") UUID orderId);
}
