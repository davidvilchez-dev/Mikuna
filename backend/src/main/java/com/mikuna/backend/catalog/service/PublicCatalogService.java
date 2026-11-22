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

    public PageResponse<PublicRestaurantResponse> search(String name, String category, String zone, int page, int size) {
        return search(name, category, zone, null, null, page, size);
    }

    /**
     * Nombre por coincidencia parcial; categoría y zona por igualdad; todo sin distinguir mayúsculas ni tildes.
     * Las sucursales de una misma cuenta se muestran como una sola tarjeta: la abierta y más cercana al punto de
     * entrega (latitude/longitude); sin punto, la sucursal principal (la más antigua).
     */
    public PageResponse<PublicRestaurantResponse> search(String name, String category, String zone, Double latitude,
            Double longitude, int page, int size) {
        Paging.validate(page, size);
        boolean located = latitude != null && longitude != null
                && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
        String nameQuery = fold(limit(name));
        String categoryQuery = fold(category);
        String zoneQuery = fold(zone);

        List<Restaurant> candidates = restaurants.findVisible().stream()
                .filter(r -> nameQuery.isEmpty() || fold(r.getName()).contains(nameQuery))
                .filter(r -> categoryQuery.isEmpty() || fold(r.getCategory()).equals(categoryQuery))
                .filter(r -> zoneQuery.isEmpty() || fold(r.getZone()).equals(zoneQuery))
                .toList();

        // Una tarjeta por marca (cuenta): se elige la mejor sucursal de cada una.
        java.util.Map<UUID, List<Restaurant>> brands = candidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(Restaurant::getOwnerId));
        List<PublicRestaurantResponse> matches = brands.values().stream()
                .map(branches -> {
                    Restaurant best = branches.stream()
                            .min(Comparator
                                    .comparing((Restaurant r) -> eligibility.attentionStatus(r) == AttentionStatus.OPEN ? 0 : 1)
                                    .thenComparing(r -> located ? distanceKm(r, latitude, longitude) : 0.0)
                                    .thenComparing(Restaurant::getSubmittedAt))
                            .orElseThrow();
                    Double distance = located ? roundedDistance(best, latitude, longitude) : null;
                    return PublicRestaurantResponse.from(best, eligibility.attentionStatus(best), fallbackCover(best),
                            distance, branches.size());
                })
                .sorted(Comparator
                        .comparing((PublicRestaurantResponse r) -> r.attention() == AttentionStatus.OPEN ? 0 : 1)
                        .thenComparing(r -> fold(r.name()))
                        .thenComparing(PublicRestaurantResponse::name))
                .toList();
        return Paging.slice(matches, page, size);
    }

    /** Sucursales sin ubicación quedan al final: se comparan como si estuvieran lejísimos. */
    private static double distanceKm(Restaurant restaurant, double latitude, double longitude) {
        if (restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
            return Double.MAX_VALUE;
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(restaurant.getLatitude() - latitude);
        double dLon = Math.toRadians(restaurant.getLongitude() - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(latitude))
                * Math.cos(Math.toRadians(restaurant.getLatitude())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static Double roundedDistance(Restaurant restaurant, double latitude, double longitude) {
        double km = distanceKm(restaurant, latitude, longitude);
        return km == Double.MAX_VALUE ? null : Math.round(km * 10) / 10.0;
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
        return new CatalogResponse(PublicRestaurantResponse.from(restaurant, eligibility.attentionStatus(restaurant),
                fallbackCover(restaurant)),
                page0);
    }

    /** Sin portada propia, la tarjeta usa la foto del primer plato disponible. */
    private String fallbackCover(Restaurant restaurant) {
        if (restaurant.getCoverUrl() != null) {
            return null;
        }
        return products.findFirstByRestaurantIdAndActiveTrueAndAvailableTrueOrderByCategoryAscNameAsc(restaurant.getId())
                .map(Product::getImageUrl).orElse(null);
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
