package com.mikuna.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
        service = new PublicCatalogService(restaurants, products, eligibility);
    }

    /** Abierto el lunes de 09:00 a 21:00, por tanto OPEN en el reloj de la prueba si está disponible. */
    private static Restaurant restaurant(String name, String category, String zone, boolean available) {
        Restaurant restaurant = TestRestaurants.approvedOpenOnMonday(OWNER, available);
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

    @Test
    void sinCriteriosDevuelveTodosLosVisiblesConSuAtencion() {
        Restaurant outside = TestRestaurants.restaurant(OWNER, RestaurantStatus.APPROVED); // sin horario
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
        when(restaurants.findVisibleById(restaurant.getId())).thenReturn(Optional.of(restaurant));
        Product product = Product.create(restaurant.getId(), "Lomo", "Casero", "Fondos", new BigDecimal("20"),
                "https://img.test/p.jpg", "mikuna/products/p", Instant.parse("2026-11-17T00:00:00Z"));
        when(products.findByRestaurantIdAndActiveTrueAndAvailableTrue(any(), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        CatalogResponse catalog = service.catalog(restaurant.getId(), 0, 24);

        assertThat(catalog.restaurant().attention()).isEqualTo(AttentionStatus.PAUSED);
        assertThat(catalog.restaurant().name()).isEqualTo("La Picantería");
        assertThat(catalog.products().items()).hasSize(1);
        assertThat(catalog.products().items().get(0).imageUrl()).isEqualTo("https://img.test/p.jpg");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(products).findByRestaurantIdAndActiveTrueAndAvailableTrue(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(24);
        assertThat(pageable.getValue().getSort().stream().map(order -> order.getProperty()).toList())
                .containsExactly("category", "name");
    }

    @Test
    void unRestauranteNoVisibleOInexistenteDaNoEncontradoYNoConsultaProductos() {
        UUID id = UUID.randomUUID();
        when(restaurants.findVisibleById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.catalog(id, 0, 24)).isInstanceOf(RestaurantNotFoundException.class);
        assertThatThrownBy(() -> service.catalog(id, -1, 24)).isInstanceOf(RegistrationValidationException.class);
        org.mockito.Mockito.verifyNoInteractions(products);
    }
}
