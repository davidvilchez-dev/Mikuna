package com.mikuna.backend.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikuna.backend.assistant.service.AssistantModel.ToolCall;
import com.mikuna.backend.assistant.service.AssistantModel.ToolDeclaration;
import com.mikuna.backend.assistant.service.AssistantModel.ToolResult;
import com.mikuna.backend.assistant.service.AssistantModel.Turn;
import com.mikuna.backend.assistant.service.GeminiModel;
import com.mikuna.backend.assistant.service.GeminiProperties;
import com.mikuna.backend.common.exception.AssistantBusyException;
import com.mikuna.backend.common.exception.AssistantUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** US4: el cliente de Gemini contra un servidor local simulado; nunca se llama al servicio real. */
class GeminiModelTest {

    private static final String KEY = "clave-secreta-de-prueba";

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> keyHeader = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private int status = 200;
    private String response = "";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GeminiModel model(String key) {
        return new GeminiModel(RestClient.builder(), new GeminiProperties(key, "test-model"),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private List<ToolCall> generate(GeminiModel model, List<String> texts) {
        return model.generate("sistema", List.of(Turn.user("hola")),
                List.of(new ToolDeclaration("buscar_platos", "d", Map.of("texto", Map.of("type", "STRING")))),
                texts::add);
    }

    @Test
    void laClaveVaEnLaCabeceraYNuncaEnLaUrl() {
        response = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hola\"}]}}]}\n\n";
        List<String> texts = new ArrayList<>();

        generate(model(KEY), texts);

        assertThat(keyHeader.get()).isEqualTo(KEY);
        assertThat(path.get()).contains("/models/test-model:streamGenerateContent").contains("alt=sse")
                .doesNotContain(KEY);
        assertThat(body.get()).contains("systemInstruction").contains("functionDeclarations")
                .contains("\"role\":\"user\"").doesNotContain(KEY);
    }

    @Test
    void emiteElTextoDeCadaFragmentoEnOrdenYIgnoraElPensamiento() {
        response = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hola \"}]}}]}\n\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"resumen\",\"thought\":true},"
                + "{\"text\":\"cliente\"}]}}]}\n\n"
                + ": comentario\n\n"
                + "data: \n\n";
        List<String> texts = new ArrayList<>();

        List<ToolCall> calls = generate(model(KEY), texts);

        assertThat(texts).containsExactly("Hola ", "cliente");
        assertThat(calls).isEmpty();
    }

    @Test
    void devuelveLasLlamadasAHerramientasConSuFirma() {
        response = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"buscar_platos\","
                + "\"args\":{\"texto\":\"ensalada\",\"precio_maximo\":20}},\"thoughtSignature\":\"abc\"},"
                + "{\"functionCall\":{\"name\":\"listar_categorias\"}}]}}]}\n\n";

        List<ToolCall> calls = generate(model(KEY), new ArrayList<>());

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).name()).isEqualTo("buscar_platos");
        assertThat(calls.get(0).args()).containsEntry("texto", "ensalada").containsEntry("precio_maximo", 20);
        assertThat(calls.get(0).signature()).isEqualTo("abc");
        assertThat(calls.get(1).args()).isEmpty();
        assertThat(calls.get(1).signature()).isNull();
    }

    @Test
    void enviaElHistorialConLlamadasYResultadosDeHerramientas() {
        response = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}\n\n";
        ToolCall call = new ToolCall("buscar_platos", Map.of("texto", "x"), "firma");

        model(KEY).generate("sistema", List.of(Turn.user("hola"),
                Turn.model("", List.of(call)),
                Turn.toolResults(List.of(new ToolResult("buscar_platos", Map.of("platos", List.of())))),
                Turn.model("listo", List.of())), List.of(), text -> { });

        assertThat(body.get()).contains("\"functionCall\"").contains("\"thoughtSignature\":\"firma\"")
                .contains("\"functionResponse\"").contains("\"role\":\"model\"").doesNotContain("\"tools\"");
    }

    @Test
    void sinClaveNoEstaDisponibleYNoLlamaAlServidor() {
        GeminiModel model = model("  ");

        assertThat(model.available()).isFalse();
        assertThatThrownBy(() -> generate(model, new ArrayList<>()))
                .isInstanceOf(AssistantUnavailableException.class);
        assertThat(path.get()).isNull();
    }

    @Test
    void estaDisponibleConClave() {
        assertThat(model(KEY).available()).isTrue();
    }

    @Test
    void http429EsOcupado() {
        status = 429;
        response = "{\"error\":\"cuota\"}";

        assertThatThrownBy(() -> generate(model(KEY), new ArrayList<>()))
                .isInstanceOf(AssistantBusyException.class);
    }

    @Test
    void erroresDelProveedorSonIndisponibleSinFiltrarDetalles() {
        for (int code : new int[] {500, 503, 401, 403, 400}) {
            status = code;
            response = "{\"error\":\"detalle interno con " + KEY + "\"}";

            assertThatThrownBy(() -> generate(model(KEY), new ArrayList<>()))
                    .isInstanceOf(AssistantUnavailableException.class)
                    .hasMessageNotContaining(KEY).hasMessageNotContaining("detalle interno");
        }
    }

    @Test
    void conexionCaidaEsIndisponible() {
        GeminiModel model = model(KEY);
        server.stop(0);

        assertThatThrownBy(() -> generate(model, new ArrayList<>()))
                .isInstanceOf(AssistantUnavailableException.class);
    }

    @Test
    void unaRespuestaCorruptaEsIndisponible() {
        response = "data: {esto no es json\n\n";

        assertThatThrownBy(() -> generate(model(KEY), new ArrayList<>()))
                .isInstanceOf(AssistantUnavailableException.class);
    }

    @Test
    void laClaveNoApareceEnElToString() {
        assertThat(new GeminiProperties(KEY, "m").toString()).doesNotContain(KEY);
    }
}
