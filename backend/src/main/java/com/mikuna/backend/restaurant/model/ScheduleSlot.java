package com.mikuna.backend.restaurant.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Franja de atención como valor inmutable, apta para guardarse en la caché pública (spec 015). Apertura y cierre
 * nulos significan cerrado, igual que {@link OpeningHour}.
 */
public record ScheduleSlot(DayOfWeek day, LocalTime opensAt, LocalTime closesAt) {

    public static ScheduleSlot of(OpeningHour hour) {
        return new ScheduleSlot(hour.getDay(), hour.getOpensAt(), hour.getClosesAt());
    }

    /** Apertura inclusiva y cierre exclusivo; {@link OpeningHour#contains} delega aquí. */
    public boolean contains(LocalTime time) {
        return opensAt != null && closesAt != null && !time.isBefore(opensAt) && time.isBefore(closesAt);
    }
}
