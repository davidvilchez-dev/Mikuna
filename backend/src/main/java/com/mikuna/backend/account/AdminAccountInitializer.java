package com.mikuna.backend.account;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.service.AccountRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Procedimiento local controlado por el responsable para crear una cuenta ADMIN (RF-01).
 * Solo se activa con el perfil «admin-setup» y lee los datos de MIKUNA_ADMIN_NAME, MIKUNA_ADMIN_EMAIL
 * y MIKUNA_ADMIN_PASSWORD. No existe valor por defecto ni alta administrativa pública.
 */
@Component
@Profile("admin-setup")
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AccountRegistrationService registrationService;
    private final Environment environment;

    public AdminAccountInitializer(AccountRegistrationService registrationService, Environment environment) {
        this.registrationService = registrationService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String name = required("MIKUNA_ADMIN_NAME");
        String email = required("MIKUNA_ADMIN_EMAIL");
        String password = required("MIKUNA_ADMIN_PASSWORD");
        try {
            Account admin = registrationService.createAdmin(name, email, password);
            log.info("Administrador creado: {}", admin.getEmail());
        } catch (RuntimeException failure) {
            // Solo el motivo (nunca la contraseña); el arranque falla con código distinto de cero.
            log.error("No se creó el administrador: {}", failure.getMessage());
            throw new IllegalStateException("No se creó el administrador: " + failure.getMessage());
        }
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta la variable " + key + " para crear el administrador");
        }
        return value;
    }
}
