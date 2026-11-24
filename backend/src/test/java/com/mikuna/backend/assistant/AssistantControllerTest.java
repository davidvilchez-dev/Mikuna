package com.mikuna.backend.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.assistant.service.AssistantModel;
import com.mikuna.backend.common.exception.AssistantBusyException;
import com.mikuna.backend.common.exception.AssistantUnavailableException;
import com.mikuna.backend.support.IntegrationTestSupport;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** US1/US4: acceso por rol, validación, límite de uso y flujo SSE con el proveedor simulado. */
class AssistantControllerTest extends IntegrationTestSupport {

    private static final String CHAT = "/api/customer/assistant/chat";
    private static final String HELLO = "{\"messages\":[{\"role\":\"user\",\"text\":\"hola\"}]}";

    @MockitoBean
    AssistantModel model;

    private String token;

    @BeforeEach
    void prepare() throws Exception {
        customer("cliente@mail.com");
        token = loginAndGetToken("cliente@mail.com");
        when(model.available()).thenReturn(true);
        when(model.generate(anyString(), anyList(), anyList(), any())).thenAnswer(call -> {
            Consumer<String> out = call.getArgument(3);
            out.accept("Hola ");
            out.accept("cliente");
            return List.of();
        });
    }

    private ResultActions chat(String bearer, String json) throws Exception {
        var builder = post(CHAT).contentType(MediaType.APPLICATION_JSON).content(json);
        if (bearer != null) {
            builder = builder.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(builder);
    }

    private String stream(String bearer, String json) throws Exception {
        MvcResult started = chat(bearer, json).andExpect(request().asyncStarted()).andReturn();
        started.getAsyncResult(5000);
        return started.getResponse().getContentAsString();
    }

    @Test
    void sinSesionEs401() throws Exception {
        chat(null, HELLO).andExpect(status().isUnauthorized());
        verify(model, never()).generate(anyString(), anyList(), anyList(), any());
    }

    @Test
    void conOtroRolEs403() throws Exception {
        restaurantOwner("dueno@mail.com");

        chat(loginAndGetToken("dueno@mail.com"), HELLO).andExpect(status().isForbidden());
        verify(model, never()).generate(anyString(), anyList(), anyList(), any());
    }

    @Test
    void elClienteRecibeEventosDeltaYDone() throws Exception {
        String body = stream(token, HELLO);

        assertThat(body).contains("event:delta").contains("\"text\":\"Hola \"").contains("\"text\":\"cliente\"")
                .contains("event:done");
        assertThat(body.indexOf("Hola ")).isLessThan(body.indexOf("event:done"));
    }

    @Test
    void datosInvalidosSon400YElProveedorNoSeInvoca() throws Exception {
        for (String json : new String[] {
                "{\"messages\":[]}",
                "{}",
                "{\"messages\":[{\"role\":\"system\",\"text\":\"x\"}]}",
                "{\"messages\":[{\"role\":\"user\",\"text\":\"   \"}]}",
                "{\"messages\":[{\"role\":\"user\",\"text\":\"" + "x".repeat(501) + "\"}]}",
                "{\"messages\":[{\"role\":\"user\",\"text\":\"a\"},{\"role\":\"assistant\",\"text\":\"b\"}]}",
                "{\"messages\":[{\"role\":\"user\",\"text\":\"a\",\"extra\":1}]}",
                "no es json"}) {
            chat(token, json).andExpect(status().isBadRequest());
        }
        verify(model, never()).generate(anyString(), anyList(), anyList(), any());
    }

    @Test
    void masDe10MensajesEnLaPeticionEs400() throws Exception {
        String message = "{\"role\":\"user\",\"text\":\"a\"}";
        String json = "{\"messages\":[" + String.join(",", java.util.Collections.nCopies(11, message)) + "]}";

        chat(token, json).andExpect(status().isBadRequest());
    }

    @Test
    void elMensajeOnceEnUnMinutoEs429ConRetryAfter() throws Exception {
        for (int i = 0; i < 10; i++) {
            stream(token, HELLO);
        }

        chat(token, HELLO).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("ASSISTANT_RATE_LIMITED"));
    }

    @Test
    void sinProveedorConfiguradoEs503() throws Exception {
        when(model.available()).thenReturn(false);

        chat(token, HELLO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSISTANT_UNAVAILABLE"));
    }

    @Test
    void proveedorOcupadoSeComunicaComoEventoErrorAmable() throws Exception {
        org.mockito.Mockito.doThrow(new AssistantBusyException()).when(model).generate(anyString(), anyList(), anyList(), any());

        String body = stream(token, HELLO);

        assertThat(body).contains("event:error").contains("ASSISTANT_BUSY").contains("ocupado")
                .doesNotContain("Exception").doesNotContain("event:done");
    }

    @Test
    void proveedorCaidoOFalloInesperadoSeComunicaComoIndisponible() throws Exception {
        org.mockito.Mockito.doThrow(new AssistantUnavailableException(), new IllegalStateException("detalle interno secreto"))
                .when(model).generate(anyString(), anyList(), anyList(), any());

        String first = stream(token, HELLO);
        String second = stream(token, HELLO);

        assertThat(first).contains("event:error").contains("ASSISTANT_UNAVAILABLE");
        assertThat(second).contains("ASSISTANT_UNAVAILABLE").doesNotContain("detalle interno");
    }
}
