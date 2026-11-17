package com.mikuna.backend.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del token de sesión (mikuna.security.jwt.*). El secreto procede de backend/.env
 * y debe tener al menos 32 bytes para HS256; si no, la aplicación no arranca.
 */
@ConfigurationProperties(prefix = "mikuna.security.jwt")
public record JwtProperties(String secret, Duration expiration) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "mikuna.security.jwt.secret (MIKUNA_JWT_SECRET) debe tener al menos 32 bytes");
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("mikuna.security.jwt.expiration debe ser una duración positiva");
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
