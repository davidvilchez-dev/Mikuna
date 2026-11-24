package com.mikuna.backend.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.assistant.dto.ChatMessageDto;
import com.mikuna.backend.assistant.dto.ChatRequest;
import com.mikuna.backend.assistant.service.AssistantCatalogTools;
import com.mikuna.backend.assistant.service.AssistantModel;
import com.mikuna.backend.assistant.service.AssistantModel.Role;
import com.mikuna.backend.assistant.service.AssistantModel.ToolCall;
import com.mikuna.backend.assistant.service.AssistantModel.Turn;
import com.mikuna.backend.assistant.service.AssistantProperties;
import com.mikuna.backend.assistant.service.AssistantService;
import com.mikuna.backend.assistant.service.AssistantUsageLimiter;
import com.mikuna.backend.common.exception.AssistantMessageInvalidException;
import com.mikuna.backend.common.exception.AssistantRateLimitedException;
import com.mikuna.backend.common.exception.AssistantUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** US1/US2/US4: validación previa, bucle de herramientas y privacidad de lo que recibe el proveedor. */
@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    private static final UUID ACCOUNT = UUID.randomUUID();

    @Mock AssistantModel model;
    @Mock AssistantCatalogTools tools;
    @Mock AssistantUsageLimiter limiter;

    private AssistantService service;

    @BeforeEach
    void setUp() {
        service = new AssistantService(model, tools, limiter, new AssistantProperties(10, 500));
    }

    private static ChatRequest request(ChatMessageDto... messages) {
        return new ChatRequest(List.of(messages));
    }

    private static ChatMessageDto user(String text) {
        return new ChatMessageDto("user", text);
    }

    @Test
    void sinProveedorConfiguradoEstaNoDisponibleYNoConsumeCupo() {
        when(model.available()).thenReturn(false);

        assertThatThrownBy(() -> service.prepare(ACCOUNT, request(user("hola"))))
                .isInstanceOf(AssistantUnavailableException.class);
        verify(limiter, never()).acquire(any());
    }

    @Test
    void mensajeVacioLargoOUltimoNoClienteSeRechazanSinConsumirCupo() {
        when(model.available()).thenReturn(true);

        assertThatThrownBy(() -> service.prepare(ACCOUNT, request(user("   "))))
                .isInstanceOf(AssistantMessageInvalidException.class);
        assertThatThrownBy(() -> service.prepare(ACCOUNT, request(user("x".repeat(501)))))
                .isInstanceOf(AssistantMessageInvalidException.class);
        assertThatThrownBy(() -> service.prepare(ACCOUNT,
                request(user("hola"), new ChatMessageDto("assistant", "dime"))))
                .isInstanceOf(AssistantMessageInvalidException.class);
        verify(limiter, never()).acquire(any());
    }

    @Test
    void conservaSoloLosUltimos10MensajesYRecortaEspacios() {
        when(model.available()).thenReturn(true);
        List<ChatMessageDto> messages = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            messages.add(i % 2 == 0 ? user("  pregunta " + i + "  ") : new ChatMessageDto("assistant", "r" + i));
        }
        messages.add(user("última"));

        List<Turn> conversation = service.prepare(ACCOUNT, new ChatRequest(messages));

        assertThat(conversation).hasSize(10);
        assertThat(conversation.get(9).text()).isEqualTo("última");
        assertThat(conversation.get(0).role()).isEqualTo(Role.MODEL);
        verify(limiter).acquire(ACCOUNT);
    }

    @Test
    void siSuperaElLimiteLaExcepcionSePropagaYElProveedorNoSeLlama() {
        when(model.available()).thenReturn(true);
        org.mockito.Mockito.doThrow(new AssistantRateLimitedException(30)).when(limiter).acquire(ACCOUNT);

        assertThatThrownBy(() -> service.prepare(ACCOUNT, request(user("hola"))))
                .isInstanceOf(AssistantRateLimitedException.class);
        verify(model, never()).generate(anyString(), anyList(), anyList(), any());
    }

    @Test
    void respondeSinHerramientasReenviandoElTexto() {
        when(model.generate(anyString(), anyList(), anyList(), any())).thenAnswer(call -> {
            Consumer<String> out = call.getArgument(3);
            out.accept("Hola ");
            out.accept("cliente");
            return List.of();
        });
        List<String> received = new ArrayList<>();

        service.stream(List.of(Turn.user("hola")), received::add);

        assertThat(received).containsExactly("Hola ", "cliente");
    }

    @Test
    void ejecutaLaHerramientaPedidaYDevuelveElResultadoAlModelo() {
        ToolCall call = new ToolCall("buscar_platos", Map.of("texto", "ensalada"), "sig");
        when(tools.execute(call)).thenReturn(Map.of("platos", List.of()));
        when(model.generate(anyString(), anyList(), anyList(), any()))
                .thenReturn(List.of(call))
                .thenAnswer(c -> {
                    Consumer<String> out = c.getArgument(3);
                    out.accept("No hay ensaladas");
                    return List.of();
                });
        List<String> received = new ArrayList<>();

        service.stream(List.of(Turn.user("ensalada")), received::add);

        assertThat(received).containsExactly("No hay ensaladas");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(model, org.mockito.Mockito.times(2)).generate(anyString(), turns.capture(), anyList(), any());
        List<Turn> second = turns.getAllValues().get(1);
        assertThat(second).extracting(Turn::role).containsExactly(Role.USER, Role.MODEL, Role.TOOL);
        assertThat(second.get(2).toolResults()).singleElement()
                .satisfies(r -> assertThat(r.name()).isEqualTo("buscar_platos"));
    }

    @Test
    void cortaAlas3VueltasDeHerramientas() {
        ToolCall call = new ToolCall("buscar_platos", Map.of(), null);
        when(tools.execute(call)).thenReturn(Map.of());
        when(model.generate(anyString(), anyList(), anyList(), any())).thenReturn(List.of(call));

        assertThatThrownBy(() -> service.stream(List.of(Turn.user("x")), text -> { }))
                .isInstanceOf(AssistantUnavailableException.class);
        verify(model, org.mockito.Mockito.times(4)).generate(anyString(), anyList(), anyList(), any());
    }

    @Test
    void elProveedorNuncaRecibeDatosQueIdentifiquenAlCliente() {
        when(model.available()).thenReturn(true);
        when(model.generate(anyString(), anyList(), anyList(), any())).thenReturn(List.of());

        List<Turn> conversation = service.prepare(ACCOUNT, request(user("quiero algo vegetariano")));
        service.stream(conversation, text -> { });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Turn>> turns = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(model).generate(prompt.capture(), turns.capture(), anyList(), any());
        assertThat(turns.getValue().toString()).doesNotContain(ACCOUNT.toString());
        assertThat(prompt.getValue()).doesNotContain(ACCOUNT.toString())
                .contains("no instrucciones").contains("No reveles");
    }
}
