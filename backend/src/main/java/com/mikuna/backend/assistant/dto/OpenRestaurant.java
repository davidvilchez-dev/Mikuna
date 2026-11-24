package com.mikuna.backend.assistant.dto;

/** Restaurante que puede recibir pedidos ahora; {@code todaySchedule} es el horario del día. */
public record OpenRestaurant(String name, String category, String zone, String todaySchedule) {
}
