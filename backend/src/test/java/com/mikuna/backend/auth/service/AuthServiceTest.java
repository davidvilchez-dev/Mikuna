package com.mikuna.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.RestaurantApproval;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.auth.dto.AccountResponse;
import com.mikuna.backend.auth.dto.LoginResponse;
import com.mikuna.backend.common.exception.AccountDisabledException;
import com.mikuna.backend.common.exception.InvalidCredentialsException;
import com.mikuna.backend.support.TestAccounts;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String DUMMY_HASH = "{dummy}";

    @Mock
    private AccountRepository accounts;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        service = new AuthService(accounts, passwordEncoder, tokenService);
    }

    @Test
    void loginCorrectoNormalizaCorreoYDevuelveToken() {
        Account account = TestAccounts.account(Role.CUSTOMER, "ana@mail.com", "{hash}");
        when(accounts.findByEmail("ana@mail.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("clave", "{hash}")).thenReturn(true);
        Instant expiresAt = Instant.parse("2026-11-17T12:00:00Z");
        when(tokenService.issue(account)).thenReturn(new TokenService.IssuedToken("jwt", expiresAt));

        LoginResponse response = service.login("  ANA@mail.com ", "clave");

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.account().email()).isEqualTo("ana@mail.com");
    }

    @Test
    void correoInexistenteCompruebaHashFicticioYFalla() {
        when(accounts.findByEmail("nadie@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nadie@mail.com", "clave"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Correo o contraseña incorrectos");
        verify(passwordEncoder).matches("clave", DUMMY_HASH);
        verify(tokenService, never()).issue(any());
    }

    @Test
    void contrasenaIncorrectaFallaConElMismoMensaje() {
        Account account = TestAccounts.account(Role.CUSTOMER, "ana@mail.com", "{hash}");
        when(accounts.findByEmail("ana@mail.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("mala", "{hash}")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ana@mail.com", "mala"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Correo o contraseña incorrectos");
    }

    @Test
    void cuentaInhabilitadaConClaveCorrectaEsRechazada() {
        Account account = TestAccounts.disabled(TestAccounts.account(Role.COURIER, "rep@mail.com", "{hash}"));
        when(accounts.findByEmail("rep@mail.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("clave", "{hash}")).thenReturn(true);

        assertThatThrownBy(() -> service.login("rep@mail.com", "clave")).isInstanceOf(AccountDisabledException.class);
        verify(tokenService, never()).issue(any());
    }

    @Test
    void restaurantePendientePuedeIniciarSesionSinAprobacion() {
        Account account = TestAccounts.account(Role.RESTAURANT, "local@mail.com", "{hash}");
        when(accounts.findByEmail("local@mail.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("clave", "{hash}")).thenReturn(true);
        when(tokenService.issue(account)).thenReturn(new TokenService.IssuedToken("jwt", Instant.now()));

        LoginResponse response = service.login("local@mail.com", "clave");

        assertThat(response.account().restaurantApproval()).isEqualTo(RestaurantApproval.PENDING);
    }

    @Test
    void consultaLaCuentaPropia() {
        Account account = TestAccounts.account(Role.ADMIN, "admin@mail.com", "{hash}");
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        AccountResponse response = service.currentAccount(account.getId());

        assertThat(response.id()).isEqualTo(account.getId());
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void cuentaPropiaInexistenteFalla() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.currentAccount(id)).isInstanceOf(InvalidCredentialsException.class);
    }
}
