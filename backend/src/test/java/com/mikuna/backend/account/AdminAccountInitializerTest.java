package com.mikuna.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.service.AccountRegistrationService;
import com.mikuna.backend.common.exception.EmailAlreadyRegisteredException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.support.TestAccounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AdminAccountInitializerTest {

    private static final String PASSWORD = "clave-admin-muy-segura";

    @Mock
    private AccountRegistrationService registrationService;

    private AdminAccountInitializer initializer(MockEnvironment environment) {
        return new AdminAccountInitializer(registrationService, environment);
    }

    private static MockEnvironment fullEnvironment() {
        return new MockEnvironment()
                .withProperty("MIKUNA_ADMIN_NAME", "Responsable")
                .withProperty("MIKUNA_ADMIN_EMAIL", "admin@mikuna.pe")
                .withProperty("MIKUNA_ADMIN_PASSWORD", PASSWORD);
    }

    @Test
    void creaElAdministradorSinRegistrarLaContrasena(CapturedOutput output) {
        when(registrationService.createAdmin("Responsable", "admin@mikuna.pe", PASSWORD))
                .thenReturn(TestAccounts.account(Role.ADMIN, "admin@mikuna.pe", "{hash}"));

        initializer(fullEnvironment()).run(new DefaultApplicationArguments());

        verify(registrationService).createAdmin("Responsable", "admin@mikuna.pe", PASSWORD);
        assertThat(output).contains("Administrador creado: admin@mikuna.pe").doesNotContain(PASSWORD);
    }

    @Test
    void fallaSiFaltaUnValor() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MIKUNA_ADMIN_NAME", "Responsable")
                .withProperty("MIKUNA_ADMIN_EMAIL", "admin@mikuna.pe");

        assertThatThrownBy(() -> initializer(environment).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MIKUNA_ADMIN_PASSWORD");
        verify(registrationService, never()).createAdmin(any(), any(), any());
    }

    @Test
    void fallaConContrasenaInvalidaSinMostrarla(CapturedOutput output) {
        MockEnvironment environment = fullEnvironment().withProperty("MIKUNA_ADMIN_PASSWORD", "x".repeat(129));
        when(registrationService.createAdmin("Responsable", "admin@mikuna.pe", "x".repeat(129)))
                .thenThrow(new RegistrationValidationException("password", "La contraseña es obligatoria y admite hasta 128 caracteres"));

        assertThatThrownBy(() -> initializer(environment).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(output).contains("No se creó el administrador").doesNotContain("x".repeat(129));
    }

    @Test
    void fallaSiElCorreoYaExiste() {
        when(registrationService.createAdmin("Responsable", "admin@mikuna.pe", PASSWORD))
                .thenThrow(new EmailAlreadyRegisteredException());

        assertThatThrownBy(() -> initializer(fullEnvironment()).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("El correo ya está registrado");
    }
}
