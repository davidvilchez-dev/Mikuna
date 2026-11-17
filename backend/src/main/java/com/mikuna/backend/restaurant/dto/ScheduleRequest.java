package com.mikuna.backend.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Horario semanal completo: los siete días, sin repetir. */
public record ScheduleRequest(
        @NotNull(message = "El horario es obligatorio")
        @Size(min = 7, max = 7, message = "Define los siete días de la semana") List<OpeningHourDto> days) {
}
