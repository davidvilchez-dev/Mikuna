package com.mikuna.backend.admin.controller;

import com.mikuna.backend.admin.dto.AdminOrderDetail;
import com.mikuna.backend.admin.dto.AdminOrderSummary;
import com.mikuna.backend.admin.service.AdminOrderService;
import com.mikuna.backend.catalog.dto.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de todos los pedidos de la plataforma (protegido, rol ADMIN). Solo lectura. */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminOrderSummary> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.listOrders(status, page, size);
    }

    @GetMapping("/{id}")
    public AdminOrderDetail detail(@PathVariable UUID id) {
        return service.orderDetail(id);
    }
}
