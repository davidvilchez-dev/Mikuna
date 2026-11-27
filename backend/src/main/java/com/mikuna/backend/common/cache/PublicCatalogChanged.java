package com.mikuna.backend.common.cache;

import java.util.UUID;

/**
 * Cambió algo que ve el público (spec 015, FR-004). Lo publican los servicios que escriben y lo escucha la caché
 * del catálogo después del commit; así los módulos que escriben no dependen del catálogo. Con {@code restaurantId}
 * también cambió la carta de ese restaurante; la lista de restaurantes se descarta siempre (la portada de
 * respaldo depende del primer plato).
 */
public record PublicCatalogChanged(UUID restaurantId) {

    /** Datos, horario, disponibilidad, imágenes o visibilidad de algún restaurante. */
    public static PublicCatalogChanged restaurants() {
        return new PublicCatalogChanged(null);
    }

    /** La carta de un restaurante. */
    public static PublicCatalogChanged catalog(UUID restaurantId) {
        return new PublicCatalogChanged(restaurantId);
    }
}
