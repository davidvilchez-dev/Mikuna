package com.mikuna.backend.catalog.controller;

import com.mikuna.backend.catalog.dto.AvailabilityRequest;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.catalog.dto.ProductRequest;
import com.mikuna.backend.catalog.dto.ProductResponse;
import com.mikuna.backend.catalog.service.ProductService;
import com.mikuna.backend.common.security.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Catálogo propio (protegido, rol RESTAURANT). No recibe ids de restaurante: el titular sale del token. */
@RestController
@RequestMapping("/api/restaurant/me/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestPart("product") ProductRequest product, @RequestPart("image") MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.id(), product, image));
    }

    /** Copia la carta de otra sucursal de la misma cuenta a la sucursal con la que se trabaja. */
    @PostMapping("/copy-from/{sourceRestaurantId}")
    public ProductService.CopyMenuResult copyFrom(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID sourceRestaurantId) {
        return service.copyMenu(principal.id(), sourceRestaurantId);
    }

    @GetMapping
    public PageResponse<ProductResponse> list(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(principal.id(), page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return service.get(principal.id(), id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
            @Valid @RequestPart("product") ProductRequest product,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return service.update(principal.id(), id, product, image);
    }

    @PutMapping("/{id}/availability")
    public ProductResponse availability(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody AvailabilityRequest request) {
        return service.setAvailability(principal.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
            @RequestParam Long version) {
        service.withdraw(principal.id(), id, version);
        return ResponseEntity.noContent().build();
    }
}
