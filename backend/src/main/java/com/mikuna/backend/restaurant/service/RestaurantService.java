package com.mikuna.backend.restaurant.service;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.exception.RestaurantConflictException;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.common.exception.RestaurantStateException;
import com.mikuna.backend.restaurant.dto.AvailabilityRequest;
import com.mikuna.backend.restaurant.dto.OpeningHourDto;
import com.mikuna.backend.restaurant.dto.RestaurantRequest;
import com.mikuna.backend.restaurant.dto.RestaurantResponse;
import com.mikuna.backend.restaurant.dto.ScheduleRequest;
import com.mikuna.backend.restaurant.model.OpeningHour;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitud y gestión del restaurante propio (RF-05, RF-07). El titular siempre se toma del token:
 * ninguna operación recibe un id de restaurante, por lo que no se puede acceder a uno ajeno (RF-04).
 */
@Service
public class RestaurantService {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ]{7,15}$");
    private static final Pattern HOUR = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    private final RestaurantRepository restaurants;
    private final AccountRepository accounts;
    private final OrderEligibility eligibility;
    private final Clock clock;

    public RestaurantService(RestaurantRepository restaurants, AccountRepository accounts,
            OrderEligibility eligibility, Clock clock) {
        this.restaurants = restaurants;
        this.accounts = accounts;
        this.eligibility = eligibility;
        this.clock = clock;
    }

    /** RF-05: una única solicitud por cuenta, siempre en estado pendiente. */
    @Transactional
    public RestaurantResponse create(UUID ownerId, RestaurantRequest request) {
        ValidatedData data = validate(request);
        if (restaurants.findByOwnerId(ownerId).isPresent()) {
            throw alreadyRequested();
        }
        try {
            Restaurant saved = restaurants.saveAndFlush(Restaurant.request(ownerId, data.name(), data.category(),
                    data.description(), data.address(), data.zone(), data.phone(), Instant.now(clock)));
            return respond(saved);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            throw alreadyRequested();
        }
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getMine(UUID ownerId) {
        return respond(find(ownerId));
    }

    /** Datos generales: editables en cualquier estado; no cambian el estado de la solicitud. */
    @Transactional
    public RestaurantResponse updateMine(UUID ownerId, RestaurantRequest request) {
        ValidatedData data = validate(request);
        Restaurant restaurant = find(ownerId);
        restaurant.updateGeneralData(data.name(), data.category(), data.description(), data.address(),
                data.zone(), data.phone());
        return respond(save(restaurant));
    }

    /** Reenvío tras un rechazo: REJECTED → PENDING mediante actualización condicional. */
    @Transactional
    public RestaurantResponse resubmit(UUID ownerId) {
        Restaurant restaurant = find(ownerId);
        if (restaurant.getStatus() != RestaurantStatus.REJECTED) {
            throw new RestaurantStateException("Solo una solicitud rechazada puede reenviarse");
        }
        if (restaurants.resubmit(restaurant.getId(), ownerId, Instant.now(clock)) == 0) {
            throw new RestaurantConflictException("La solicitud cambió; vuelve a consultarla");
        }
        return respond(find(ownerId));
    }

    /** RF-07: horario semanal completo; solo un restaurante aprobado. */
    @Transactional
    public RestaurantResponse updateSchedule(UUID ownerId, ScheduleRequest request) {
        Restaurant restaurant = approved(find(ownerId));
        restaurant.replaceSchedule(parseSchedule(request));
        return respond(save(restaurant));
    }

    /** RF-07: activar o pausar la disponibilidad; solo un restaurante aprobado. */
    @Transactional
    public RestaurantResponse setAvailability(UUID ownerId, AvailabilityRequest request) {
        if (request == null || request.available() == null) {
            throw new RegistrationValidationException("available", "Indica si estás disponible");
        }
        Restaurant restaurant = approved(find(ownerId));
        restaurant.setAvailable(request.available());
        return respond(save(restaurant));
    }

    private Restaurant find(UUID ownerId) {
        return restaurants.findByOwnerId(ownerId)
                .orElseThrow(() -> new RestaurantNotFoundException("Aún no tienes una solicitud de restaurante"));
    }

    private static Restaurant approved(Restaurant restaurant) {
        if (restaurant.getStatus() != RestaurantStatus.APPROVED) {
            throw new RestaurantStateException("Tu restaurante debe estar aprobado para realizar esta acción");
        }
        return restaurant;
    }

    private Restaurant save(Restaurant restaurant) {
        try {
            return restaurants.saveAndFlush(restaurant);
        } catch (ObjectOptimisticLockingFailureException concurrentEdit) {
            throw new RestaurantConflictException("La solicitud cambió; vuelve a consultarla");
        }
    }

    private RestaurantResponse respond(Restaurant restaurant) {
        boolean ownerEnabled = accounts.findById(restaurant.getOwnerId()).map(Account::isEnabled).orElse(false);
        return RestaurantResponse.from(restaurant, eligibility.canReceiveOrders(restaurant, ownerEnabled));
    }

    private static RestaurantConflictException alreadyRequested() {
        return new RestaurantConflictException("Ya enviaste una solicitud de restaurante");
    }

    private record ValidatedData(String name, String category, String description, String address, String zone,
            String phone) {
    }

    private static ValidatedData validate(RestaurantRequest request) {
        if (request == null) {
            throw new RegistrationValidationException("name", "Completa los datos del restaurante");
        }
        String name = required("name", request.name(), 2, 100, "El nombre admite de 2 a 100 caracteres");
        String category = required("category", request.category(), 2, 50, "La categoría admite de 2 a 50 caracteres");
        String address = required("address", request.address(), 5, 200, "La dirección admite de 5 a 200 caracteres");
        String zone = required("zone", request.zone(), 2, 100, "La zona admite de 2 a 100 caracteres");
        String phone = request.phone() == null ? "" : request.phone().trim();
        if (!PHONE.matcher(phone).matches()) {
            throw new RegistrationValidationException("phone", "Ingresa un teléfono válido (7 a 15 dígitos)");
        }
        String description = request.description() == null ? "" : request.description().trim();
        if (description.length() > 300) {
            throw new RegistrationValidationException("description", "La descripción admite hasta 300 caracteres");
        }
        return new ValidatedData(name, category, description.isEmpty() ? null : description, address, zone, phone);
    }

    private static String required(String field, String value, int min, int max, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() < min || clean.length() > max) {
            throw new RegistrationValidationException(field, message);
        }
        return clean;
    }

    private static List<OpeningHour> parseSchedule(ScheduleRequest request) {
        if (request == null || request.days() == null || request.days().size() != 7) {
            throw new RegistrationValidationException("days", "Define los siete días de la semana");
        }
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        List<OpeningHour> hours = new ArrayList<>();
        for (OpeningHourDto dto : request.days()) {
            DayOfWeek day = parseDay(dto);
            if (!seen.add(day)) {
                throw new RegistrationValidationException("days", "Cada día debe aparecer una sola vez");
            }
            hours.add(parseHour(day, dto));
        }
        return hours;
    }

    private static DayOfWeek parseDay(OpeningHourDto dto) {
        try {
            return DayOfWeek.valueOf(dto == null || dto.day() == null ? "" : dto.day());
        } catch (IllegalArgumentException invalidDay) {
            throw new RegistrationValidationException("days", "Día de la semana no válido");
        }
    }

    private static OpeningHour parseHour(DayOfWeek day, OpeningHourDto dto) {
        if (dto.opensAt() == null && dto.closesAt() == null) {
            return new OpeningHour(day, null, null);
        }
        if (dto.opensAt() == null || dto.closesAt() == null
                || !HOUR.matcher(dto.opensAt()).matches() || !HOUR.matcher(dto.closesAt()).matches()) {
            throw new RegistrationValidationException("days", "Indica apertura y cierre en formato HH:mm");
        }
        try {
            LocalTime opens = LocalTime.parse(dto.opensAt());
            LocalTime closes = LocalTime.parse(dto.closesAt());
            if (!closes.isAfter(opens)) {
                throw new RegistrationValidationException("days", "El cierre debe ser posterior a la apertura");
            }
            return new OpeningHour(day, opens, closes);
        } catch (DateTimeParseException invalidHour) {
            throw new RegistrationValidationException("days", "Indica apertura y cierre en formato HH:mm");
        }
    }
}
