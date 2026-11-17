package com.mikuna.backend.restaurant.controller;

import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.restaurant.dto.AdminRestaurantResponse;
import com.mikuna.backend.restaurant.dto.RejectRequest;
import com.mikuna.backend.restaurant.service.RestaurantReviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Revisión de solicitudes (protegido, rol ADMIN). */
@RestController
@RequestMapping("/api/admin/restaurants")
public class RestaurantAdminController {

    private final RestaurantReviewService service;

    public RestaurantAdminController(RestaurantReviewService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminRestaurantResponse> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping("/{id}/approve")
    public AdminRestaurantResponse approve(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID id) {
        return service.approve(principal.id(), id);
    }

    @PostMapping("/{id}/reject")
    public AdminRestaurantResponse reject(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return service.reject(principal.id(), id, request.reason());
    }
}
