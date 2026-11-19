package com.mikuna.backend.admin.repository;

import com.mikuna.backend.admin.model.AdminAccountAction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountActionRepository extends JpaRepository<AdminAccountAction, UUID> {

    /** Más reciente primero; el identificador desempata. */
    List<AdminAccountAction> findByAccountIdOrderByCreatedAtDescIdDesc(UUID accountId);

    List<AdminAccountAction> findByAccountIdAndScopeOrderByCreatedAtDescIdDesc(UUID accountId, String scope);
}
