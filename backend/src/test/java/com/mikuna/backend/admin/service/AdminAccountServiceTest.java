package com.mikuna.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Account;
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
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.support.TestAccounts;
import com.mikuna.backend.support.TestRestaurants;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    private final UUID adminId = UUID.randomUUID();
    private AdminAccountQueryRepository accounts;
    private AdminAccountActionRepository actions;
    private RestaurantRepository restaurants;
    private AdminAccountQueryService queries;
    private AdminAccountService service;
    private final AccountDetail detail = new AccountDetail(null, null, null, java.util.List.of());

    @BeforeEach
    void setUp() {
        accounts = mock(AdminAccountQueryRepository.class);
        actions = mock(AdminAccountActionRepository.class);
        restaurants = mock(RestaurantRepository.class);
        queries = mock(AdminAccountQueryService.class);
        service = new AdminAccountService(accounts, actions, restaurants, queries, Clock.fixed(NOW, ZoneOffset.UTC),
                event -> { });
    }

    @Test
    void effectiveAccessChangeRecordsTheActionWithTheAdministrator() {
        Account customer = TestAccounts.account(Role.CUSTOMER, "c@mail.com", "hash");
        when(accounts.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(accounts.updateStatus(customer.getId(), "ENABLED", "DISABLED")).thenReturn(1);
        when(queries.userDetail(customer.getId())).thenReturn(detail);

        assertThat(service.setAccess(adminId, customer.getId(), false)).isSameAs(detail);

        ArgumentCaptor<AdminAccountAction> saved = ArgumentCaptor.forClass(AdminAccountAction.class);
        verify(actions).save(saved.capture());
        assertThat(saved.getValue().getAccountId()).isEqualTo(customer.getId());
        assertThat(saved.getValue().getScope()).isEqualTo(ActionScope.ACCESS);
        assertThat(saved.getValue().getAction()).isEqualTo(ActionType.DEACTIVATED);
        assertThat(saved.getValue().getAdminId()).isEqualTo(adminId);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void reactivatingRecordsAnActivation() {
        Account customer = TestAccounts.account(Role.CUSTOMER, "c@mail.com", "hash");
        when(accounts.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(accounts.updateStatus(customer.getId(), "DISABLED", "ENABLED")).thenReturn(1);

        service.setAccess(adminId, customer.getId(), true);

        ArgumentCaptor<AdminAccountAction> saved = ArgumentCaptor.forClass(AdminAccountAction.class);
        verify(actions).save(saved.capture());
        assertThat(saved.getValue().getAction()).isEqualTo(ActionType.ACTIVATED);
    }

    @Test
    void repeatingAChangeAlreadyAppliedDoesNotRecordAnything() {
        Account customer = TestAccounts.account(Role.CUSTOMER, "c@mail.com", "hash");
        when(accounts.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(accounts.updateStatus(any(), anyString(), anyString())).thenReturn(0);
        when(queries.userDetail(customer.getId())).thenReturn(detail);

        assertThat(service.setAccess(adminId, customer.getId(), false)).isSameAs(detail);

        verify(actions, never()).save(any());
    }

    @Test
    void unknownAccountIsNotFound() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setAccess(adminId, id, false)).isInstanceOf(AccountNotFoundException.class);
        verify(actions, never()).save(any());
    }

    @Test
    void administratorAccountsAreProtectedIncludingTheOwn() {
        Account other = TestAccounts.account(Role.ADMIN, "otro@mail.com", "hash");
        when(accounts.findById(other.getId())).thenReturn(Optional.of(other));
        when(accounts.findById(adminId)).thenReturn(Optional.of(TestAccounts.account(Role.ADMIN, "yo@mail.com", "h")));

        assertThatThrownBy(() -> service.setAccess(adminId, other.getId(), false))
                .isInstanceOf(AdminAccountProtectedException.class);
        assertThatThrownBy(() -> service.setAccess(adminId, adminId, false))
                .isInstanceOf(AdminAccountProtectedException.class);
        verify(accounts, never()).updateStatus(any(), anyString(), anyString());
        verify(actions, never()).save(any());
    }

    @Test
    void restaurantOperationChangesTheOwnerAccountWithOperationScope() {
        Account owner = TestAccounts.account(Role.RESTAURANT, "r@mail.com", "hash");
        Restaurant restaurant = TestRestaurants.restaurant(owner.getId(), RestaurantStatus.APPROVED);
        when(restaurants.findById(restaurant.getId())).thenReturn(Optional.of(restaurant));
        when(accounts.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(accounts.updateOperation(owner.getId(), false, "RESTAURANT")).thenReturn(1);
        when(queries.restaurantDetail(restaurant.getId())).thenReturn(detail);

        assertThat(service.setRestaurantOperation(adminId, restaurant.getId(), false)).isSameAs(detail);

        ArgumentCaptor<AdminAccountAction> saved = ArgumentCaptor.forClass(AdminAccountAction.class);
        verify(actions).save(saved.capture());
        assertThat(saved.getValue().getAccountId()).isEqualTo(owner.getId());
        assertThat(saved.getValue().getScope()).isEqualTo(ActionScope.OPERATION);
        assertThat(saved.getValue().getAction()).isEqualTo(ActionType.DEACTIVATED);
    }

    @Test
    void unknownRestaurantIsNotFound() {
        UUID id = UUID.randomUUID();
        when(restaurants.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setRestaurantOperation(adminId, id, true))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void courierOperationRequiresACourierAccount() {
        Account customer = TestAccounts.account(Role.CUSTOMER, "c@mail.com", "hash");
        when(accounts.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.setCourierOperation(adminId, customer.getId(), false))
                .isInstanceOf(AccountNotFoundException.class);
        verify(accounts, never()).updateOperation(any(), org.mockito.ArgumentMatchers.anyBoolean(), anyString());
    }

    @Test
    void courierOperationRecordsTheActivationWhenItChanges() {
        Account courier = TestAccounts.account(Role.COURIER, "rep@mail.com", "hash");
        when(accounts.findById(courier.getId())).thenReturn(Optional.of(courier));
        when(accounts.updateOperation(courier.getId(), true, "COURIER")).thenReturn(1);
        when(queries.courierDetail(courier.getId())).thenReturn(detail);

        assertThat(service.setCourierOperation(adminId, courier.getId(), true)).isSameAs(detail);

        ArgumentCaptor<AdminAccountAction> saved = ArgumentCaptor.forClass(AdminAccountAction.class);
        verify(actions).save(saved.capture());
        assertThat(saved.getValue().getAction()).isEqualTo(ActionType.ACTIVATED);
        assertThat(saved.getValue().getScope()).isEqualTo(ActionScope.OPERATION);
    }
}
