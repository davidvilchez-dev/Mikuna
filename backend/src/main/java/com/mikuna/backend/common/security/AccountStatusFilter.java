package com.mikuna.backend.common.security;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.repository.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * RF-04 / RNF-07: tras validar el JWT, recarga la cuenta en cada acceso protegido. Una cuenta
 * inexistente responde 401, una inhabilitada 403; el rol se toma de la base de datos, nunca del token.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final AccountRepository accounts;

    public AccountStatusFilter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<Account> account = parseId(jwtAuthentication.getToken().getSubject()).flatMap(accounts::findById);
        if (account.isEmpty()) {
            SecurityContextHolder.clearContext();
            ProblemDetailSecurityHandler.writeUnauthorized(response);
            return;
        }
        if (!account.get().isEnabled()) {
            SecurityContextHolder.clearContext();
            ProblemDetailSecurityHandler.writeForbidden(response);
            return;
        }

        Account current = account.get();
        AccountPrincipal principal = new AccountPrincipal(current.getId(), current.getEmail(), current.getRole());
        var authenticated = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + current.getRole().name())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private static Optional<UUID> parseId(String subject) {
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Optional.empty();
        }
    }
}
