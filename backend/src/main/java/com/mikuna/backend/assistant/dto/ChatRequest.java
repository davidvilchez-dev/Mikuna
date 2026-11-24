package com.mikuna.backend.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Historial reciente de la conversación; el servidor no guarda conversaciones (FR-017). */
public record ChatRequest(
        @NotEmpty(message = "Escribe un mensaje")
        @Size(max = 10, message = "La conversación admite hasta 10 mensajes")
        List<@Valid ChatMessageDto> messages) {
}
