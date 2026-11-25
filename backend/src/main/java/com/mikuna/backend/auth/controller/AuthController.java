package com.mikuna.backend.auth.controller;

import com.mikuna.backend.account.service.AccountRegistrationService;
import com.mikuna.backend.auth.dto.AccountResponse;
import com.mikuna.backend.auth.dto.LoginRequest;
import com.mikuna.backend.auth.dto.LoginResponse;
import com.mikuna.backend.auth.dto.RegisterRequest;
import com.mikuna.backend.auth.service.AuthService;
import com.mikuna.backend.common.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registro (público), inicio de sesión (público) y sesión propia (protegida). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountRegistrationService registrationService;
    private final AuthService authService;

    public AuthController(AccountRegistrationService registrationService, AuthService authService) {
        this.registrationService = registrationService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        var account = registrationService.registerPublic(request.name(), request.email(), request.password(),
                request.passwordConfirmation(), request.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    /**
     * La IP es la del socket. Detrás de un proxy o balanceador, configurar {@code server.forward-headers-strategy}
     * para que sea la del cliente real y no la del proxy (si no, todos compartirían el límite por IP).
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.email(), request.password(), http.getRemoteAddr());
    }

    /** Solo el titular del token; no acepta parámetros de identidad. */
    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return authService.currentAccount(principal.id());
    }
}
