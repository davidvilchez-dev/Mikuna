package com.mikuna.backend.support;

import com.mikuna.backend.catalog.model.Product;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Datos de prueba del catálogo insertados con SQL para no depender de los servicios bajo prueba. */
public final class TestProducts {

    /** Firmas mínimas de los formatos admitidos (el validador comprueba la firma del archivo). */
    public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0};
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    public static final byte[] WEBP = concat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[] {4, 0, 0, 0},
            "WEBP".getBytes(StandardCharsets.US_ASCII));

    private TestProducts() {
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] all = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, all, offset, part.length);
            offset += part.length;
        }
        return all;
    }

    /**
     * Restaurante aprobado, disponible y abierto todos los días de 00:00 a 23:59 (siempre dentro de
     * horario), asociado a una cuenta existente.
     */
    public static UUID approvedRestaurant(JdbcTemplate jdbc, UUID ownerId, String name, String category, String zone) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into restaurantes (id, owner_id, name, category, description, address, zone, phone, status,
                    submitted_at, available, version)
                values (?, ?, ?, ?, 'Comida casera', 'Jr. Lima 123', ?, '966123456', 'APPROVED', ?, true, 0)""",
                id, ownerId, name, category, zone, Timestamp.from(Instant.now()));
        for (DayOfWeek day : DayOfWeek.values()) {
            jdbc.update("insert into horarios_restaurante (restaurante_id, day_of_week, opens_at, closes_at)"
                    + " values (?, ?, '00:00', '23:59')", id, day.name());
        }
        return id;
    }

    /** Cierra todos los días del horario (fuera de horario). */
    public static void closeAllDays(JdbcTemplate jdbc, UUID restaurantId) {
        jdbc.update("update horarios_restaurante set opens_at = null, closes_at = null where restaurante_id = ?",
                restaurantId);
    }

    public static UUID product(JdbcTemplate jdbc, UUID restaurantId, String name, String category, String price,
            boolean available, boolean active) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into productos (id, restaurant_id, name, name_key, description, category, category_key, price,
                    image_url, image_public_id, available, active, withdrawn_at, created_at, updated_at, version)
                values (?, ?, ?, ?, null, ?, ?, ?, 'https://img.test/p.jpg', 'mikuna/products/p', ?, ?, ?, ?, ?, 0)""",
                id, restaurantId, name, Product.key(name), category, Product.key(category), new BigDecimal(price),
                available, active ? Boolean.TRUE : null, active ? null : now, now, now);
        return id;
    }
}
