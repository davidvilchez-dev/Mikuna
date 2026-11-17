package com.mikuna.backend.account.repository;

import com.mikuna.backend.account.model.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** El correo debe llegar ya normalizado (ver EmailNormalizer). */
    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);
}
