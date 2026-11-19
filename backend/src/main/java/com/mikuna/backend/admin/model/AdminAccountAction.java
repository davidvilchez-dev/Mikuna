package com.mikuna.backend.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro inmutable de una activación o desactivación efectiva de una cuenta (FR-017): solo se inserta, junto con
 * el cambio, y no se modifica ni se borra. Ámbito y acción se guardan como texto plano (sin CHECK de Hibernate).
 */
@Entity
@Table(name = "acciones_admin_cuenta",
        indexes = @Index(name = "ix_acciones_cuenta_fecha", columnList = "account_id, created_at"))
public class AdminAccountAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false, length = 16)
    private String scope;

    @Column(nullable = false, updatable = false, length = 16)
    private String action;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAccountAction() {
        // JPA
    }

    public AdminAccountAction(UUID accountId, ActionScope scope, ActionType action, UUID adminId, Instant createdAt) {
        this.accountId = accountId;
        this.scope = scope.name();
        this.action = action.name();
        this.adminId = adminId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public ActionScope getScope() { return ActionScope.valueOf(scope); }
    public ActionType getAction() { return ActionType.valueOf(action); }
    public UUID getAdminId() { return adminId; }
    public Instant getCreatedAt() { return createdAt; }
}
