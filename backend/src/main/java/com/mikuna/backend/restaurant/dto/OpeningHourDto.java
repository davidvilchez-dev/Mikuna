package com.mikuna.backend.restaurant.dto;

/** Día de la semana (MONDAY…SUNDAY) con horas HH:mm; ambas nulas significan cerrado. */
public record OpeningHourDto(String day, String opensAt, String closesAt) {
}
