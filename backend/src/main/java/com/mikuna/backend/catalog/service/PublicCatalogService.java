package com.mikuna.backend.catalog.service;

import com.mikuna.backend.catalog.dto.CatalogResponse;
import com.mikuna.backend.catalog.dto.FiltersResponse;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.catalog.dto.PublicRestaurantResponse;
import com.mikuna.backend.catalog.dto.RestaurantSnapshot;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.service.OrderEligibility;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Consulta pública (RF-09, RF-10): solo restaurantes aprobados con cuenta habilitada y, de ellos, solo los
 * productos activos y disponibles. Los datos salen de {@link PublicCatalogCache} (spec 015): con la caché llena,
 * una consulta no lee la base de datos (FR-014). Filtrar, agrupar, ordenar y paginar se hace en memoria, y el
 * estado de atención se calcula en cada petición con la hora actual porque de él depende el orden (FR-002).
 */
@Service
public class PublicCatalogService {

    private static final int MAX_QUERY = 100;

    private final PublicCatalogCache cache;
    private final OrderEligibility eligibility;

    public PublicCatalogService(PublicCatalogCache cache, OrderEligibility eligibility) {
        this.cache = cache;
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

        List<RestaurantSnapshot> candidates = cache.restaurants().items().stream()
                .filter(r -> nameQuery.isEmpty() || fold(r.name()).contains(nameQuery))
                .filter(r -> categoryQuery.isEmpty() || fold(r.category()).equals(categoryQuery))
                .filter(r -> zoneQuery.isEmpty() || fold(r.zone()).equals(zoneQuery))
                .toList();

        // Una tarjeta por marca (cuenta): se elige la mejor sucursal de cada una.
        Map<UUID, List<RestaurantSnapshot>> brands = candidates.stream()
                .collect(Collectors.groupingBy(RestaurantSnapshot::ownerId));
        List<PublicRestaurantResponse> matches = brands.values().stream()
                .map(branches -> {
                    RestaurantSnapshot best = branches.stream()
                            .min(Comparator
                                    .comparing((RestaurantSnapshot r) -> attention(r) == AttentionStatus.OPEN ? 0 : 1)
                                    .thenComparing(r -> located ? distanceKm(r, latitude, longitude) : 0.0)
                                    .thenComparing(RestaurantSnapshot::submittedAt))
                            .orElseThrow();
                    Double distance = located ? roundedDistance(best, latitude, longitude) : null;
                    return PublicRestaurantResponse.from(best, attention(best), distance, branches.size());
                })
                .sorted(Comparator
                        .comparing((PublicRestaurantResponse r) -> r.attention() == AttentionStatus.OPEN ? 0 : 1)
                        .thenComparing(r -> fold(r.name()))
                        .thenComparing(PublicRestaurantResponse::name))
                .toList();
        return Paging.slice(matches, page, size);
    }

    private AttentionStatus attention(RestaurantSnapshot restaurant) {
        return eligibility.attentionStatus(restaurant.available(), restaurant.schedule());
    }

    /** Sucursales sin ubicación quedan al final: se comparan como si estuvieran lejísimos. */
    private static double distanceKm(RestaurantSnapshot restaurant, double latitude, double longitude) {
        if (restaurant.latitude() == null || restaurant.longitude() == null) {
            return Double.MAX_VALUE;
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(restaurant.latitude() - latitude);
        double dLon = Math.toRadians(restaurant.longitude() - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(latitude))
                * Math.cos(Math.toRadians(restaurant.latitude())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static Double roundedDistance(RestaurantSnapshot restaurant, double latitude, double longitude) {
        double km = distanceKm(restaurant, latitude, longitude);
        return km == Double.MAX_VALUE ? null : Math.round(km * 10) / 10.0;
    }

    /** Categorías y zonas distintas (sin distinguir mayúsculas ni tildes) de los restaurantes visibles. */
    public FiltersResponse filters() {
        List<RestaurantSnapshot> visible = cache.restaurants().items();
        return new FiltersResponse(distinct(visible.stream().map(RestaurantSnapshot::category)),
                distinct(visible.stream().map(RestaurantSnapshot::zone)));
    }

    /** Catálogo de un restaurante visible; uno no visible o inexistente da el mismo 404. */
    public CatalogResponse catalog(UUID restaurantId, int page, int size) {
        Paging.validate(page, size);
        // La visibilidad se decide siempre con la instantánea: una carta guardada de un restaurante que dejó de ser
        // visible nunca se sirve.
        RestaurantSnapshot restaurant = cache.restaurants().items().stream()
                .filter(r -> r.id().equals(restaurantId))
                .findFirst()
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurante no encontrado"));
        return new CatalogResponse(PublicRestaurantResponse.from(restaurant, attention(restaurant), null, 1),
                Paging.slice(cache.products(restaurantId).items(), page, size));
    }

    private static List<String> distinct(Stream<String> values) {
        // Variantes de mayúsculas o tildes se agrupan; se conserva siempre la misma (la menor) para ser estable.
        return values.collect(Collectors.toMap(PublicCatalogService::fold, v -> v,
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
