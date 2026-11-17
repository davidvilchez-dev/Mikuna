package com.mikuna.backend.support;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.account.service.AccountRegistrationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base de las pruebas de integración contra mikuna_test_db (PostgreSQL local). */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "contraseña-segura";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected AccountRepository accounts;

    @Autowired
    protected AccountRegistrationService registrationService;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        accounts.deleteAll();
    }

    protected String loginAndGetToken(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected Account admin(String email) {
        return registrationService.createAdmin("Responsable", email, PASSWORD);
    }

    protected void disable(UUID accountId) {
        jdbc.update("update cuentas set status = 'DISABLED' where id = ?", accountId);
    }
}
