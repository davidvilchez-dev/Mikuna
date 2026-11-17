package com.mikuna.backend.cart.controller;

import com.mikuna.backend.cart.dto.AddLineRequest;
import com.mikuna.backend.cart.dto.CartResponse;
import com.mikuna.backend.cart.dto.QuantityRequest;
import com.mikuna.backend.cart.dto.SelectAddressRequest;
import com.mikuna.backend.cart.service.CartService;
import com.mikuna.backend.common.security.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Carrito propio (protegido, rol CUSTOMER). El cliente sale del token; los importes los calcula el servidor. */
@RestController
@RequestMapping("/api/customer/cart")
public class CartController {

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.get(principal.id());
    }

    @PostMapping("/lines")
    public CartResponse addLine(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AddLineRequest request) {
        return service.addLine(principal.id(), request);
    }

    @PutMapping("/lines/{lineId}")
    public CartResponse changeQuantity(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID lineId, @Valid @RequestBody QuantityRequest request) {
        return service.changeQuantity(principal.id(), lineId, request.quantity());
    }

    @DeleteMapping("/lines/{lineId}")
    public CartResponse removeLine(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID lineId) {
        return service.removeLine(principal.id(), lineId);
    }

    @DeleteMapping
    public CartResponse clear(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.clear(principal.id());
    }

    @PutMapping("/address")
    public CartResponse selectAddress(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody SelectAddressRequest request) {
        return service.selectAddress(principal.id(), request.addressId());
    }
}
