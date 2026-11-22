package com.mikuna.backend.catalog.controller;

import com.mikuna.backend.catalog.dto.CatalogResponse;
import com.mikuna.backend.catalog.dto.FiltersResponse;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.catalog.dto.PublicRestaurantResponse;
import com.mikuna.backend.catalog.service.PublicCatalogService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta pública de restaurantes y catálogos (RF-09, RF-10). Solo lectura y sin sesión: no recibe el
 * titular autenticado ni expone datos de cuenta.
 */
@RestController
@RequestMapping("/api/public/restaurants")
public class PublicRestaurantController {

    private final PublicCatalogService service;

    public PublicRestaurantController(PublicCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PublicRestaurantResponse> search(@RequestParam(required = false) String name,
            @RequestParam(required = false) String category, @RequestParam(required = false) String zone,
            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.search(name, category, zone, lat, lng, page, size);
    }

    @GetMapping("/filters")
    public FiltersResponse filters() {
        return service.filters();
    }

    @GetMapping("/{id}/catalog")
    public CatalogResponse catalog(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return service.catalog(id, page, size);
    }
}
