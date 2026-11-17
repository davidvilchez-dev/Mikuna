package com.mikuna.backend.catalog.service;

import com.mikuna.backend.catalog.dto.CatalogResponse;
import com.mikuna.backend.catalog.dto.FiltersResponse;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.catalog.dto.PublicProductResponse;
import com.mikuna.backend.catalog.dto.PublicRestaurantResponse;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.restaurant.service.OrderEligibility;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta pública (RF-09, RF-10): solo restaurantes aprobados con cuenta habilitada y, de ellos, solo los
 * productos activos y disponibles. Filtra y ordena en memoria porque el orden depende del horario y la hora
 * actual; a escala local es suficiente y su optimización queda para la spec de carga (research §2).
 */
@Service
@Transactional(readOnly = true)
public class PublicCatalogService {

    private static final int MAX_QUERY = 100;

    private final RestaurantRepository restaurants;
    private final ProductRepository products;
    private final OrderEligibility eligibility;

    public PublicCatalogService(RestaurantRepository restaurants, ProductRepository products,
            OrderEligibility eligibility) {
        this.restaurants = restaurants;
        this.products = products;
        this.eligibility = eligibility;
    }

    /** Nombre por coincidencia parcial; categoría y zona por igualdad; todo sin distinguir mayúsculas ni tildes. */
    public PageResponse<PublicRestaurantResponse> search(String name, String category, String zone, int page, int size) {
        Paging.validate(page, size);
        String nameQuery = fold(limit(name));
        String categoryQuery = fold(category);
        String zoneQuery = fold(zone);

        List<PublicRestaurantResponse> matches = restaurants.findVisible().stream()
                .filter(r -> nameQuery.isEmpty() || fold(r.getName()).contains(nameQuery))
                .filter(r -> categoryQuery.isEmpty() || fold(r.getCategory()).equals(categoryQuery))
                .filter(r -> zoneQuery.isEmpty() || fold(r.getZone()).equals(zoneQuery))
                .map(r -> PublicRestaurantResponse.from(r, eligibility.attentionStatus(r)))
                .sorted(Comparator
                        .comparing((PublicRestaurantResponse r) -> r.attention() == AttentionStatus.OPEN ? 0 : 1)
                        .thenComparing(r -> fold(r.name()))
                        .thenComparing(PublicRestaurantResponse::name))
                .toList();
        return Paging.slice(matches, page, size);
    }

    /** Categorías y zonas distintas (sin distinguir mayúsculas ni tildes) de los restaurantes visibles. */
    public FiltersResponse filters() {
        List<Restaurant> visible = restaurants.findVisible();
        return new FiltersResponse(distinct(visible.stream().map(Restaurant::getCategory)),
                distinct(visible.stream().map(Restaurant::getZone)));
    }

    /** Catálogo de un restaurante visible; uno no visible o inexistente da el mismo 404. */
    public CatalogResponse catalog(UUID restaurantId, int page, int size) {
        Paging.validate(page, size);
        Restaurant restaurant = restaurants.findVisibleById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurante no encontrado"));
        PageResponse<PublicProductResponse> page0 = PageResponse.from(
                products.findByRestaurantIdAndActiveTrueAndAvailableTrue(restaurant.getId(),
                        PageRequest.of(page, size, Sort.by("category", "name"))),
                PublicProductResponse::from);
        return new CatalogResponse(PublicRestaurantResponse.from(restaurant, eligibility.attentionStatus(restaurant)),
                page0);
    }

    private static List<String> distinct(Stream<String> values) {
        // Variantes de mayúsculas o tildes se agrupan; se conserva siempre la misma (la menor) para ser estable.
        return values.collect(java.util.stream.Collectors.toMap(PublicCatalogService::fold, v -> v,
                        (a, b) -> a.compareTo(b) <= 0 ? a : b))
                .values().stream()
                .sorted(Comparator.comparing(PublicCatalogService::fold))
                .toList();
    }

    private static String limit(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() > MAX_QUERY ? trimmed.substring(0, MAX_QUERY) : trimmed;
    }

    private static String fold(String text) {
        return text == null || text.isBlank() ? "" : Product.key(text);
    }
}
