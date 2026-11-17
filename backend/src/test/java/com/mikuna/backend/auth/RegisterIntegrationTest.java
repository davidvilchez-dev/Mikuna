package com.mikuna.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.account.service.AccountRegistrationService;
import com.mikuna.backend.common.exception.EmailAlreadyRegisteredException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RegisterIntegrationTest {

    private static final String PASSWORD = "contraseña-segura";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AccountRegistrationService registrationService;

    @BeforeEach
    void clean() {
        accounts.deleteAll();
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String name, String email, String password, String confirmation, String role) {
        return """
                {"name":"%s","email":"%s","password":"%s","passwordConfirmation":"%s","role":"%s"}"""
                .formatted(name, email, password, confirmation, role);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "RESTAURANT", "COURIER"})
    void registraLosTresTiposPublicos(String role) throws Exception {
        register(body("Ana Quispe", " Ana@Mail.com ", PASSWORD, PASSWORD, role))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.restaurantApproval").value(role.equals("RESTAURANT") ? "PENDING" : null))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString(PASSWORD))));
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(accounts.findByEmail("ana@mail.com").orElseThrow().getPasswordHash()).doesNotContain(PASSWORD);
    }

    @Test
    void rechazaDatosInvalidos() throws Exception {
        register(body("", "a@mail.com", PASSWORD, PASSWORD, "CUSTOMER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        register(body("x".repeat(101), "a@mail.com", PASSWORD, PASSWORD, "CUSTOMER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        register(body("Ana", "correo-invalido", PASSWORD, PASSWORD, "CUSTOMER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
        register(body("Ana", "a".repeat(246) + "@mail.com", PASSWORD, PASSWORD, "CUSTOMER"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        register(body("Ana", "a@mail.com", "x".repeat(11), "x".repeat(11), "CUSTOMER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        register(body("Ana", "a@mail.com", "x".repeat(129), "x".repeat(129), "CUSTOMER"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());
        register(body("Ana", "a@mail.com", PASSWORD, PASSWORD + "x", "CUSTOMER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.passwordConfirmation").exists());
        assertThat(accounts.count()).isZero();
    }

    @Test
    void rechazaRolAdministrativoYCamposPrivilegiados() throws Exception {
        register(body("Ana", "a@mail.com", PASSWORD, PASSWORD, "ADMIN")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
        register(body("Ana", "a@mail.com", PASSWORD, PASSWORD, "SUPERUSER")).andExpect(status().isBadRequest());
        register("""
                {"name":"Ana","email":"a@mail.com","password":"%s","passwordConfirmation":"%s","role":"CUSTOMER",
                 "status":"ENABLED"}""".formatted(PASSWORD, PASSWORD)).andExpect(status().isBadRequest());
        register("""
                {"name":"Ana","email":"a@mail.com","password":"%s","passwordConfirmation":"%s","role":"RESTAURANT",
                 "restaurantApproval":"APPROVED"}""".formatted(PASSWORD, PASSWORD)).andExpect(status().isBadRequest());
        assertThat(accounts.count()).isZero();
    }

    @Test
    void rechazaCorreoRepetidoAunqueCambienMayusculasEspaciosORol() throws Exception {
        register(body("Ana", "ana@mail.com", PASSWORD, PASSWORD, "CUSTOMER")).andExpect(status().isCreated());

        register(body("Ana", "  ANA@MAIL.COM ", PASSWORD, PASSWORD, "CUSTOMER")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El correo ya está registrado"));
        register(body("Ana", "ana@mail.com", PASSWORD, PASSWORD, "COURIER")).andExpect(status().isConflict());

        assertThat(accounts.count()).isEqualTo(1);
        assertThat(accounts.findByEmail("ana@mail.com").orElseThrow().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registrosConcurrentesDelMismoCorreoCreanUnaSolaCuenta() throws Exception {
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> results = new ArrayList<>();
            Role[] roles = {Role.CUSTOMER, Role.COURIER};
            for (int i = 0; i < threads; i++) {
                Role role = roles[i];
                String email = i == 0 ? "ana@mail.com" : " ANA@mail.com";
                Callable<String> task = () -> {
                    start.await();
                    try {
                        registrationService.registerPublic("Ana", email, PASSWORD, PASSWORD, role);
                        return "created";
                    } catch (EmailAlreadyRegisteredException duplicate) {
                        return "conflict";
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get());
            }
            assertThat(outcomes).containsExactlyInAnyOrder("created", "conflict");
        } finally {
            pool.shutdownNow();
        }
        assertThat(accounts.count()).isEqualTo(1);
    }
}
