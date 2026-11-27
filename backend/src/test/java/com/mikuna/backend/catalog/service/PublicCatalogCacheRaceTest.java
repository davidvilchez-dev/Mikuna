package com.mikuna.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.catalog.dto.PublicProductList;
import com.mikuna.backend.catalog.dto.RestaurantSnapshotList;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.common.cache.CacheNames;
import com.mikuna.backend.common.cache.PublicCatalogChanged;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

/**
 * Spec 015, caso límite "cambio y consulta simultáneos" (research §3): una lectura que empezó antes del commit y
 * guarda el dato viejo después del primer descarte queda eliminada por el descarte diferido. El ejecutor es falso:
 * la prueba ejecuta el descarte programado sin dormir.
 */
class PublicCatalogCacheRaceTest {

    private static final UUID RESTAURANT = UUID.randomUUID();
    private static final RestaurantSnapshotList OLD_LIST = new RestaurantSnapshotList(List.of());
    private static final PublicProductList OLD_PRODUCTS = new PublicProductList(List.of());

    private CacheManager cacheManager;
    private ScheduledExecutorService scheduler;
    private PublicCatalogCache cache;

    @BeforeEach
    void setUp() {
        cacheManager = new CaffeineCacheManager(CacheNames.PUBLIC_RESTAURANTS, CacheNames.PUBLIC_CATALOG);
        scheduler = mock(ScheduledExecutorService.class);
        cache = new PublicCatalogCache(mock(RestaurantRepository.class), mock(ProductRepository.class), cacheManager,
                scheduler);
    }

    private Cache restaurantsCache() {
        return cacheManager.getCache(CacheNames.PUBLIC_RESTAURANTS);
    }

    private Cache catalogCache() {
        return cacheManager.getCache(CacheNames.PUBLIC_CATALOG);
    }

    @Test
    void elDescarteDiferidoEliminaElDatoViejoGuardadoDespuesDelPrimerDescarte() {
        restaurantsCache().put("all", OLD_LIST);
        catalogCache().put(RESTAURANT, OLD_PRODUCTS);

        cache.on(PublicCatalogChanged.catalog(RESTAURANT));

        assertThat(restaurantsCache().get("all")).isNull();
        assertThat(catalogCache().get(RESTAURANT)).isNull();

        // La lectura lenta, iniciada antes del commit, guarda ahora lo que leyó.
        restaurantsCache().put("all", OLD_LIST);
        catalogCache().put(RESTAURANT, OLD_PRODUCTS);

        ArgumentCaptor<Runnable> second = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(second.capture(), eq(PublicCatalogCache.SECOND_EVICT_MS),
                eq(TimeUnit.MILLISECONDS));
        second.getValue().run();

        assertThat(restaurantsCache().get("all")).isNull();
        assertThat(catalogCache().get(RESTAURANT)).isNull();
    }

    @Test
    void unCambioDeRestaurantesNoTocaLasCartasGuardadas() {
        restaurantsCache().put("all", OLD_LIST);
        catalogCache().put(RESTAURANT, OLD_PRODUCTS);

        cache.on(PublicCatalogChanged.restaurants());

        assertThat(restaurantsCache().get("all")).isNull();
        assertThat(catalogCache().get(RESTAURANT)).isNotNull();
    }

    @Test
    void siNoSePuedeProgramarElSegundoDescarteElPrimeroIgualSeAplica() {
        when(scheduler.schedule(any(Runnable.class), anyLong(), any())).thenThrow(new IllegalStateException("cerrado"));
        restaurantsCache().put("all", OLD_LIST);

        cache.on(PublicCatalogChanged.restaurants());

        assertThat(restaurantsCache().get("all")).isNull();
    }

    @Test
    void unAlmacenCaidoAlDescartarNoRompeLaOperacionConfirmada() {
        CacheManager broken = mock(CacheManager.class);
        Cache failing = mock(Cache.class);
        when(broken.getCache(any())).thenReturn(failing);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis caído")).when(failing).evict(any());
        PublicCatalogCache withBrokenStore = new PublicCatalogCache(mock(RestaurantRepository.class),
                mock(ProductRepository.class), broken, scheduler);

        withBrokenStore.on(PublicCatalogChanged.catalog(RESTAURANT)); // no lanza

        verify(failing, org.mockito.Mockito.times(2)).evict(any());
    }
}
