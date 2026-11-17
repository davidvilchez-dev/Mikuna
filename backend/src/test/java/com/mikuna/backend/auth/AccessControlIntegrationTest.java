package com.mikuna.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.auth.service.TokenService;
import com.mikuna.backend.common.security.JwtProperties;
import com.mikuna.backend.support.IntegrationTestSupport;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** RF-04 / RNF-07: 401/403, suplantación y elevación de privilegios en los accesos implementados. */
class AccessControlIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokenConFirmaAlteradaResponde401() throws Exception {
        customer("ana@mail.com");
        String token = loginAndGetToken("ana@mail.com");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoResponde401() throws Exception {
        Account account = customer("ana@mail.com");
        Clock threeHoursAgo = Clock.fixed(Instant.now().minus(3, ChronoUnit.HOURS), ZoneOffset.UTC);
        String expired = new TokenService(jwtEncoder, jwtProperties, threeHoursAgo).issue(account).value();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeCuentaEliminadaResponde401() throws Exception {
        Account account = customer("ana@mail.com");
        String token = loginAndGetToken("ana@mail.com");
        accounts.deleteById(account.getId());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cuentaInhabilitadaConTokenVigenteResponde403() throws Exception {
        Account account = customer("ana@mail.com");
        String token = loginAndGetToken("ana@mail.com");
        disable(account.getId());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La cuenta no está habilitada"));
    }

    @Test
    void parametrosDelClienteNoCambianIdentidadNiRol() throws Exception {
        customer("ana@mail.com");
        Account other = admin("admin@mail.com");
        String token = loginAndGetToken("ana@mail.com");
        mvc.perform(get("/api/auth/me").param("id", other.getId().toString())
                        .header("X-Role", "ADMIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void tokenFirmadoConOtroSecretoResponde401() throws Exception {
        Account account = customer("ana@mail.com");
        String foreignSecret = "otro-secreto-distinto-de-al-menos-32-bytes";
        var foreignEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(foreignSecret.getBytes(), "HmacSHA256")));
        String forged = new TokenService(foreignEncoder, new JwtProperties(foreignSecret, Duration.ofHours(2)),
                Clock.systemUTC()).issue(account).value();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otrasRutasDeLaApiExigenAutenticacion() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
