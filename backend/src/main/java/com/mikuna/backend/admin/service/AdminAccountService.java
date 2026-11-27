package com.mikuna.backend.admin.service;

import com.mikuna.backend.common.cache.PublicCatalogChanged;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.AccountStatus;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.admin.dto.AccountDetail;
import com.mikuna.backend.admin.model.ActionScope;
import com.mikuna.backend.admin.model.ActionType;
import com.mikuna.backend.admin.model.AdminAccountAction;
import com.mikuna.backend.admin.repository.AdminAccountActionRepository;
import com.mikuna.backend.admin.repository.AdminAccountQueryRepository;
import com.mikuna.backend.common.exception.AccountNotFoundException;
import com.mikuna.backend.common.exception.AdminAccountProtectedException;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activación y desactivación de cuentas (RF-32). Cada cambio es una actualización condicional en PostgreSQL
 * (constitución IV): solo el cambio efectivo inserta el registro de la acción, en la misma transacción; repetir un
 * cambio ya aplicado responde con el estado actual y no registra nada (FR-010). Las cuentas no se eliminan.
 */
@Service
@Transactional
public class AdminAccountService {

    private final AdminAccountQueryRepository accounts;
    private final AdminAccountActionRepository actions;
    private final RestaurantRepository restaurants;
    private final AdminAccountQueryService queries;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public AdminAccountService(AdminAccountQueryRepository accounts, AdminAccountActionRepository actions,
            RestaurantRepository restaurants, AdminAccountQueryService queries, Clock clock,
            ApplicationEventPublisher events) {
        this.events = events;
        this.accounts = accounts;
        this.actions = actions;
        this.restaurants = restaurants;
        this.queries = queries;
        this.clock = clock;
    }

    /** Bloquea o restablece el inicio de sesión de una cuenta de usuario (ámbito ACCESS). */
    public AccountDetail setAccess(UUID adminId, UUID accountId, boolean enabled) {
        Account account = accounts.findById(accountId).orElseThrow(AccountNotFoundException::new);
        if (account.getRole() == Role.ADMIN) {
            throw new AdminAccountProtectedException();
        }
        AccountStatus from = enabled ? AccountStatus.DISABLED : AccountStatus.ENABLED;
        AccountStatus to = enabled ? AccountStatus.ENABLED : AccountStatus.DISABLED;
        if (accounts.updateStatus(accountId, from.name(), to.name()) == 1) {
            record(accountId, ActionScope.ACCESS, enabled, adminId);
            restaurantVisibilityChanged(account.getRole());
        }
        return queries.userDetail(accountId);
    }

    /** Activa o desactiva la operación de un restaurante: la cuenta que cambia es la de su titular. */
    public AccountDetail setRestaurantOperation(UUID adminId, UUID restaurantId, boolean active) {
        Restaurant restaurant = restaurants.findById(restaurantId).orElseThrow(AccountNotFoundException::new);
        changeOperation(adminId, restaurant.getOwnerId(), Role.RESTAURANT, active);
        return queries.restaurantDetail(restaurantId);
    }

    /** Activa o desactiva la operación de un repartidor (candidato a entregas nuevas). */
    public AccountDetail setCourierOperation(UUID adminId, UUID courierId, boolean active) {
        changeOperation(adminId, courierId, Role.COURIER, active);
        return queries.courierDetail(courierId);
    }

    private void changeOperation(UUID adminId, UUID accountId, Role role, boolean active) {
        Account account = accounts.findById(accountId).filter(a -> a.getRole() == role)
                .orElseThrow(AccountNotFoundException::new);
        if (accounts.updateOperation(account.getId(), active, role.name()) == 1) {
            record(account.getId(), ActionScope.OPERATION, active, adminId);
            restaurantVisibilityChanged(role);
        }
    }

    /** Habilitar o no la cuenta de un titular cambia qué restaurantes ve el público (spec 015). */
    private void restaurantVisibilityChanged(Role role) {
        if (role == Role.RESTAURANT) {
            events.publishEvent(PublicCatalogChanged.restaurants());
        }
    }

    private void record(UUID accountId, ActionScope scope, boolean activated, UUID adminId) {
        actions.save(new AdminAccountAction(accountId, scope, activated ? ActionType.ACTIVATED : ActionType.DEACTIVATED,
                adminId, Instant.now(clock)));
    }
}
