package com.mikuna.backend.cart.repository;

import com.mikuna.backend.cart.model.Cart;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCustomerId(UUID customerId);

    /** Bloquea la fila del carrito: las mutaciones de un mismo cliente se serializan (research §2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.customerId = :customerId")
    Optional<Cart> lockByCustomerId(@Param("customerId") UUID customerId);

    /**
     * Crea el carrito si no existe, sin lanzar excepción ante una creación simultánea (una violación de
     * restricción abortaría la transacción de PostgreSQL).
     */
    @Modifying
    @Query(value = "insert into carritos (id, customer_id, created_at, updated_at) "
            + "values (:id, :customerId, :now, :now) on conflict (customer_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("customerId") UUID customerId, @Param("now") Instant now);

    /** Deja de seleccionar una dirección eliminada. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Cart c set c.selectedAddressId = null "
            + "where c.customerId = :customerId and c.selectedAddressId = :addressId")
    int clearSelection(@Param("customerId") UUID customerId, @Param("addressId") UUID addressId);
}
