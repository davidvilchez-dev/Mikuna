package com.mikuna.backend.restaurant.service;

import com.mikuna.backend.restaurant.model.ScheduleSlot;
import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
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
        return attentionStatus(restaurant.isAvailable(), restaurant.getSchedule().stream()
                .map(ScheduleSlot::of)
                .toList());
    }

    /**
     * La misma regla sobre los datos guardados en la caché pública (spec 015): se evalúa en cada consulta con la
     * hora actual, nunca se guarda el resultado (FR-002).
     */
    public AttentionStatus attentionStatus(boolean available, List<ScheduleSlot> schedule) {
        if (!available) {
            return AttentionStatus.PAUSED;
        }
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        boolean inSchedule = schedule.stream()
                .filter(slot -> slot.day() == now.getDayOfWeek())
                .anyMatch(slot -> slot.contains(now.toLocalTime()));
        return inSchedule ? AttentionStatus.OPEN : AttentionStatus.OUTSIDE_HOURS;
    }
}
