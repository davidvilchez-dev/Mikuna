package com.mikuna.backend.assistant.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Acceso a Gemini (plan gratuito, constitución III). La clave es opcional: sin ella la aplicación arranca y el
 * chat responde que no está disponible. La clave nunca aparece en {@code toString()}.
 */
@Validated
@ConfigurationProperties("gemini")
public record GeminiProperties(String apiKey, @NotBlank(message = "gemini.model es obligatorio") String model) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "GeminiProperties[model=" + model + "]";
    }
}
