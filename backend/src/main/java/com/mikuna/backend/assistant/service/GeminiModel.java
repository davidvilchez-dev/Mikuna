package com.mikuna.backend.assistant.service;

import com.mikuna.backend.common.exception.AssistantBusyException;
import com.mikuna.backend.common.exception.AssistantUnavailableException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini (plan gratuito) mediante su API REST sobre HTTPS, en modo de flujo SSE (research §1). La clave viaja en la
 * cabecera {@code x-goog-api-key}, nunca en la URL, y no se registra ni se incluye en excepciones. Las pruebas
 * sustituyen la interfaz por un doble o apuntan este cliente a un servidor local.
 */
@Component
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiModel implements AssistantModel {

    private static final Logger log = LoggerFactory.getLogger(GeminiModel.class);
    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final RestClient client;
    private final GeminiProperties properties;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    public GeminiModel(GeminiProperties properties) {
        this(RestClient.builder().requestFactory(timeoutFactory()), properties, BASE_URL);
    }

    public GeminiModel(RestClient.Builder builder, GeminiProperties properties, String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.properties = properties;
    }

    private static JdkClientHttpRequestFactory timeoutFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public boolean available() {
        return properties.configured();
    }

    @Override
    public List<ToolCall> generate(String systemPrompt, List<Turn> conversation, List<ToolDeclaration> tools,
            Consumer<String> onText) {
        if (!available()) {
            throw new AssistantUnavailableException();
        }
        List<ToolCall> calls = new ArrayList<>();
        try {
            String body = mapper.writeValueAsString(request(systemPrompt, conversation, tools));
            client.post()
                    .uri("/models/{model}:streamGenerateContent?alt=sse", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 429) {
                            throw new AssistantBusyException();
                        }
                        if (status < 200 || status >= 300) {
                            log.warn("Gemini respondió con HTTP {}", status);
                            throw new AssistantUnavailableException();
                        }
                        readStream(new BufferedReader(new InputStreamReader(response.getBody(),
                                StandardCharsets.UTF_8)), onText, calls);
                        return null;
                    });
        } catch (AssistantBusyException | AssistantUnavailableException e) {
            throw e;
        } catch (RestClientException | UncheckedIOException | JacksonException e) {
            log.warn("Fallo al consultar Gemini: {}", e.getClass().getSimpleName());
            throw new AssistantUnavailableException();
        }
        return calls;
    }

    private void readStream(BufferedReader reader, Consumer<String> onText, List<ToolCall> calls) {
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (!data.isEmpty()) {
                    readChunk(mapper.readTree(data), onText, calls);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void readChunk(JsonNode chunk, Consumer<String> onText, List<ToolCall> calls) {
        for (JsonNode part : chunk.path("candidates").path(0).path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            JsonNode text = part.get("text");
            if (text != null && !text.asString().isEmpty()) {
                onText.accept(text.asString());
            }
            JsonNode call = part.get("functionCall");
            if (call != null) {
                Map<String, Object> args = call.hasNonNull("args") ? mapper.convertValue(call.get("args"), MAP)
                        : Map.of();
                String signature = part.hasNonNull("thoughtSignature") ? part.get("thoughtSignature").asString()
                        : null;
                calls.add(new ToolCall(call.path("name").asString(), args, signature));
            }
        }
    }

    private Map<String, Object> request(String systemPrompt, List<Turn> conversation, List<ToolDeclaration> tools) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        request.put("contents", conversation.stream().map(GeminiModel::content).toList());
        if (!tools.isEmpty()) {
            request.put("tools", List.of(Map.of("functionDeclarations", tools.stream().map(GeminiModel::declaration)
                    .toList())));
        }
        request.put("generationConfig", Map.of("maxOutputTokens", 1024, "temperature", 0.4));
        return request;
    }

    private static Map<String, Object> content(Turn turn) {
        List<Map<String, Object>> parts = new ArrayList<>();
        switch (turn.role()) {
            case USER -> parts.add(Map.of("text", turn.text()));
            case MODEL -> {
                if (!turn.text().isBlank()) {
                    parts.add(Map.of("text", turn.text()));
                }
                for (ToolCall call : turn.toolCalls()) {
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("functionCall", Map.of("name", call.name(), "args", call.args()));
                    if (call.signature() != null) {
                        part.put("thoughtSignature", call.signature());
                    }
                    parts.add(part);
                }
            }
            case TOOL -> turn.toolResults().forEach(result -> parts.add(Map.of("functionResponse",
                    Map.of("name", result.name(), "response", result.response()))));
        }
        return Map.of("role", turn.role() == Role.MODEL ? "model" : "user", "parts", parts);
    }

    private static Map<String, Object> declaration(ToolDeclaration tool) {
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("name", tool.name());
        declaration.put("description", tool.description());
        if (!tool.parameters().isEmpty()) {
            declaration.put("parameters", Map.of("type", "OBJECT", "properties", tool.parameters()));
        }
        return declaration;
    }
}
