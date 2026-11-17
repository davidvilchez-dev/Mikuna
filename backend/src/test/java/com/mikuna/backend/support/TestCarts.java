package com.mikuna.backend.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Datos de prueba de direcciones insertados con SQL para no depender de los servicios bajo prueba. */
public final class TestCarts {

    private TestCarts() {
    }

    public static UUID address(JdbcTemplate jdbc, UUID customerId, String street, String district) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into direcciones (id, customer_id, label, street, district, reference, contact_phone,
                    created_at, updated_at)
                values (?, ?, null, ?, ?, null, null, ?, ?)""", id, customerId, street, district, now, now);
        return id;
    }
}
