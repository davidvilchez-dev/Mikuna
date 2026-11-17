package com.mikuna.backend.address.controller;

import com.mikuna.backend.address.dto.AddressRequest;
import com.mikuna.backend.address.dto.AddressResponse;
import com.mikuna.backend.address.service.AddressService;
import com.mikuna.backend.common.security.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direcciones propias (protegido, rol CUSTOMER). El cliente sale del token, nunca del cuerpo. */
@RestController
@RequestMapping("/api/customer/addresses")
public class AddressController {

    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.id(), request));
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.list(principal.id());
    }

    @PutMapping("/{id}")
    public AddressResponse update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody AddressRequest request) {
        return service.update(principal.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        service.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }
}
