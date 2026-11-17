package com.mikuna.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final String SECRET_32 = "x".repeat(32);

    @Test
    void aceptaSecretoDe32BytesYDuracionPositiva() {
        JwtProperties properties = new JwtProperties(SECRET_32, Duration.ofHours(2));
        assertThat(properties.secretBytes()).hasSize(32);
    }

    @Test
    void rechazaSecretoAusenteOCorto() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(2))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("x".repeat(31), Duration.ofHours(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaDuracionNoPositiva() {
        assertThatThrownBy(() -> new JwtProperties(SECRET_32, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET_32, Duration.ZERO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET_32, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
