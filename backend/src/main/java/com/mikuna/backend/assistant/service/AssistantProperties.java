package com.mikuna.backend.assistant.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Límites del asistente (spec 013): mensajes por minuto y por cliente, y longitud de cada mensaje. */
@Validated
@ConfigurationProperties("mikuna.assistant")
public record AssistantProperties(
        @Min(value = 1, message = "mikuna.assistant.rate-limit-per-minute debe ser al menos 1") int rateLimitPerMinute,
        @Min(value = 1, message = "mikuna.assistant.max-message-length debe ser al menos 1") int maxMessageLength) {
}
