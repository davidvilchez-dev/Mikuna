package com.mikuna.backend.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.AccountStatus;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.common.exception.EmailAlreadyRegisteredException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountRegistrationServiceTest {

    private static final String PASSWORD = "contraseña-segura";

    @Mock
    private AccountRepository accounts;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new AccountRegistrationService(accounts, passwordEncoder);
    }

    private void stubSave() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{hash}xyz");
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void normalizaCorreoYGuardaSoloElHash() {
        stubSave();

        Account account = service.registerPublic("  Ana Quispe ", " Ana@Mail.com ", PASSWORD, PASSWORD, Role.CUSTOMER);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@mail.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ana Quispe");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{hash}xyz").doesNotContain(PASSWORD);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ENABLED);
        verify(accounts).existsByEmail("ana@mail.com");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "RESTAURANT", "COURIER"})
    void registraLosTresRolesPublicosHabilitados(Role role) {
        stubSave();
        Account account = service.registerPublic("Nombre", "persona@mail.com", PASSWORD, PASSWORD, role);
        assertThat(account.getRole()).isEqualTo(role);
        assertThat(account.isEnabled()).isTrue();
    }

    @Test
    void rechazaAdministradorPorLaViaPublica() {
        assertThatThrownBy(() -> service.registerPublic("Admin", "admin@mail.com", PASSWORD, PASSWORD, Role.ADMIN))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting("field").isEqualTo("role");
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void rechazaRolNulo() {
        assertThatThrownBy(() -> service.registerPublic("Ana", "ana@mail.com", PASSWORD, PASSWORD, null))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting("field").isEqualTo("role");
    }

    @Test
    void rechazaNombreVacioOLargo() {
        assertField(() -> service.registerPublic("   ", "a@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER), "name");
        assertField(() -> service.registerPublic(null, "a@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER), "name");
        assertField(() -> service.registerPublic("x".repeat(101), "a@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER),
                "name");
    }

    @Test
    void aceptaNombreDeCienCaracteresTrasRecortar() {
        stubSave();
        Account account = service.registerPublic("  " + "x".repeat(100) + "  ", "a@mail.com", PASSWORD, PASSWORD,
                Role.CUSTOMER);
        assertThat(account.getName()).hasSize(100);
    }

    @Test
    void rechazaCorreoInvalidoOLargo() {
        assertField(() -> service.registerPublic("Ana", "sin-arroba", PASSWORD, PASSWORD, Role.CUSTOMER), "email");
        assertField(() -> service.registerPublic("Ana", "a b@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER), "email");
        assertField(() -> service.registerPublic("Ana", null, PASSWORD, PASSWORD, Role.CUSTOMER), "email");
        String longEmail = "a".repeat(245) + "@mail.com"; // 254 + 1
        assertField(() -> service.registerPublic("Ana", "x" + longEmail, PASSWORD, PASSWORD, Role.CUSTOMER), "email");
    }

    @Test
    void rechazaContrasenaFueraDeLimites() {
        assertField(() -> service.registerPublic("Ana", "a@mail.com", "", "", Role.CUSTOMER), "password");
        assertField(() -> service.registerPublic("Ana", "a@mail.com", "x".repeat(129), "x".repeat(129), Role.CUSTOMER),
                "password");
        assertField(() -> service.registerPublic("Ana", "a@mail.com", null, null, Role.CUSTOMER), "password");
    }

    @Test
    void aceptaContrasenaCorta() {
        when(passwordEncoder.encode("1")).thenReturn("{hash}1");
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.registerPublic("Ana", "a@mail.com", "1", "1", Role.CUSTOMER).getPasswordHash())
                .isEqualTo("{hash}1");
    }

    @Test
    void rechazaConfirmacionDistinta() {
        assertField(() -> service.registerPublic("Ana", "a@mail.com", PASSWORD, PASSWORD + "x", Role.CUSTOMER),
                "passwordConfirmation");
    }

    @Test
    void rechazaCorreoYaRegistrado() {
        when(accounts.existsByEmail("ana@mail.com")).thenReturn(true);
        assertThatThrownBy(() -> service.registerPublic("Ana", "ANA@mail.com", PASSWORD, PASSWORD, Role.COURIER))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void traduceViolacionDeUnicidadConcurrente() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{hash}xyz");
        when(accounts.saveAndFlush(any(Account.class))).thenThrow(new DataIntegrityViolationException("uk_cuentas_email"));
        assertThatThrownBy(() -> service.registerPublic("Ana", "ana@mail.com", PASSWORD, PASSWORD, Role.CUSTOMER))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void creaAdministradorHabilitado() {
        stubSave();
        Account admin = service.createAdmin("Responsable", " Admin@Mikuna.pe ", PASSWORD);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getEmail()).isEqualTo("admin@mikuna.pe");
        assertThat(admin.isEnabled()).isTrue();
    }

    @Test
    void administradorAplicaLasMismasValidaciones() {
        assertField(() -> service.createAdmin("Responsable", "admin@mikuna.pe", ""), "password");
    }

    private static void assertField(Runnable call, String field) {
        assertThatThrownBy(call::run).isInstanceOf(RegistrationValidationException.class)
                .extracting("field").isEqualTo(field);
    }
}
