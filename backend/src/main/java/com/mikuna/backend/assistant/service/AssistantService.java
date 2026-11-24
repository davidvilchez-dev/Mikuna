package com.mikuna.backend.assistant.service;

import com.mikuna.backend.assistant.dto.ChatMessageDto;
import com.mikuna.backend.assistant.dto.ChatRequest;
import com.mikuna.backend.assistant.service.AssistantModel.ToolCall;
import com.mikuna.backend.assistant.service.AssistantModel.ToolResult;
import com.mikuna.backend.assistant.service.AssistantModel.Turn;
import com.mikuna.backend.common.exception.AssistantMessageInvalidException;
import com.mikuna.backend.common.exception.AssistantUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Coordina el chat (spec 013): valida el historial, aplica el límite por cliente y ejecuta el bucle de
 * herramientas con el proveedor. No guarda conversaciones y no entrega al proveedor nada que identifique al cliente.
 */
@Service
public class AssistantService {

    static final int MAX_HISTORY = 10;
    static final int MAX_TOOL_ROUNDS = 3;

    private final AssistantModel model;
    private final AssistantCatalogTools tools;
    private final AssistantUsageLimiter limiter;
    private final AssistantProperties properties;

    public AssistantService(AssistantModel model, AssistantCatalogTools tools, AssistantUsageLimiter limiter,
            AssistantProperties properties) {
        this.model = model;
        this.tools = tools;
        this.limiter = limiter;
        this.properties = properties;
    }

    /**
     * Fase síncrona, antes de abrir el flujo: comprueba que el proveedor esté configurado, valida los mensajes y
     * consume cupo. Un mensaje inválido no consume cupo ni llega al proveedor.
     */
    public List<Turn> prepare(UUID accountId, ChatRequest request) {
        if (!model.available()) {
            throw new AssistantUnavailableException();
        }
        List<ChatMessageDto> messages = request.messages();
        List<ChatMessageDto> recent = messages.subList(Math.max(0, messages.size() - MAX_HISTORY), messages.size());
        if (!"user".equals(recent.get(recent.size() - 1).role())) {
            throw new AssistantMessageInvalidException("El último mensaje debe ser del cliente");
        }
        List<Turn> conversation = new ArrayList<>();
        for (ChatMessageDto message : recent) {
            String text = message.text().trim();
            if (text.isEmpty()) {
                throw new AssistantMessageInvalidException("El mensaje no puede estar vacío");
            }
            if (text.length() > properties.maxMessageLength()) {
                throw new AssistantMessageInvalidException(
                        "El mensaje no puede superar los " + properties.maxMessageLength() + " caracteres");
            }
            conversation.add("user".equals(message.role()) ? Turn.user(text) : Turn.model(text, List.of()));
        }
        limiter.acquire(accountId);
        return conversation;
    }

    /** Fase de flujo: reenvía el texto al consumidor a medida que llega; lanza las excepciones del proveedor. */
    public void stream(List<Turn> conversation, Consumer<String> onText) {
        List<Turn> turns = new ArrayList<>(conversation);
        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            StringBuilder said = new StringBuilder();
            List<ToolCall> calls = model.generate(AssistantPrompt.SYSTEM, turns, tools.declarations(), text -> {
                said.append(text);
                onText.accept(text);
            });
            if (calls.isEmpty()) {
                return;
            }
            if (round == MAX_TOOL_ROUNDS) {
                throw new AssistantUnavailableException();
            }
            turns.add(Turn.model(said.toString(), calls));
            turns.add(Turn.toolResults(calls.stream()
                    .map(call -> new ToolResult(call.name(), tools.execute(call))).toList()));
        }
    }

}
