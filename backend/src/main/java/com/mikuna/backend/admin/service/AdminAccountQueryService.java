package com.mikuna.backend.admin.service;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.AccountStatus;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.admin.dto.AccountDetail;
import com.mikuna.backend.admin.dto.AccountSummary;
import com.mikuna.backend.admin.dto.ActionEntry;
import com.mikuna.backend.admin.model.ActionScope;
import com.mikuna.backend.admin.model.AdminAccountAction;
import com.mikuna.backend.admin.repository.AdminAccountActionRepository;
import com.mikuna.backend.admin.repository.AdminAccountQueryRepository;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.exception.AccountNotFoundException;
import com.mikuna.backend.delivery.model.CourierAvailability;
import com.mikuna.backend.delivery.repository.CourierAvailabilityRepository;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecturas del administrador sobre usuarios, restaurantes y repartidores (RF-32): listados paginados con búsqueda y
 * filtros, y detalle con el registro de acciones. Nunca expone credenciales (FR-019) ni cuentas de administrador.
 */
@Service
@Transactional(readOnly = true)
public class AdminAccountQueryService {

    /** Valores del filtro {@code operation} de restaurantes y repartidores. */
    public enum OperationFilter { ACTIVE, INACTIVE }

    private static final List<Boolean> ANY_OPERATION = List.of(true, false);

    private final AdminAccountQueryRepository queries;
    private final AdminAccountActionRepository actions;
    private final RestaurantRepository restaurants;
    private final CourierAvailabilityRepository availability;

    public AdminAccountQueryService(AdminAccountQueryRepository queries, AdminAccountActionRepository actions,
            RestaurantRepository restaurants, CourierAvailabilityRepository availability) {
        this.queries = queries;
        this.actions = actions;
        this.restaurants = restaurants;
        this.availability = availability;
    }

    public PageResponse<AccountSummary> listUsers(String role, String status, String q, Integer page, Integer size) {
        Pageable pageable = AdminPaging.pageable(page, size);
        Role roleFilter = AdminPaging.enumFilter("role", role, Role.class);
        if (roleFilter == Role.ADMIN) {
            throw new com.mikuna.backend.common.exception.RegistrationValidationException("role", "Valor no válido");
        }
        AccountStatus statusFilter = AdminPaging.enumFilter("status", status, AccountStatus.class);
        Collection<String> roles = roleFilter == null
                ? List.of(Role.CUSTOMER.name(), Role.RESTAURANT.name(), Role.COURIER.name())
                : List.of(roleFilter.name());
        Collection<String> statuses = statusFilter == null
                ? Arrays.stream(AccountStatus.values()).map(Enum::name).toList()
                : List.of(statusFilter.name());
        Page<Account> result = queries.searchUsers(roles, statuses, AdminPaging.likePattern(q), pageable);
        return PageResponse.from(result, AdminAccountQueryService::summary);
    }

    public PageResponse<AccountSummary> listRestaurants(String registration, String operation, String q,
            Integer page, Integer size) {
        Pageable pageable = AdminPaging.pageable(page, size);
        RestaurantStatus registrationFilter = AdminPaging.enumFilter("registration", registration,
                RestaurantStatus.class);
        Collection<String> registrations = registrationFilter == null
                ? Arrays.stream(RestaurantStatus.values()).map(Enum::name).toList()
                : List.of(registrationFilter.name());
        Page<Restaurant> result = queries.searchRestaurants(registrations, operations(operation),
                AdminPaging.likePattern(q), pageable);
        Map<UUID, Account> owners = accountsById(result.getContent().stream().map(Restaurant::getOwnerId).toList());
        return PageResponse.from(result, restaurant -> restaurantSummary(restaurant, owners.get(restaurant.getOwnerId())));
    }

    public PageResponse<AccountSummary> listCouriers(String operation, String q, Integer page, Integer size) {
        Pageable pageable = AdminPaging.pageable(page, size);
        Page<Account> result = queries.searchCouriers(operations(operation), AdminPaging.likePattern(q), pageable);
        return PageResponse.from(result, AdminAccountQueryService::summary);
    }

    public AccountDetail userDetail(UUID id) {
        Account account = queries.findById(id).filter(a -> a.getRole() != Role.ADMIN)
                .orElseThrow(AccountNotFoundException::new);
        return new AccountDetail(summary(account), restaurantInfo(account), courierInfo(account),
                entries(actions.findByAccountIdOrderByCreatedAtDescIdDesc(account.getId())));
    }

    public AccountDetail restaurantDetail(UUID restaurantId) {
        Restaurant restaurant = restaurants.findById(restaurantId).orElseThrow(AccountNotFoundException::new);
        Account owner = queries.findById(restaurant.getOwnerId()).orElseThrow(AccountNotFoundException::new);
        return new AccountDetail(restaurantSummary(restaurant, owner), restaurantInfo(restaurant), null,
                entries(operationActions(owner.getId())));
    }

    public AccountDetail courierDetail(UUID courierId) {
        Account courier = queries.findById(courierId).filter(a -> a.getRole() == Role.COURIER)
                .orElseThrow(AccountNotFoundException::new);
        return new AccountDetail(summary(courier), null, courierInfo(courier),
                entries(operationActions(courier.getId())));
    }

    // ---- ensamblado ----

    private static Collection<Boolean> operations(String operation) {
        OperationFilter filter = AdminPaging.enumFilter("operation", operation, OperationFilter.class);
        if (filter == null) {
            return ANY_OPERATION;
        }
        return List.of(filter == OperationFilter.ACTIVE);
    }

    private List<AdminAccountAction> operationActions(UUID accountId) {
        return actions.findByAccountIdAndScopeOrderByCreatedAtDescIdDesc(accountId, ActionScope.OPERATION.name());
    }

    private Map<UUID, Account> accountsById(Collection<UUID> ids) {
        return queries.findAllById(ids).stream().collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private List<ActionEntry> entries(List<AdminAccountAction> rows) {
        Set<UUID> adminIds = rows.stream().map(AdminAccountAction::getAdminId).collect(Collectors.toSet());
        Map<UUID, Account> admins = accountsById(adminIds);
        return rows.stream().map(row -> new ActionEntry(row.getAction().name(), row.getScope().name(),
                row.getCreatedAt(), row.getAdminId(),
                admins.containsKey(row.getAdminId()) ? admins.get(row.getAdminId()).getName() : null)).toList();
    }

    /** Resumen de una cuenta de acceso; la operación solo existe para restaurantes y repartidores. */
    static AccountSummary summary(Account account) {
        boolean operates = account.getRole() == Role.RESTAURANT || account.getRole() == Role.COURIER;
        return new AccountSummary(account.getId(), account.getId(), account.getName(), account.getEmail(),
                account.getRole().name(), account.isEnabled(), operates ? account.isOperationEnabled() : null,
                null, account.getCreatedAt());
    }

    static AccountSummary restaurantSummary(Restaurant restaurant, Account owner) {
        return new AccountSummary(restaurant.getId(), restaurant.getOwnerId(), restaurant.getName(),
                owner == null ? null : owner.getEmail(), Role.RESTAURANT.name(),
                owner != null && owner.isEnabled(), owner == null ? null : owner.isOperationEnabled(),
                restaurant.getStatus().name(), restaurant.getSubmittedAt());
    }

    private AccountDetail.RestaurantInfo restaurantInfo(Account account) {
        if (account.getRole() != Role.RESTAURANT) {
            return null;
        }
        return restaurants.findByOwnerId(account.getId()).map(this::restaurantInfo).orElse(null);
    }

    private AccountDetail.RestaurantInfo restaurantInfo(Restaurant restaurant) {
        return new AccountDetail.RestaurantInfo(restaurant.getCategory(), restaurant.getDescription(),
                restaurant.getAddress(), restaurant.getZone(), restaurant.getPhone(), restaurant.isAvailable());
    }

    private AccountDetail.CourierInfo courierInfo(Account account) {
        if (account.getRole() != Role.COURIER) {
            return null;
        }
        CourierAvailability row = availability.findById(account.getId()).orElse(null);
        return new AccountDetail.CourierInfo(row != null && row.isAvailable(), row == null ? null : row.getFreeSince());
    }
}
