package com.mikuna.backend.admin.controller;

import com.mikuna.backend.admin.dto.AdminDeliveryDetail;
import com.mikuna.backend.admin.dto.AdminDeliverySummary;
import com.mikuna.backend.admin.service.AdminOrderService;
import com.mikuna.backend.catalog.dto.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de todas las entregas de la plataforma (protegido, rol ADMIN). Solo lectura; la asignación manual y la
 * liberación siguen en {@code AdminDeliveryController} (spec 008).
 */
@RestController
@RequestMapping("/api/admin/deliveries")
public class AdminDeliveryQueryController {

    private final AdminOrderService service;

    public AdminDeliveryQueryController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminDeliverySummary> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.listDeliveries(status, page, size);
    }

    @GetMapping("/{id}")
    public AdminDeliveryDetail detail(@PathVariable UUID id) {
        return service.deliveryDetail(id);
    }
}
