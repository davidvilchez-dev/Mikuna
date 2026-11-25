package com.mikuna.backend.auth.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite de intentos fallidos de inicio de sesión (mikuna.security.login.*): por cuenta (adivinar la contraseña de
 * un correo) y por dirección IP (probar muchas cuentas desde un mismo origen), dentro de una ventana deslizante.
 */
@ConfigurationProperties(prefix = "mikuna.security.login")
public record LoginProperties(int maxFailuresPerAccount, int maxFailuresPerIp, Duration window) {

    public LoginProperties {
        if (maxFailuresPerAccount < 1 || maxFailuresPerIp < 1) {
            throw new IllegalStateException("mikuna.security.login.max-failures-* debe ser al menos 1");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("mikuna.security.login.window debe ser una duración positiva");
        }
    }
}
