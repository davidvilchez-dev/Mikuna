package com.mikuna.backend.order.controller;

import com.mikuna.backend.common.exception.OrderConflictException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.order.dto.DecisionRequest;
import com.mikuna.backend.order.dto.RestaurantOrderResponse;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.service.OrderStatusService;
import jakarta.validation.Valid;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pedidos del restaurante propio (protegido, rol RESTAURANT). El restaurante sale del token. */
@RestController
@RequestMapping("/api/restaurant/orders")
public class RestaurantOrderController {

    private static final Set<OrderStatus> LISTABLE =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY);

    private final OrderStatusService service;

    public RestaurantOrderController(OrderStatusService service) {
        this.service = service;
    }

    @GetMapping
    public List<RestaurantOrderResponse> list(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(name = "status", defaultValue = "PENDING") String status) {
        return service.list(principal.id(), parse(status)).stream().map(RestaurantOrderController::response)
                .toList();
    }

    @GetMapping("/{id}")
    public RestaurantOrderResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return response(service.get(principal.id(), id));
    }

    /** Acepta y cobra. Si la reserva venció, el pedido ya quedó Cancelado y se responde 409 (FR-011a). */
    @PostMapping("/{id}/accept")
    public RestaurantOrderResponse accept(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID id) {
        OrderStatusService.Result result = service.accept(principal.id(), id);
        if (result.reservationExpired()) {
            throw new OrderConflictException(OrderConflictException.RESERVATION_EXPIRED,
                    "La reserva del pago venció: el pedido se canceló sin cobro");
        }
        return response(result);
    }

    @PostMapping("/{id}/reject")
    public RestaurantOrderResponse reject(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return response(service.reject(principal.id(), id, reason(request)));
    }

    @PostMapping("/{id}/start-preparation")
    public RestaurantOrderResponse startPreparation(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID id) {
        return response(service.startPreparation(principal.id(), id));
    }

    @PostMapping("/{id}/mark-ready")
    public RestaurantOrderResponse markReady(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID id) {
        return response(service.markReady(principal.id(), id));
    }

    private static OrderStatus parse(String value) {
        try {
            OrderStatus status = OrderStatus.valueOf(value);
            if (LISTABLE.contains(status)) {
                return status;
            }
        } catch (IllegalArgumentException e) {
            // se informa abajo como un único error de validación
        }
        throw new RegistrationValidationException("status",
                "El estado debe ser PENDING, ACCEPTED, PREPARING o READY");
    }

    private static String reason(DecisionRequest request) {
        return request == null ? null : request.reason();
    }

    private static RestaurantOrderResponse response(OrderStatusService.Result result) {
        return RestaurantOrderResponse.of(result.order(), result.payment());
    }
}
