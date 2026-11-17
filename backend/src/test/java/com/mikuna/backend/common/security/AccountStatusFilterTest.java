package com.mikuna.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.support.TestAccounts;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AccountStatusFilterTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private FilterChain chain;

    private AccountStatusFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new AccountStatusFilter(accounts);
        request = new MockHttpServletRequest("GET", "/api/auth/me");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithJwt(String subject, String roleClaim) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(subject).claim("role", roleClaim)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void sinAutenticacionJwtContinuaSinCambios() throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void cuentaInexistenteResponde401() throws Exception {
        Account ghost = TestAccounts.account(Role.CUSTOMER, "x@mail.com", "{hash}");
        when(accounts.findById(ghost.getId())).thenReturn(Optional.empty());
        authenticateWithJwt(ghost.getId().toString(), "CUSTOMER");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void sujetoNoUuidResponde401() throws Exception {
        authenticateWithJwt("no-es-uuid", "ADMIN");
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void cuentaInhabilitadaResponde403() throws Exception {
        Account account = TestAccounts.disabled(TestAccounts.account(Role.COURIER, "r@mail.com", "{hash}"));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        authenticateWithJwt(account.getId().toString(), "COURIER");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("La cuenta no está habilitada");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void cuentaHabilitadaUsaElRolDeLaBaseDeDatos() throws Exception {
        Account account = TestAccounts.account(Role.CUSTOMER, "c@mail.com", "{hash}");
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        authenticateWithJwt(account.getId().toString(), "ADMIN");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(
                new AccountPrincipal(account.getId(), "c@mail.com", Role.CUSTOMER));
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
    }
}
