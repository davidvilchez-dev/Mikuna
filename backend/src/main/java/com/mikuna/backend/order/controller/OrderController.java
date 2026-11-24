package com.mikuna.backend.order.controller;

import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.order.dto.ConfirmOrderRequest;
import com.mikuna.backend.order.dto.DecisionRequest;
import com.mikuna.backend.order.dto.OrderResponse;
import com.mikuna.backend.order.dto.TrackingResponse;
import com.mikuna.backend.order.service.CheckoutService;
import com.mikuna.backend.order.service.CheckoutTransaction;
import com.mikuna.backend.order.service.OrderStatusService;
import com.mikuna.backend.order.service.TrackingService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pedidos propios (protegido, rol CUSTOMER). El cliente sale del token; los importes los calcula el servidor. */
@RestController
@RequestMapping("/api/customer/orders")
public class OrderController {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final CheckoutService service;
    private final OrderStatusService statusService;
    private final TrackingService tracking;
    private final Clock clock;

    public OrderController(CheckoutService service, OrderStatusService statusService, TrackingService tracking,
            Clock clock) {
        this.clock = clock;
        this.service = service;
        this.statusService = statusService;
        this.tracking = tracking;
    }

    /** 201 si se registró ahora; 200 si la clave de idempotencia ya se había procesado. */
    @PostMapping
    public ResponseEntity<OrderResponse> confirm(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConfirmOrderRequest request) {
        if (!KEY.matcher(idempotencyKey).matches()) {
            throw new RegistrationValidationException("Idempotency-Key",
                    "La clave de confirmación debe tener de 1 a 64 letras, números, guiones o guiones bajos");
        }
        CheckoutTransaction.Confirmation result = service.confirm(principal.id(), idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    /** El cliente cancela un pedido propio solo mientras esté Pendiente; la reserva se libera sin cobro (RF-20). */
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionRequest request) {
        OrderStatusService.Result result = statusService.cancel(principal.id(), id,
                request == null ? null : request.reason());
        return OrderResponse.of(result.order(), result.payment(), Instant.now(clock));
    }

    /** Estado, etapas y, solo con el pedido Recogido, la ubicación del repartidor (RF-27, RF-28). */
    @GetMapping("/{id}/tracking")
    public TrackingResponse tracking(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return tracking.get(principal.id(), id);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return service.get(principal.id(), id);
    }
}
