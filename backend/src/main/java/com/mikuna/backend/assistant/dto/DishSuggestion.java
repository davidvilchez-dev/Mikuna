package com.mikuna.backend.assistant.dto;

import java.math.BigDecimal;

/** Plato que el asistente puede recomendar: sin identificadores, imágenes ni datos de usuarios. */
public record DishSuggestion(String name, String description, String category, BigDecimal price,
        String restaurant, boolean open) {
}
