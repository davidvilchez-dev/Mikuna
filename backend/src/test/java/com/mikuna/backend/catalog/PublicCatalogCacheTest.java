package com.mikuna.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.catalog.service.PublicCatalogCache;
import com.mikuna.backend.catalog.service.PublicCatalogService;
import com.mikuna.backend.common.cache.CacheNames;
import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.restaurant.service.OrderEligibility;
import com.mikuna.backend.support.TestRestaurants;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Spec 015, historia 1: la consulta pública sale de la caché (proxy real de Spring con Caffeine) y el estado de
 * atención se calcula en cada petición. Sin base de datos: los repositorios son simulados para contar lecturas.
 */
@SpringJUnitConfig(PublicCatalogCacheTest.Config.class)
class PublicCatalogCacheTest {

    /** Lunes 16/11/2026 a las 20:59 en Lima (UTC-5); el restaurante de prueba atiende el lunes de 09:00 a 21:00. */
    private static final Instant MONDAY_2059 = Instant.parse("2026-11-17T01:59:00Z");

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        MovableClock clock() {
            return new MovableClock();
        }

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager(CacheNames.PUBLIC_RESTAURANTS,
                    CacheNames.PUBLIC_CATALOG);
            manager.setCaffeine(Caffeine.newBuilder().recordStats());
            return manager;
        }

        @Bean
        RestaurantRepository restaurantRepository() {
            return mock(RestaurantRepository.class);
        }

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        PublicCatalogCache publicCatalogCache(RestaurantRepository restaurants, ProductRepository products,
                CacheManager cacheManager) {
            return new PublicCatalogCache(restaurants, products, cacheManager);
        }

        @Bean
        OrderEligibility orderEligibility(MovableClock clock) {
            return new OrderEligibility(clock, "America/Lima");
        }

        @Bean
        PublicCatalogService publicCatalogService(PublicCatalogCache cache, OrderEligibility eligibility) {
            return new PublicCatalogService(cache, eligibility);
        }
    }

    @Autowired PublicCatalogService service;
    @Autowired RestaurantRepository restaurants;
    @Autowired ProductRepository products;
    @Autowired CacheManager cacheManager;
    @Autowired MovableClock clock;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        reset(restaurants, products);
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        clock.set(MONDAY_2059);
        restaurant = TestRestaurants.approvedOpenOnMonday(UUID.randomUUID(), true);
        when(restaurants.findVisible()).thenReturn(List.of(restaurant));
        when(products.findByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc(restaurant.getId()))
                .thenReturn(List.of(Product.create(restaurant.getId(), "Lomo", "Casero", "Fondos",
                        new BigDecimal("22.50"), "https://img.test/p.jpg", "p", MONDAY_2059)));
    }

    @Test
    void laSegundaConsultaNoLeeLaBaseDeDatosYDevuelveLoMismo() {
        var firstSearch = service.search(null, null, null, 0, 20);
        var firstFilters = service.filters();
        var firstCatalog = service.catalog(restaurant.getId(), 0, 24);

        clearInvocations(restaurants, products);
        assertThat(service.search(null, null, null, 0, 20)).isEqualTo(firstSearch);
        assertThat(service.filters()).isEqualTo(firstFilters);
        assertThat(service.catalog(restaurant.getId(), 0, 24)).isEqualTo(firstCatalog);
        assertThat(service.catalog(restaurant.getId(), 1, 24).products().totalItems()).isEqualTo(1);

        verifyNoInteractions(restaurants, products);
    }

    @Test
    void elEstadoDeAtencionSeCalculaEnCadaConsultaAunqueLosDatosEstenGuardados() {
        assertThat(service.search(null, null, null, 0, 20).items().get(0).attention()).isEqualTo(AttentionStatus.OPEN);
        assertThat(service.catalog(restaurant.getId(), 0, 24).restaurant().attention()).isEqualTo(AttentionStatus.OPEN);

        clock.set(MONDAY_2059.plusSeconds(120)); // 21:01, sin cambiar datos ni descartar la caché

        assertThat(service.search(null, null, null, 0, 20).items().get(0).attention())
                .isEqualTo(AttentionStatus.OUTSIDE_HOURS);
        assertThat(service.catalog(restaurant.getId(), 0, 24).restaurant().attention())
                .isEqualTo(AttentionStatus.OUTSIDE_HOURS);
        verify(restaurants, times(1)).findVisible();
    }

    @Test
    void lasBusquedasDeTextoLibreNoCreanEntradasNuevas() {
        for (String text : List.of("lomo", "pollo", "zzz", "a".repeat(300), "pic")) {
            service.search(text, null, null, 0, 20);
            service.search(null, text, text, 0, 5);
        }

        assertThat(nativeSize(CacheNames.PUBLIC_RESTAURANTS)).isEqualTo(1);
        verify(restaurants, times(1)).findVisible();
    }

    @Test
    void laCartaGuardadaDeUnRestauranteQueDejoDeSerVisibleNoSeSirve() {
        service.catalog(restaurant.getId(), 0, 24);
        when(restaurants.findVisible()).thenReturn(List.of());
        cacheManager.getCache(CacheNames.PUBLIC_RESTAURANTS).clear(); // como haría el descarte al suspenderlo

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.catalog(restaurant.getId(), 0, 24))
                .isInstanceOf(com.mikuna.backend.common.exception.RestaurantNotFoundException.class);
        assertThat(nativeSize(CacheNames.PUBLIC_CATALOG)).isEqualTo(1);
    }

    @Test
    void cadaRestauranteTieneSuPropiaCarta() {
        Restaurant other = TestRestaurants.approvedOpenOnMonday(UUID.randomUUID(), true);
        when(restaurants.findVisible()).thenReturn(List.of(restaurant, other));
        when(products.findByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc(other.getId()))
                .thenReturn(List.of());

        assertThat(service.catalog(restaurant.getId(), 0, 24).products().totalItems()).isEqualTo(1);
        assertThat(service.catalog(other.getId(), 0, 24).products().totalItems()).isZero();
        assertThat(nativeSize(CacheNames.PUBLIC_CATALOG)).isEqualTo(2);
        verify(products, times(1)).firstAvailableImageByRestaurant(any());
    }

    private long nativeSize(String cacheName) {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(cacheName);
        cache.getNativeCache().cleanUp();
        return cache.getNativeCache().estimatedSize();
    }

    /** Reloj que la prueba puede adelantar. */
    static final class MovableClock extends Clock {

        private volatile Instant now = Instant.EPOCH;

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
