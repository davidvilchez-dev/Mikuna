package com.mikuna.backend.order.controller;

import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.order.dto.CheckoutResponse;
import com.mikuna.backend.order.service.CheckoutService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resumen de confirmación del cliente (protegido, rol CUSTOMER). El cliente sale del token. */
@RestController
@RequestMapping("/api/customer/checkout")
public class CheckoutController {

    private final CheckoutService service;

    public CheckoutController(CheckoutService service) {
        this.service = service;
    }

    @GetMapping
    public CheckoutResponse get(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.checkout(principal.id());
    }
}
