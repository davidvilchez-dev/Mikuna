package com.mikuna.backend.restaurant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** Franja de atención de un día; apertura y cierre nulos significan cerrado. */
@Embeddable
public class OpeningHour {

    @Column(name = "day_of_week", nullable = false, length = 10)
    private String day;

    @Column(name = "opens_at")
    private LocalTime opensAt;

    @Column(name = "closes_at")
    private LocalTime closesAt;

    protected OpeningHour() {
        // JPA
    }

    public OpeningHour(DayOfWeek day, LocalTime opensAt, LocalTime closesAt) {
        this.day = day.name();
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public DayOfWeek getDay() { return DayOfWeek.valueOf(day); }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }

    public boolean isOpen() {
        return opensAt != null && closesAt != null;
    }

    /** Apertura inclusiva y cierre exclusivo. */
    public boolean contains(LocalTime time) {
        return isOpen() && !time.isBefore(opensAt) && time.isBefore(closesAt);
    }
}
