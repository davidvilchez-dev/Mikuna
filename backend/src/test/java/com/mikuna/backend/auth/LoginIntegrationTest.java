package com.mikuna.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class LoginIntegrationTest extends IntegrationTestSupport {

    private static final String BAD_CREDENTIALS = "Correo o contraseña incorrectos";

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private Account create(Role role, String email) {
        return role == Role.ADMIN ? admin(email)
                : registrationService.registerPublic("Titular", email, PASSWORD, PASSWORD, role);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void losCuatroRolesInicianSesionYConsultanSuCuenta(Role role) throws Exception {
        create(role, "titular@mail.com");

        login(" Titular@Mail.com ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.account.role").value(role.name()))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString("passwordHash"))));

        String token = loginAndGetToken("titular@mail.com");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("titular@mail.com"))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.status").value("ENABLED"));
    }

    @Test
    void credencialesIncorrectasNoRevelanSiElCorreoExiste() throws Exception {
        create(Role.CUSTOMER, "ana@mail.com");

        login("nadie@mail.com", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(BAD_CREDENTIALS));
        login("ana@mail.com", "contraseña-incorrecta").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(BAD_CREDENTIALS));
    }

    @Test
    void cuentaInhabilitadaNoObtieneSesion() throws Exception {
        Account account = create(Role.COURIER, "rep@mail.com");
        disable(account.getId());

        login("rep@mail.com", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La cuenta no está habilitada"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void cuerpoInvalidoResponde400() throws Exception {
        login("", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void restaurantePendienteVeSuCondicion() throws Exception {
        create(Role.RESTAURANT, "local@mail.com");
        String token = loginAndGetToken("local@mail.com");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantApproval").value("PENDING"));
    }
}
