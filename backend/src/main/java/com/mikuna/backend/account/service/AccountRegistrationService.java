package com.mikuna.backend.account.service;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.common.exception.EmailAlreadyRegisteredException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de alta de cuentas (RF-01). Revalida todo lo recibido; la unicidad del correo la garantiza
 * la restricción UNIQUE de PostgreSQL, también ante registros concurrentes.
 */
@Service
public class AccountRegistrationService {

    static final int NAME_MAX = 100;
    static final int EMAIL_MAX = 254;
    static final int PASSWORD_MIN = 12;
    static final int PASSWORD_MAX = 128;
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AccountRegistrationService(AccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    /** Registro público: solo cliente, restaurante o repartidor. */
    @Transactional
    public Account registerPublic(String name, String email, String password, String passwordConfirmation,
            Role role) {
        if (role == null || role == Role.ADMIN) {
            throw new RegistrationValidationException("role", "Elige un tipo de cuenta válido");
        }
        return create(name, email, password, passwordConfirmation, role);
    }

    /** Alta administrativa: solo la usa el procedimiento local AdminAccountInitializer. */
    @Transactional
    public Account createAdmin(String name, String email, String password) {
        return create(name, email, password, password, Role.ADMIN);
    }

    private Account create(String name, String email, String password, String passwordConfirmation, Role role) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > NAME_MAX) {
            throw new RegistrationValidationException("name", "El nombre es obligatorio y admite hasta 100 caracteres");
        }
        String normalizedEmail = EmailNormalizer.normalize(email);
        if (normalizedEmail == null || normalizedEmail.length() > EMAIL_MAX
                || !EMAIL_FORMAT.matcher(normalizedEmail).matches()) {
            throw new RegistrationValidationException("email", "Ingresa un correo válido de hasta 254 caracteres");
        }
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new RegistrationValidationException("password", "La contraseña debe tener entre 12 y 128 caracteres");
        }
        if (!password.equals(passwordConfirmation)) {
            throw new RegistrationValidationException("passwordConfirmation", "Las contraseñas no coinciden");
        }
        if (accounts.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            return accounts.saveAndFlush(
                    Account.create(cleanName, normalizedEmail, passwordEncoder.encode(password), role));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            throw new EmailAlreadyRegisteredException();
        }
    }
}
