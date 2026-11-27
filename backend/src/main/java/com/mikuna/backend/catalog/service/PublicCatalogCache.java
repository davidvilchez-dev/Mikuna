package com.mikuna.backend.catalog.service;

import com.mikuna.backend.catalog.dto.PublicProductList;
import com.mikuna.backend.catalog.dto.PublicProductResponse;
import com.mikuna.backend.catalog.dto.RestaurantSnapshot;
import com.mikuna.backend.catalog.dto.RestaurantSnapshotList;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.common.cache.CacheNames;
import com.mikuna.backend.common.cache.PublicCatalogChanged;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Datos de la consulta pública guardados en caché (spec 015, research §2-§3): la instantánea de restaurantes
 * visibles (una sola entrada) y los productos disponibles de cada restaurante. No guarda respuestas ni el estado
 * de atención, que se calculan en cada petición.
 *
 * <p>Los servicios que escriben publican {@link PublicCatalogChanged}: el descarte ocurre después del commit y se
 * repite a {@value #SECOND_EVICT_MS} ms para cubrir la lectura que empezó antes del commit y guardó tarde. El
 * tiempo de vida de la caché es solo un respaldo.
 */
@Component
public class PublicCatalogCache {

    static final long SECOND_EVICT_MS = 1_000;
    private static final String ALL = "all";
    private static final Logger log = LoggerFactory.getLogger(PublicCatalogCache.class);

    private final RestaurantRepository restaurants;
    private final ProductRepository products;
    private final CacheManager cacheManager;
    private final ScheduledExecutorService scheduler;

    @Autowired
    public PublicCatalogCache(RestaurantRepository restaurants, ProductRepository products,
            CacheManager cacheManager) {
        this(restaurants, products, cacheManager, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "public-catalog-evict");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** Para pruebas: el segundo descarte se programa en el ejecutor dado. */
    PublicCatalogCache(RestaurantRepository restaurants, ProductRepository products, CacheManager cacheManager,
            ScheduledExecutorService scheduler) {
        this.restaurants = restaurants;
        this.products = products;
        this.cacheManager = cacheManager;
        this.scheduler = scheduler;
    }

    /** Restaurantes visibles con su portada de respaldo resuelta en una sola consulta (research §7). */
    @Cacheable(cacheNames = CacheNames.PUBLIC_RESTAURANTS, key = "'" + ALL + "'")
    @Transactional(readOnly = true)
    public RestaurantSnapshotList restaurants() {
        List<Restaurant> visible = restaurants.findVisible();
        List<UUID> withoutCover = visible.stream().filter(r -> r.getCoverUrl() == null).map(Restaurant::getId)
                .toList();
        Map<UUID, String> fallbackCovers = withoutCover.isEmpty() ? Map.of()
                : products.firstAvailableImageByRestaurant(withoutCover).stream()
                        .collect(Collectors.toMap(ProductRepository.FirstImage::getRestaurantId,
                                ProductRepository.FirstImage::getImageUrl));
        return new RestaurantSnapshotList(visible.stream()
                .map(r -> RestaurantSnapshot.from(r, fallbackCovers.get(r.getId())))
                .toList());
    }

    /** Todos los productos activos y disponibles del restaurante, en el orden de la carta. */
    @Cacheable(cacheNames = CacheNames.PUBLIC_CATALOG, key = "#restaurantId")
    @Transactional(readOnly = true)
    public PublicProductList products(UUID restaurantId) {
        return new PublicProductList(products
                .findByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc(restaurantId).stream()
                .map(PublicProductResponse::from)
                .toList());
    }

    /**
     * Descarta lo que cambió después del commit (antes, otra lectura podría volver a guardar el dato viejo); sin
     * transacción, en el acto. Si la transacción se revierte no hay nada que descartar.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(PublicCatalogChanged change) {
        Runnable evict = () -> {
            evict(CacheNames.PUBLIC_RESTAURANTS, ALL);
            if (change.restaurantId() != null) {
                evict(CacheNames.PUBLIC_CATALOG, change.restaurantId());
            }
        };
        evict.run();
        try {
            scheduler.schedule(evict, SECOND_EVICT_MS, TimeUnit.MILLISECONDS);
        } catch (RuntimeException rejected) {
            log.warn("No se pudo programar el segundo descarte de la caché pública: {}", rejected.toString());
        }
    }

    /** Un almacén caído no debe romper la escritura ya confirmada (FR-006); el tiempo de vida lo acota. */
    private void evict(String cacheName, Object key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (RuntimeException unavailable) {
            log.warn("Caché no disponible al descartar {}[{}]: {}", cacheName, key, unavailable.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
