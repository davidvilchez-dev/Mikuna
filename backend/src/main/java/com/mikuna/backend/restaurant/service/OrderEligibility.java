package com.mikuna.backend.restaurant.service;

import com.mikuna.backend.restaurant.model.AttentionStatus;
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
 * debe reutilizarla. La situación de atención (spec 004) es la base de esa regla.
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
        return ownerEnabled && restaurant.getStatus() == RestaurantStatus.APPROVED
                && attentionStatus(restaurant) == AttentionStatus.OPEN;
    }

    /** Pausa del titular primero; si no, dentro o fuera del horario del día (hora local de la plataforma). */
    public AttentionStatus attentionStatus(Restaurant restaurant) {
        if (!restaurant.isAvailable()) {
            return AttentionStatus.PAUSED;
        }
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        boolean inSchedule = restaurant.getSchedule().stream()
                .filter(hour -> hour.getDay() == now.getDayOfWeek())
                .anyMatch(hour -> hour.contains(now.toLocalTime()));
        return inSchedule ? AttentionStatus.OPEN : AttentionStatus.OUTSIDE_HOURS;
    }
}
