package com.mikuna.backend.assistant.controller;

import com.mikuna.backend.assistant.dto.ChatRequest;
import com.mikuna.backend.assistant.service.AssistantModel.Turn;
import com.mikuna.backend.assistant.service.AssistantService;
import com.mikuna.backend.common.exception.AssistantBusyException;
import com.mikuna.backend.common.exception.AssistantUnavailableException;
import com.mikuna.backend.common.security.AccountPrincipal;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat con el asistente (spec 013). Ruta protegida bajo {@code /api/customer/**} (solo clientes). Validación y
 * límite de uso se resuelven antes de abrir el flujo, como respuestas HTTP normales; lo que falle después se
 * comunica con un evento {@code error}. El trabajo del proveedor corre en un ejecutor acotado.
 */
@RestController
@RequestMapping("/api/customer/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);
    private static final long TIMEOUT_MS = 120_000;

    private final AssistantService service;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ChatRequest request) {
        List<Turn> conversation = service.prepare(principal.id(), request);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        try {
            executor.execute(() -> run(conversation, emitter));
        } catch (RejectedExecutionException e) {
            fail(emitter, AssistantBusyException.CODE, AssistantBusyException.MESSAGE);
        }
        return emitter;
    }

    private void run(List<Turn> conversation, SseEmitter emitter) {
        try {
            service.stream(conversation, text -> send(emitter, "delta", Map.of("text", text)));
            send(emitter, "done", Map.of());
            emitter.complete();
        } catch (AssistantBusyException e) {
            fail(emitter, AssistantBusyException.CODE, AssistantBusyException.MESSAGE);
        } catch (AssistantUnavailableException e) {
            fail(emitter, AssistantUnavailableException.CODE, AssistantUnavailableException.MESSAGE);
        } catch (ClientGone e) {
            emitter.complete();
        } catch (RuntimeException e) {
            log.warn("Error inesperado en el chat: {}", e.getClass().getSimpleName());
            fail(emitter, AssistantUnavailableException.CODE, AssistantUnavailableException.MESSAGE);
        }
    }

    private void fail(SseEmitter emitter, String code, String message) {
        try {
            send(emitter, "error", Map.of("code", code, "message", message));
        } catch (ClientGone ignored) {
            // El navegador ya cerró el flujo.
        }
        emitter.complete();
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGone();
        }
    }

    /** El navegador cerró la conexión: se deja de generar y de consumir cuota. */
    private static final class ClientGone extends RuntimeException {
        ClientGone() {
            super(null, null, false, false);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
