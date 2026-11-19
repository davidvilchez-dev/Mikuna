package com.mikuna.backend.restaurant.service;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.exception.RestaurantConflictException;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.restaurant.dto.AdminRestaurantResponse;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-06: revisión administrativa. Cada resolución es una actualización condicional sobre el estado
 * de origen (PENDING), de modo que ante resoluciones simultáneas solo una prospera.
 */
@Service
public class RestaurantReviewService {

    static final int REASON_MAX = 300;

    private final RestaurantRepository restaurants;
    private final AccountRepository accounts;
    private final OrderEligibility eligibility;
    private final Clock clock;

    public RestaurantReviewService(RestaurantRepository restaurants, AccountRepository accounts,
            OrderEligibility eligibility, Clock clock) {
        this.restaurants = restaurants;
        this.accounts = accounts;
        this.eligibility = eligibility;
        this.clock = clock;
    }

    /** Lista por estado (por defecto PENDING), de la más antigua a la más reciente. */
    @Transactional(readOnly = true)
    public List<AdminRestaurantResponse> list(String status) {
        RestaurantStatus wanted = parse(status);
        return restaurants.findByStatusOrderBySubmittedAtAsc(wanted.name()).stream().map(this::respond).toList();
    }

    @Transactional
    public AdminRestaurantResponse approve(UUID adminId, UUID restaurantId) {
        return resolve(adminId, restaurantId, RestaurantStatus.APPROVED, null);
    }

    @Transactional
    public AdminRestaurantResponse reject(UUID adminId, UUID restaurantId, String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty() || clean.length() > REASON_MAX) {
            throw new RegistrationValidationException("reason", "El motivo es obligatorio y admite hasta 300 caracteres");
        }
        return resolve(adminId, restaurantId, RestaurantStatus.REJECTED, clean);
    }

    private AdminRestaurantResponse resolve(UUID adminId, UUID restaurantId, RestaurantStatus to, String reason) {
        int updated = restaurants.resolve(restaurantId, RestaurantStatus.PENDING.name(), to.name(), reason,
                adminId, Instant.now(clock));
        if (updated == 0) {
            if (!restaurants.existsById(restaurantId)) {
                throw new RestaurantNotFoundException("Solicitud no encontrada");
            }
            throw new RestaurantConflictException("La solicitud ya fue resuelta o modificada");
        }
        Restaurant restaurant = restaurants.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Solicitud no encontrada"));
        return respond(restaurant);
    }

    private static RestaurantStatus parse(String status) {
        if (status == null || status.isBlank()) {
            return RestaurantStatus.PENDING;
        }
        try {
            return RestaurantStatus.valueOf(status.trim());
        } catch (IllegalArgumentException invalid) {
            throw new RegistrationValidationException("status", "Estado no válido");
        }
    }

    private AdminRestaurantResponse respond(Restaurant restaurant) {
        Optional<Account> owner = accounts.findById(restaurant.getOwnerId());
        boolean ownerEnabled = owner.map(Account::canOperate).orElse(false);
        return AdminRestaurantResponse.from(restaurant, eligibility.canReceiveOrders(restaurant, ownerEnabled),
                owner.map(Account::getName).orElse(""), owner.map(Account::getEmail).orElse(""));
    }
}
