package com.mikuna.backend.auth.service;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.account.service.EmailNormalizer;
import com.mikuna.backend.auth.dto.AccountResponse;
import com.mikuna.backend.auth.dto.LoginResponse;
import com.mikuna.backend.common.exception.AccountDisabledException;
import com.mikuna.backend.common.exception.InvalidCredentialsException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inicio de sesión y consulta de la sesión propia (RF-01). */
@Service
public class AuthService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptLimiter attempts;
    /** Hash de referencia para igualar el tiempo de respuesta cuando el correo no existe. */
    private final String dummyHash;

    public AuthService(AccountRepository accounts, PasswordEncoder passwordEncoder, TokenService tokenService,
            LoginAttemptLimiter attempts) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.attempts = attempts;
        this.dummyHash = passwordEncoder.encode("mikuna-dummy-password-for-timing");
    }

    /**
     * {@code clientIp} identifica el origen para el límite de intentos; con la cuenta o la IP bloqueadas se responde
     * 429 sin comprobar la contraseña. Un correo inexistente cuenta como fallo igual que uno existente.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password, String clientIp) {
        String normalized = EmailNormalizer.normalize(email);
        attempts.check(normalized, clientIp);
        Optional<Account> found = accounts.findByEmail(normalized);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            attempts.recordFailure(normalized, clientIp);
            throw new InvalidCredentialsException();
        }
        Account account = found.get();
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            attempts.recordFailure(normalized, clientIp);
            throw new InvalidCredentialsException();
        }
        attempts.recordSuccess(normalized);
        if (!account.isEnabled()) {
            throw new AccountDisabledException();
        }
        TokenService.IssuedToken token = tokenService.issue(account);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), AccountResponse.from(account));
    }

    /** La cuenta ya fue verificada (existe y está habilitada) por AccountStatusFilter. */
    @Transactional(readOnly = true)
    public AccountResponse currentAccount(UUID accountId) {
        return accounts.findById(accountId).map(AccountResponse::from).orElseThrow(InvalidCredentialsException::new);
    }
}
