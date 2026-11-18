package com.mikuna.backend.delivery.controller;

import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.delivery.dto.AvailabilityRequest;
import com.mikuna.backend.delivery.dto.AvailabilityResponse;
import com.mikuna.backend.delivery.dto.CourierDeliveryResponse;
import com.mikuna.backend.delivery.dto.LocationRequest;
import com.mikuna.backend.delivery.dto.RejectDeliveryRequest;
import com.mikuna.backend.delivery.service.CourierAvailabilityService;
import com.mikuna.backend.delivery.service.CourierDeliveryService;
import com.mikuna.backend.delivery.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Disponibilidad y entregas del repartidor propio (protegido, rol COURIER). El repartidor sale del token. */
@RestController
@RequestMapping("/api/courier")
public class CourierController {

    private final CourierAvailabilityService availability;
    private final CourierDeliveryService deliveries;
    private final LocationService location;

    public CourierController(CourierAvailabilityService availability, CourierDeliveryService deliveries,
            LocationService location) {
        this.availability = availability;
        this.deliveries = deliveries;
        this.location = location;
    }

    /** Ubicación vigente del repartidor con una entrega en curso (RF-26). */
    @PutMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLocation(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LocationRequest request) {
        location.update(principal.id(), request);
    }

    @GetMapping("/availability")
    public AvailabilityResponse availability(@AuthenticationPrincipal AccountPrincipal principal) {
        return availability.get(principal.id());
    }

    @PutMapping("/availability")
    public AvailabilityResponse setAvailability(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AvailabilityRequest request) {
        return availability.set(principal.id(), request.available());
    }

    @GetMapping("/deliveries")
    public List<CourierDeliveryResponse> deliveries(@AuthenticationPrincipal AccountPrincipal principal) {
        return deliveries.listActive(principal.id());
    }

    @PostMapping("/deliveries/{orderId}/pickup")
    public CourierDeliveryResponse pickup(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID orderId) {
        return deliveries.pickup(principal.id(), orderId);
    }

    @PostMapping("/deliveries/{orderId}/complete")
    public CourierDeliveryResponse complete(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID orderId) {
        return deliveries.complete(principal.id(), orderId);
    }

    @PostMapping("/deliveries/{orderId}/reject")
    public CourierDeliveryResponse reject(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID orderId, @Valid @RequestBody(required = false) RejectDeliveryRequest request) {
        return deliveries.reject(principal.id(), orderId, request == null ? null : request.reason());
    }
}
