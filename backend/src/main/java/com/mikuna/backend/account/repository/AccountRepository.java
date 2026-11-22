package com.mikuna.backend.account.repository;

import com.mikuna.backend.account.model.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** El correo debe llegar ya normalizado (ver EmailNormalizer). */
    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Bloquea la fila de la cuenta hasta el fin de la transacción: serializa las altas de restaurante de un mismo titular. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") UUID id);
}
