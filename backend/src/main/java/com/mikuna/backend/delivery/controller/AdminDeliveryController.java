package com.mikuna.backend.delivery.controller;

import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.delivery.dto.AssignRequest;
import com.mikuna.backend.delivery.dto.PendingDeliveryResponse;
import com.mikuna.backend.delivery.service.DeliveryAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Asignación manual y liberación de entregas (protegido, rol ADMIN). El administrador sale del token. */
@RestController
@RequestMapping("/api/admin/deliveries")
public class AdminDeliveryController {

    private final DeliveryAdminService service;

    public AdminDeliveryController(DeliveryAdminService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<PendingDeliveryResponse> pending() {
        return service.listPending();
    }

    @PostMapping("/{orderId}/assign")
    public PendingDeliveryResponse assign(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID orderId, @Valid @RequestBody AssignRequest request) {
        return service.assign(principal.id(), orderId, request.courierId());
    }

    @PostMapping("/{orderId}/release")
    public PendingDeliveryResponse release(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID orderId) {
        return service.release(principal.id(), orderId);
    }
}
