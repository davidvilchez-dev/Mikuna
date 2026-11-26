package com.mikuna.backend.admin.controller;

import com.mikuna.backend.admin.dto.AdminOrderDetail;
import com.mikuna.backend.admin.dto.AdminOrderSummary;
import com.mikuna.backend.admin.dto.CancelOrderRequest;
import com.mikuna.backend.admin.service.AdminOrderCancellationService;
import com.mikuna.backend.admin.service.AdminOrderService;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.security.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pedidos de la plataforma (protegido, rol ADMIN): consulta y, desde la spec 014, cancelación de un pedido aceptado y
 * no entregado con motivo obligatorio y reembolso total si se pagó con tarjeta.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;
    private final AdminOrderCancellationService cancellation;

    public AdminOrderController(AdminOrderService service, AdminOrderCancellationService cancellation) {
        this.service = service;
        this.cancellation = cancellation;
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

    /** El responsable sale del token; el motivo se valida aquí y queda en el cambio de estado. */
    @PostMapping("/{id}/cancel")
    public AdminOrderDetail cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request) {
        return cancellation.cancel(principal.id(), id, request.reason());
    }
}
