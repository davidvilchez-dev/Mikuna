package com.mikuna.backend.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuenta de Mikuna: un correo normalizado identifica una única cuenta con un único rol.
 * Rol y estado se persisten como texto plano: con @Enumerated (o conversores) Hibernate 7 genera
 * restricciones CHECK que ddl-auto=update no amplía.
 */
@Entity
@Table(name = "cuentas", uniqueConstraints = @UniqueConstraint(name = "uk_cuentas_email", columnNames = "email"))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    /**
     * Habilitación para operar (spec 011): restaurante que recibe pedidos o repartidor candidato a entregas.
     * Es independiente de {@code status} (acceso): quien no opera conserva el inicio de sesión y su historial.
     */
    @Column(name = "operation_enabled", nullable = false, columnDefinition = "boolean not null default true")
    private boolean operationEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // JPA
    }

    private Account(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role.name();
        this.status = AccountStatus.ENABLED.name();
    }

    /** Crea una cuenta habilitada. */
    public static Account create(String name, String email, String passwordHash, Role role) {
        return new Account(name, email, passwordHash, role);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isEnabled() {
        return AccountStatus.ENABLED.name().equals(status);
    }

    public boolean isOperationEnabled() {
        return operationEnabled;
    }

    /** Puede iniciar sesión y, además, operar (recibir pedidos o ser candidato a entregas). */
    public boolean canOperate() {
        return isEnabled() && operationEnabled;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return Role.valueOf(role); }
    public AccountStatus getStatus() { return AccountStatus.valueOf(status); }
    public Instant getCreatedAt() { return createdAt; }
}
