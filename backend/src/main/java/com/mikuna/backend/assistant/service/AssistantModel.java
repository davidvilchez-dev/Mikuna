package com.mikuna.backend.assistant.service;

import com.mikuna.backend.common.exception.AssistantBusyException;
import com.mikuna.backend.common.exception.AssistantUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Proveedor de IA aislado de las reglas del chat (constitución II, enmienda 3.2.0): cambiarlo no exige tocar la
 * interfaz ni las consultas al catálogo. Una llamada es una «vuelta»: el modelo emite texto a medida que lo genera
 * y, si necesita datos, devuelve las herramientas que quiere ejecutar.
 */
public interface AssistantModel {

    /** Falso si falta la configuración del proveedor (por ejemplo, la clave). */
    boolean available();

    /**
     * @param onText recibe cada fragmento de texto en orden
     * @return herramientas pedidas por el modelo; vacío si terminó de responder
     * @throws AssistantBusyException si el proveedor agotó su cuota
     * @throws AssistantUnavailableException si no está configurado, no responde o falla
     */
    List<ToolCall> generate(String systemPrompt, List<Turn> conversation, List<ToolDeclaration> tools,
            Consumer<String> onText);

    enum Role { USER, MODEL, TOOL }

    /** Turno de la conversación: del cliente, del modelo (texto y llamadas) o con resultados de herramientas. */
    record Turn(Role role, String text, List<ToolCall> toolCalls, List<ToolResult> toolResults) {

        public static Turn user(String text) {
            return new Turn(Role.USER, text, List.of(), List.of());
        }

        public static Turn model(String text, List<ToolCall> calls) {
            return new Turn(Role.MODEL, text, List.copyOf(calls), List.of());
        }

        public static Turn toolResults(List<ToolResult> results) {
            return new Turn(Role.TOOL, "", List.of(), List.copyOf(results));
        }
    }

    /** {@code signature} es opaca: algunos modelos exigen devolverla junto con la llamada. */
    record ToolCall(String name, Map<String, Object> args, String signature) {
    }

    record ToolResult(String name, Map<String, Object> response) {
    }

    /** {@code parameters} es un esquema de objeto con propiedades (nombre → tipo y descripción). */
    record ToolDeclaration(String name, String description, Map<String, Map<String, String>> parameters) {
    }
}
