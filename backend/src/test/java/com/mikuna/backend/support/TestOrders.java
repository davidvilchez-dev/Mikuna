package com.mikuna.backend.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Carritos listos para confirmar, insertados con SQL para no depender de los servicios bajo prueba. */
public final class TestOrders {

    private TestOrders() {
    }

    /** Carrito del cliente en un restaurante, con la dirección seleccionada; las líneas se añaden aparte. */
    public static UUID cart(JdbcTemplate jdbc, UUID customerId, UUID restaurantId, UUID addressId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into carritos (id, customer_id, restaurant_id, selected_address_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)""", id, customerId, restaurantId, addressId, now, now);
        return id;
    }

    public static void line(JdbcTemplate jdbc, UUID cartId, UUID productId, int quantity) {
        jdbc.update("insert into lineas_carrito (id, cart_id, product_id, quantity, added_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), cartId, productId, quantity, Timestamp.from(Instant.now()));
    }
}
