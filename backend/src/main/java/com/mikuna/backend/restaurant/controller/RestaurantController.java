package com.mikuna.backend.restaurant.controller;

import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.restaurant.dto.AvailabilityRequest;
import com.mikuna.backend.restaurant.dto.RestaurantRequest;
import com.mikuna.backend.restaurant.dto.RestaurantResponse;
import com.mikuna.backend.restaurant.dto.ScheduleRequest;
import com.mikuna.backend.restaurant.service.RestaurantImageService;
import com.mikuna.backend.restaurant.service.RestaurantService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restaurante propio (protegido, rol RESTAURANT). No recibe ids: el titular sale del token. */
@RestController
@RequestMapping("/api/restaurant/me")
public class RestaurantController {

    private final RestaurantService service;
    private final RestaurantImageService imageService;

    public RestaurantController(RestaurantService service, RestaurantImageService imageService) {
        this.service = service;
        this.imageService = imageService;
    }

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody RestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.id(), request));
    }

    @GetMapping
    public RestaurantResponse get(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.getMine(principal.id());
    }

    @PutMapping
    public RestaurantResponse update(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody RestaurantRequest request) {
        return service.updateMine(principal.id(), request);
    }

    @PostMapping("/resubmit")
    public RestaurantResponse resubmit(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.resubmit(principal.id());
    }

    @PutMapping("/schedule")
    public RestaurantResponse schedule(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ScheduleRequest request) {
        return service.updateSchedule(principal.id(), request);
    }

    @PutMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestaurantResponse images(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "cover", required = false) MultipartFile cover) {
        return imageService.update(principal.id(), logo, cover);
    }

    @PutMapping("/availability")
    public RestaurantResponse availability(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AvailabilityRequest request) {
        return service.setAvailability(principal.id(), request);
    }
}
