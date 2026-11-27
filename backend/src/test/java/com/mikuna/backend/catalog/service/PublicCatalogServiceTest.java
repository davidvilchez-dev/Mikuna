package com.mikuna.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mikuna.backend.catalog.dto.CatalogResponse;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.catalog.dto.PublicRestaurantResponse;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.restaurant.service.OrderEligibility;
import com.mikuna.backend.support.TestRestaurants;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

/** RF-09 y RF-10: búsqueda, filtros y catálogo público con reloj fijo (lunes 15:00 en Lima). */
@ExtendWith(MockitoExtension.class)
class PublicCatalogServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    @Mock RestaurantRepository restaurants;
    @Mock ProductRepository products;

    private PublicCatalogService service;

    @BeforeEach
    void setUp() {
        OrderEligibility eligibility = new OrderEligibility(
                Clock.fixed(Instant.parse("2026-11-16T20:00:00Z"), ZoneOffset.UTC), "America/Lima");
        // Sin proxy de Spring no hay caché: cada llamada lee los repositorios simulados.
        service = new PublicCatalogService(new PublicCatalogCache(restaurants, products, new NoOpCacheManager()),
                eligibility);
    }

    /** Abierto el lunes de 09:00 a 21:00, por tanto OPEN en el reloj de la prueba si está disponible. */
    private static Restaurant restaurant(String name, String category, String zone, boolean available) {
        Restaurant restaurant = TestRestaurants.approvedOpenOnMonday(UUID.randomUUID(), available);
        ReflectionTestUtils.setField(restaurant, "name", name);
        ReflectionTestUtils.setField(restaurant, "category", category);
        ReflectionTestUtils.setField(restaurant, "zone", zone);
        return restaurant;
    }

    private void visible(Restaurant... all) {
        when(restaurants.findVisible()).thenReturn(List.of(all));
    }

    private static List<String> names(PageResponse<PublicRestaurantResponse> page) {
        return page.items().stream().map(PublicRestaurantResponse::name).toList();
    }

    private static Restaurant branch(UUID owner, String name, Double latitude, Double longitude, boolean available) {
        Restaurant restaurant = TestRestaurants.approvedOpenOnMonday(owner, available);
        ReflectionTestUtils.setField(restaurant, "name", name);
        restaurant.changeLocation(latitude, longitude);
        return restaurant;
    }

    @Test
    void lasSucursalesDeUnaMarcaSeMuestranComoUnaSolaTarjetaLaMasCercana() {
        UUID brand = UUID.randomUUID();
        visible(branch(brand, "Kimsa Lejos", -13.1700, -74.2100, true), branch(brand, "Kimsa Cerca", -13.1580, -74.2230, true),
                branch(UUID.randomUUID(), "Otra marca", -13.1000, -74.2000, true));

        PageResponse<PublicRestaurantResponse> page = service.search(null, null, null, -13.1579, -74.2232, 0, 20);

        assertThat(names(page)).containsExactly("Kimsa Cerca", "Otra marca");
        PublicRestaurantResponse kimsa = page.items().get(0);
        assertThat(kimsa.branchCount()).isEqualTo(2);
        assertThat(kimsa.distanceKm()).isNotNull().isLessThan(0.5);
    }

    @Test
    void laSucursalCerradaCedeAnteUnaAbiertaMasLejana() {
        UUID brand = UUID.randomUUID();
        visible(branch(brand, "Kimsa Cerca Pausada", -13.1580, -74.2230, false), branch(brand, "Kimsa Lejos Abierta", -13.1700, -74.2100, true));

        PageResponse<PublicRestaurantResponse> page = service.search(null, null, null, -13.1579, -74.2232, 0, 20);

        assertThat(names(page)).containsExactly("Kimsa Lejos Abierta");
    }

    @Test
    void sinPuntoDeEntregaSeUsaLaSucursalPrincipalYNoHayDistancia() {
        UUID brand = UUID.randomUUID();
        Restaurant principal = branch(brand, "Kimsa Principal", -13.1700, -74.2100, true);
        Restaurant reciente = branch(brand, "Kimsa Reciente", -13.1580, -74.2230, true);
        ReflectionTestUtils.setField(principal, "submittedAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(reciente, "submittedAt", Instant.parse("2026-06-01T00:00:00Z"));
        visible(reciente, principal);

        PublicRestaurantResponse card = service.search(null, null, null, 0, 20).items().get(0);

        assertThat(card.name()).isEqualTo("Kimsa Principal");
        assertThat(card.distanceKm()).isNull();
    }

    @Test
    void sinCriteriosDevuelveTodosLosVisiblesConSuAtencion() {
        Restaurant outside = TestRestaurants.restaurant(UUID.randomUUID(), RestaurantStatus.APPROVED); // sin horario
        outside.setAvailable(true);
        ReflectionTestUtils.setField(outside, "name", "Cerrado ahora");
        visible(restaurant("Abierto", "Criolla", "Ayacucho", true), restaurant("Pausado", "Criolla", "Ayacucho", false), outside);

        PageResponse<PublicRestaurantResponse> page = service.search(null, null, null, 0, 20);

        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.items()).extracting(PublicRestaurantResponse::attention)
                .containsExactly(AttentionStatus.OPEN, AttentionStatus.OUTSIDE_HOURS, AttentionStatus.PAUSED);
        assertThat(names(page)).containsExactly("Abierto", "Cerrado ahora", "Pausado");
    }

    @Test
    void elNombreCoincideParcialmenteSinDistinguirMayusculasNiTildes() {
        visible(restaurant("POLLERÍA Don Pepe", "Pollos", "Centro", true), restaurant("La Picantería", "Criolla", "Centro", true));

        assertThat(names(service.search("pollería", null, null, 0, 20))).containsExactly("POLLERÍA Don Pepe");
        assertThat(names(service.search("  pollERIA ", null, null, 0, 20))).containsExactly("POLLERÍA Don Pepe");
        assertThat(names(service.search("picanteria", null, null, 0, 20))).containsExactly("La Picantería");
        assertThat(service.search("zzz", null, null, 0, 20).items()).isEmpty();
    }

    @Test
    void categoriaYZonaCoincidenPorIgualdadYSeCombinan() {
        visible(restaurant("A", "Criolla", "Huamanga", true), restaurant("B", "Criolla", "Jesús Nazareno", true),
                restaurant("C", "Pizza", "Huamanga", true));

        assertThat(names(service.search(null, "criolla", null, 0, 20))).containsExactly("A", "B");
        assertThat(names(service.search(null, null, "HUAMANGA", 0, 20))).containsExactly("A", "C");
        assertThat(names(service.search(null, null, "jesus nazareno", 0, 20))).containsExactly("B");
        assertThat(names(service.search(null, "criolla", "huamanga", 0, 20))).containsExactly("A");
        assertThat(service.search(null, "criol", null, 0, 20).items()).isEmpty();
    }

    @Test
    void losQueAtiendenAhoraVanPrimeroYLuegoPorNombre() {
        visible(restaurant("Zeta", "X", "Z", true), restaurant("Beta", "X", "Z", false), restaurant("Álamo", "X", "Z", false),
                restaurant("Alfa", "X", "Z", true));

        assertThat(names(service.search(null, null, null, 0, 20))).containsExactly("Alfa", "Zeta", "Álamo", "Beta");
    }

    @Test
    void paginaLosResultadosYUnaPaginaFueraDeRangoQuedaVacia() {
        visible(restaurant("A", "X", "Z", true), restaurant("B", "X", "Z", true), restaurant("C", "X", "Z", true));

        PageResponse<PublicRestaurantResponse> first = service.search(null, null, null, 0, 2);
        assertThat(names(first)).containsExactly("A", "B");
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(names(service.search(null, null, null, 1, 2))).containsExactly("C");
        PageResponse<PublicRestaurantResponse> beyond = service.search(null, null, null, 5, 2);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalItems()).isEqualTo(3);
    }

    @Test
    void sinResultadosDevuelveUnaPaginaVaciaConCeroPaginas() {
        visible();
        PageResponse<PublicRestaurantResponse> page = service.search(null, null, null, 0, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void validaLosParametrosDePagina() {
        assertThatThrownBy(() -> service.search(null, null, null, -1, 20)).isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.search(null, null, null, 0, 0)).isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.search(null, null, null, 0, 51)).isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void loscaracteresEspecialesYTextosLargosSeTratanComoTextoLiteral() {
        visible(restaurant("Dulce 100% Rico", "Postres", "Centro", true), restaurant("O'Brien_Pub", "Bar", "Centro", true));

        assertThat(names(service.search("100%", null, null, 0, 20))).containsExactly("Dulce 100% Rico");
        assertThat(names(service.search("%", null, null, 0, 20))).containsExactly("Dulce 100% Rico");
        assertThat(names(service.search("'", null, null, 0, 20))).containsExactly("O'Brien_Pub");
        assertThat(names(service.search("_pub", null, null, 0, 20))).containsExactly("O'Brien_Pub");
        assertThat(service.search("a".repeat(500), null, null, 0, 20).items()).isEmpty();
        assertThat(names(service.search("; drop table restaurantes;--", null, null, 0, 20))).isEmpty();
    }

    @Test
    void lasOpcionesDeFiltroSonDistintasYOrdenadasSinDistinguirTildes() {
        visible(restaurant("A", "Criolla", "Jesús Nazareno", true), restaurant("B", "criolla", "Huamanga", true),
                restaurant("C", "Pizza", "Huamanga", true), restaurant("D", "Árabe", "jesus nazareno", true));

        assertThat(service.filters().categories()).containsExactly("Árabe", "Criolla", "Pizza");
        assertThat(service.filters().zones()).containsExactly("Huamanga", "Jesús Nazareno");
    }

    @Test
    void elCatalogoDeUnRestauranteVisibleIncluyeSuAtencionYSoloProductosDisponibles() {
        Restaurant restaurant = restaurant("La Picantería", "Criolla", "Ayacucho", false); // pausado
        visible(restaurant);
        Product product = Product.create(restaurant.getId(), "Lomo", "Casero", "Fondos", new BigDecimal("20"),
                "https://img.test/p.jpg", "mikuna/products/p", Instant.parse("2026-11-17T00:00:00Z"));
        when(products.findByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc(restaurant.getId()))
                .thenReturn(List.of(product));

        CatalogResponse catalog = service.catalog(restaurant.getId(), 0, 24);

        assertThat(catalog.restaurant().attention()).isEqualTo(AttentionStatus.PAUSED);
        assertThat(catalog.restaurant().name()).isEqualTo("La Picantería");
        assertThat(catalog.products().items()).hasSize(1);
        assertThat(catalog.products().items().get(0).imageUrl()).isEqualTo("https://img.test/p.jpg");
        assertThat(catalog.products().size()).isEqualTo(24);
        assertThat(catalog.products().totalItems()).isEqualTo(1);
    }

    @Test
    void elCatalogoSePaginaEnMemoriaSobreLaCartaOrdenada() {
        Restaurant restaurant = restaurant("La Picantería", "Criolla", "Ayacucho", true);
        visible(restaurant);
        List<Product> carta = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> Product.create(restaurant.getId(), "Plato " + i, "d", "Fondos", BigDecimal.TEN,
                        "https://img.test/" + i + ".jpg", "p" + i, Instant.parse("2026-11-17T00:00:00Z")))
                .toList();
        when(products.findByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc(restaurant.getId()))
                .thenReturn(carta);

        PageResponse<?> segunda = service.catalog(restaurant.getId(), 1, 2).products();

        assertThat(segunda.items()).hasSize(2);
        assertThat(segunda.totalItems()).isEqualTo(5);
        assertThat(segunda.totalPages()).isEqualTo(3);
    }

    @Test
    void sinPortadaPropiaUsaLaImagenDelPrimerPlatoDisponible() {
        Restaurant restaurant = restaurant("Sin portada", "Criolla", "Ayacucho", true);
        visible(restaurant);
        ProductRepository.FirstImage first = new ProductRepository.FirstImage() {
            public UUID getRestaurantId() { return restaurant.getId(); }
            public String getImageUrl() { return "https://img.test/primero.jpg"; }
        };
        when(products.firstAvailableImageByRestaurant(List.of(restaurant.getId()))).thenReturn(List.of(first));

        assertThat(service.search(null, null, null, 0, 20).items().get(0).coverUrl())
                .isEqualTo("https://img.test/primero.jpg");
    }

    @Test
    void unRestauranteNoVisibleOInexistenteDaNoEncontradoYNoConsultaProductos() {
        UUID id = UUID.randomUUID();
        visible();

        assertThatThrownBy(() -> service.catalog(id, 0, 24)).isInstanceOf(RestaurantNotFoundException.class);
        assertThatThrownBy(() -> service.catalog(id, -1, 24)).isInstanceOf(RegistrationValidationException.class);
        org.mockito.Mockito.verifyNoInteractions(products);
    }
}
