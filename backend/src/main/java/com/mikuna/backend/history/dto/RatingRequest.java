package com.mikuna.backend.history.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Valor de la calificación. Se recibe como número decimal para que un {@code 3.5} llegue al servicio y se
 * rechace, en lugar de truncarse en silencio a 3 al deserializar un entero.
 */
public record RatingRequest(@NotNull(message = "Indica una calificación de 1 a 5") BigDecimal value) {
}
