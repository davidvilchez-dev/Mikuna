package com.mikuna.backend.support;

import com.mikuna.backend.restaurant.model.OpeningHour;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Restaurantes en memoria para pruebas unitarias (sin base de datos). */
public final class TestRestaurants {

    private TestRestaurants() {
    }

    public static Restaurant restaurant(UUID ownerId, RestaurantStatus status) {
        Restaurant restaurant = Restaurant.request(ownerId, "La Picantería", "Criolla", null, "Jr. Lima 123",
                "Ayacucho", "966123456", Instant.parse("2026-11-16T10:00:00Z"));
        ReflectionTestUtils.setField(restaurant, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(restaurant, "status", status.name());
        return restaurant;
    }

    /** Aprobado, con el lunes abierto de 09:00 a 21:00 y el resto de días cerrado. */
    public static Restaurant approvedOpenOnMonday(UUID ownerId, boolean available) {
        Restaurant restaurant = restaurant(ownerId, RestaurantStatus.APPROVED);
        restaurant.replaceSchedule(java.util.Arrays.stream(DayOfWeek.values())
                .map(day -> day == DayOfWeek.MONDAY
                        ? new OpeningHour(day, LocalTime.of(9, 0), LocalTime.of(21, 0))
                        : new OpeningHour(day, null, null))
                .toList());
        restaurant.setAvailable(available);
        return restaurant;
    }
}
