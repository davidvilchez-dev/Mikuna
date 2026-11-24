package com.mikuna.backend.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Mensaje del chat: solo el cliente ({@code user}) o el asistente ({@code assistant}); texto no vacío. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ChatMessageDto(
        @NotBlank(message = "El rol es obligatorio")
        @Pattern(regexp = "user|assistant", message = "El rol debe ser user o assistant")
        String role,

        @NotBlank(message = "El mensaje no puede estar vacío")
        String text) {
}
