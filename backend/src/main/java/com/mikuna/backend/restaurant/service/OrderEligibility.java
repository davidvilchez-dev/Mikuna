package com.mikuna.backend.restaurant.service;

import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RF-06: regla única de «habilitado para recibir pedidos». Solo un restaurante aprobado, con cuenta
 * habilitada, disponible y dentro de su horario del día puede recibirlos. El incremento de pedidos
 * debe reutilizarla.
 */
@Component
public class OrderEligibility {

    private final Clock clock;
    private final ZoneId zone;

    public OrderEligibility(Clock clock, @Value("${mikuna.timezone}") String timezone) {
        this.clock = clock;
        this.zone = ZoneId.of(timezone);
    }

    public boolean canReceiveOrders(Restaurant restaurant, boolean ownerEnabled) {
        if (!ownerEnabled || restaurant.getStatus() != RestaurantStatus.APPROVED || !restaurant.isAvailable()) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        return restaurant.getSchedule().stream()
                .filter(hour -> hour.getDay() == now.getDayOfWeek())
                .anyMatch(hour -> hour.contains(now.toLocalTime()));
    }
}
