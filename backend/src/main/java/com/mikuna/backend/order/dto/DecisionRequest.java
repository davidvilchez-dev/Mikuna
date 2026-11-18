package com.mikuna.backend.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/** Motivo opcional de un rechazo o una cancelación (FR: máximo 200 caracteres). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record DecisionRequest(@Size(max = 200, message = "El motivo no puede superar los 200 caracteres")
        String reason) {

    public static final DecisionRequest NONE = new DecisionRequest(null);
}
