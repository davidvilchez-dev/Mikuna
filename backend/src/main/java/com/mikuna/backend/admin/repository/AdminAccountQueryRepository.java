package com.mikuna.backend.admin.repository;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.restaurant.model.Restaurant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas de solo lectura del administrador sobre cuentas y restaurantes (RF-32). Orden fijo por nombre e
 * identificador, de modo que las páginas no repiten ni omiten filas. El patrón {@code q} llega en minúsculas y con
 * {@code !} como carácter de escape (ver {@code AdminPaging.likePattern}); los administradores nunca se listan.
 */
public interface AdminAccountQueryRepository extends JpaRepository<Account, UUID> {

    @Query("""
            select a from Account a
            where a.role in :roles and a.status in :statuses
              and (lower(a.name) like :q escape '!' or lower(a.email) like :q escape '!')
            order by a.name asc, a.id asc""")
    Page<Account> searchUsers(@Param("roles") Collection<String> roles,
            @Param("statuses") Collection<String> statuses, @Param("q") String q, Pageable pageable);

    @Query("""
            select a from Account a
            where a.role = 'COURIER' and a.operationEnabled in :operations
              and (lower(a.name) like :q escape '!' or lower(a.email) like :q escape '!')
            order by a.name asc, a.id asc""")
    Page<Account> searchCouriers(@Param("operations") Collection<Boolean> operations, @Param("q") String q,
            Pageable pageable);

    @Query("""
            select r from Restaurant r
            where r.status in :registrations
              and exists (select 1 from Account a where a.id = r.ownerId and a.operationEnabled in :operations
                          and (lower(r.name) like :q escape '!' or lower(a.email) like :q escape '!'))
            order by r.name asc, r.id asc""")
    Page<Restaurant> searchRestaurants(@Param("registrations") Collection<String> registrations,
            @Param("operations") Collection<Boolean> operations, @Param("q") String q, Pageable pageable);

    /** Actualización condicional del acceso (constitución IV): solo prospera si la cuenta sigue en el estado de origen. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Account a set a.status = :to where a.id = :id and a.status = :from and a.role <> 'ADMIN'")
    int updateStatus(@Param("id") UUID id, @Param("from") String from, @Param("to") String to);

    /** Actualización condicional de la operación: solo prospera si el valor cambia y el rol es el esperado. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Account a set a.operationEnabled = :to where a.id = :id and a.operationEnabled <> :to "
            + "and a.role = :role")
    int updateOperation(@Param("id") UUID id, @Param("to") boolean to, @Param("role") String role);
}
