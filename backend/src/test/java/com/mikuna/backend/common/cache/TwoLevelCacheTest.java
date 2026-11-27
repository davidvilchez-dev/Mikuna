package com.mikuna.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * Spec 015, caché de dos niveles: el nivel 1 resuelve las lecturas, el 2 se comparte, los descartes se avisan a las
 * demás instancias y una falla de Redis nunca se propaga. El nivel 2 es un mapa en memoria que hace de "Redis".
 */
class TwoLevelCacheTest {

    private static final UUID RESTAURANT = UUID.randomUUID();

    private ConcurrentMapCacheManager sharedStore;
    private List<String> published;
    private TwoLevelCacheManager instanceA;
    private TwoLevelCacheManager instanceB;

    @BeforeEach
    void setUp() {
        sharedStore = new ConcurrentMapCacheManager(CacheNames.PUBLIC_RESTAURANTS, CacheNames.PUBLIC_CATALOG);
        published = new ArrayList<>();
        // Pub/sub simulado: cada mensaje llega a las dos instancias (también a quien lo envió).
        List<TwoLevelCacheManager> subscribers = new ArrayList<>();
        instanceA = manager(subscribers);
        instanceB = manager(subscribers);
        subscribers.add(instanceA);
        subscribers.add(instanceB);
    }

    private TwoLevelCacheManager manager(List<TwoLevelCacheManager> subscribers) {
        return new TwoLevelCacheManager(sharedStore, List.of(CacheNames.PUBLIC_RESTAURANTS, CacheNames.PUBLIC_CATALOG),
                Duration.ofMinutes(1), (channel, message) -> {
                    assertThat(channel).isEqualTo(TwoLevelCacheManager.CHANNEL);
                    published.add(message);
                    subscribers.forEach(s -> s.onInvalidation(message));
                });
    }

    private Cache catalog(TwoLevelCacheManager manager) {
        return manager.getCache(CacheNames.PUBLIC_CATALOG);
    }

    @Test
    void laLecturaSaleDelNivelLocalSinTocarElCompartido() {
        catalog(instanceA).put(RESTAURANT, "carta");
        sharedStore.getCache(CacheNames.PUBLIC_CATALOG).clear(); // si leyera Redis, no encontraría nada

        assertThat(catalog(instanceA).get(RESTAURANT).get()).isEqualTo("carta");
    }

    @Test
    void otraInstanciaLeeDelCompartidoUnaVezYLuegoDeSuNivelLocal() {
        catalog(instanceA).put(RESTAURANT, "carta");

        assertThat(catalog(instanceB).get(RESTAURANT, String.class)).isEqualTo("carta");
        sharedStore.getCache(CacheNames.PUBLIC_CATALOG).clear();
        assertThat(catalog(instanceB).get(RESTAURANT, String.class)).isEqualTo("carta");
    }

    @Test
    void unDescarteEnUnaInstanciaBorraAmbosNivelesYElLocalDeLaOtra() {
        catalog(instanceA).put(RESTAURANT, "carta vieja");
        catalog(instanceB).get(RESTAURANT); // B la tiene en su nivel 1

        catalog(instanceA).evict(RESTAURANT);

        assertThat(published).containsExactly(CacheNames.PUBLIC_CATALOG + "|" + RESTAURANT);
        assertThat(sharedStore.getCache(CacheNames.PUBLIC_CATALOG).get(RESTAURANT)).isNull();
        assertThat(catalog(instanceA).get(RESTAURANT)).isNull();
        assertThat(catalog(instanceB).get(RESTAURANT)).isNull();
    }

    @Test
    void vaciarSeAvisaConAsteriscoYVaciaElNivelLocalDeTodos() {
        catalog(instanceA).put(RESTAURANT, "carta");
        catalog(instanceB).get(RESTAURANT);

        catalog(instanceA).clear();

        assertThat(published).containsExactly(CacheNames.PUBLIC_CATALOG + "|*");
        assertThat(catalog(instanceB).get(RESTAURANT)).isNull();
    }

    @Test
    void getConCargadorGuardaEnAmbosNiveles() {
        assertThat(catalog(instanceA).get(RESTAURANT, () -> "cargada")).isEqualTo("cargada");
        assertThat(catalog(instanceA).get(RESTAURANT, () -> "otra")).isEqualTo("cargada");
        assertThat(sharedStore.getCache(CacheNames.PUBLIC_CATALOG).get(RESTAURANT).get()).isEqualTo("cargada");
        assertThatThrownBy(() -> catalog(instanceA).get(UUID.randomUUID(), () -> {
            throw new IllegalStateException("bd caída");
        })).isInstanceOf(Cache.ValueRetrievalException.class);
    }

    @Test
    void conRedisCaidoFuncionaSoloConElNivelLocalYNoPropagaErrores() {
        Cache broken = mock(Cache.class);
        when(broken.get(any())).thenThrow(new IllegalStateException("redis caído"));
        doThrow(new IllegalStateException("redis caído")).when(broken).put(any(), any());
        doThrow(new IllegalStateException("redis caído")).when(broken).evict(any());
        doThrow(new IllegalStateException("redis caído")).when(broken).clear();
        TwoLevelCache cache = new TwoLevelCache(CacheNames.PUBLIC_CATALOG,
                Caffeine.newBuilder().<String, Object>build(), broken, key -> {
                    throw new IllegalStateException("redis caído");
                });

        assertThat(cache.get(RESTAURANT)).isNull();
        cache.put(RESTAURANT, "carta");
        assertThat(cache.get(RESTAURANT).get()).isEqualTo("carta");
        cache.evict(RESTAURANT);
        assertThat(cache.get(RESTAURANT)).isNull();
        cache.clear();
        cache.put(RESTAURANT, null); // los nulos no se guardan
        assertThat(cache.get(RESTAURANT)).isNull();
    }

    @Test
    void unValorDeTipoInesperadoSeRechazaYLosAvisosMalFormadosSeIgnoran() {
        catalog(instanceA).put(RESTAURANT, "texto");
        assertThatThrownBy(() -> catalog(instanceA).get(RESTAURANT, Integer.class))
                .isInstanceOf(IllegalStateException.class);

        instanceA.onInvalidation("sin-separador");
        instanceA.onInvalidation("cache-inexistente|x");
        assertThat(catalog(instanceA).get(RESTAURANT)).isNotNull();
        assertThat(instanceA.getCacheNames()).containsExactly(CacheNames.PUBLIC_RESTAURANTS, CacheNames.PUBLIC_CATALOG);
        assertThat(instanceA.getCache("otra")).isNull();
        assertThat(catalog(instanceA).getNativeCache()).isNotNull();
    }

    @Test
    void unGestorCompartidoSinLaCacheEsUnErrorDeConfiguracion() {
        CacheManager empty = new ConcurrentMapCacheManager();
        ((ConcurrentMapCacheManager) empty).setCacheNames(List.of("otra"));
        assertThatThrownBy(() -> new TwoLevelCacheManager(empty, List.of(CacheNames.PUBLIC_CATALOG),
                Duration.ofMinutes(1), (c, m) -> { })).isInstanceOf(IllegalStateException.class);
        assertThat(new ConcurrentMapCache("x").getName()).isEqualTo("x");
    }
}
