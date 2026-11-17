package com.mikuna.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.common.security.JwtProperties;
import com.mikuna.backend.support.TestAccounts;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

    private static final String SECRET = "secreto-de-prueba-de-al-menos-32-bytes!!";

    private final Instant now = Instant.now();
    private NimbusJwtDecoder decoder;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        tokenService = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                new JwtProperties(SECRET, Duration.ofHours(2)), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void emiteTokenConSujetoRolYVencimiento() {
        Account account = TestAccounts.account(Role.COURIER, "rep@mail.com", "{hash}");

        TokenService.IssuedToken issued = tokenService.issue(account);
        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo(account.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("COURIER");
        assertThat(issued.expiresAt()).isEqualTo(now.plus(Duration.ofHours(2)));
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void rechazaTokenAlterado() {
        String token = tokenService.issue(TestAccounts.account(Role.CUSTOMER, "a@mail.com", "{hash}")).value();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }
}
