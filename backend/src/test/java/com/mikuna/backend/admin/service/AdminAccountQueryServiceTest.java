package com.mikuna.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.admin.dto.AccountDetail;
import com.mikuna.backend.admin.dto.AccountSummary;
import com.mikuna.backend.admin.model.ActionScope;
import com.mikuna.backend.admin.model.ActionType;
import com.mikuna.backend.admin.model.AdminAccountAction;
import com.mikuna.backend.admin.repository.AdminAccountActionRepository;
import com.mikuna.backend.admin.repository.AdminAccountQueryRepository;
import com.mikuna.backend.common.exception.AccountNotFoundException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.delivery.repository.CourierAvailabilityRepository;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.support.TestAccounts;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class AdminAccountQueryServiceTest {

    private AdminAccountQueryRepository queries;
    private AdminAccountActionRepository actions;
    private AdminAccountQueryService service;

    @BeforeEach
    void setUp() {
        queries = mock(AdminAccountQueryRepository.class);
        actions = mock(AdminAccountActionRepository.class);
        service = new AdminAccountQueryService(queries, actions, mock(RestaurantRepository.class),
                mock(CourierAvailabilityRepository.class));
    }

    @Test
    void invalidFiltersAreRejectedNamingTheField() {
        assertThatThrownBy(() -> service.listUsers("NOPE", null, null, null, null))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("role");
        assertThatThrownBy(() -> service.listUsers("ADMIN", null, null, null, null))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.listUsers(null, "NOPE", null, null, null))
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("status");
        assertThatThrownBy(() -> service.listRestaurants("NOPE", null, null, null, null))
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("registration");
        assertThatThrownBy(() -> service.listCouriers("NOPE", null, null, null))
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("operation");
        assertThatThrownBy(() -> service.listUsers(null, null, null, -1, null))
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("page");
    }

    @Test
    void operationIsOnlyReportedForRestaurantsAndCouriers() {
        Account customer = TestAccounts.account(Role.CUSTOMER, "c@mail.com", "hash");
        Account courier = TestAccounts.account(Role.COURIER, "r@mail.com", "hash");
        when(queries.searchUsers(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(customer, courier)));

        List<AccountSummary> items = service.listUsers(null, null, null, null, null).items();

        assertThat(items.get(0).operationEnabled()).isNull();
        assertThat(items.get(1).operationEnabled()).isTrue();
        assertThat(items.get(0).accessEnabled()).isTrue();
    }

    @Test
    void administratorsHaveNoDetail() {
        Account admin = TestAccounts.account(Role.ADMIN, "a@mail.com", "hash");
        when(queries.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.userDetail(admin.getId())).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void courierDetailRejectsOtherRoles() {
        Account customer = TestAccounts.account(Role.CUSTOMER, "c@mail.com", "hash");
        when(queries.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.courierDetail(customer.getId())).isInstanceOf(AccountNotFoundException.class);
        verify(actions, never()).findByAccountIdAndScopeOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void userDetailListsAllActionsWithTheAdministratorName() {
        Account customer = TestAccounts.account(Role.CUSTOMER, "c@mail.com", "hash");
        Account admin = TestAccounts.account(Role.ADMIN, "a@mail.com", "hash");
        when(queries.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(queries.findAllById(any())).thenReturn(List.of(admin));
        when(actions.findByAccountIdOrderByCreatedAtDescIdDesc(customer.getId())).thenReturn(List.of(
                new AdminAccountAction(customer.getId(), ActionScope.ACCESS, ActionType.ACTIVATED, admin.getId(),
                        Instant.parse("2026-11-18T12:00:00Z")),
                new AdminAccountAction(customer.getId(), ActionScope.ACCESS, ActionType.DEACTIVATED, admin.getId(),
                        Instant.parse("2026-11-18T11:00:00Z"))));

        AccountDetail detail = service.userDetail(customer.getId());

        assertThat(detail.actions()).hasSize(2);
        assertThat(detail.actions().get(0).action()).isEqualTo("ACTIVATED");
        assertThat(detail.actions().get(0).adminName()).isEqualTo(admin.getName());
        assertThat(detail.restaurant()).isNull();
        assertThat(detail.courier()).isNull();
    }

    @Test
    void courierDetailShowsOnlyOperationActions() {
        Account courier = TestAccounts.account(Role.COURIER, "r@mail.com", "hash");
        when(queries.findById(courier.getId())).thenReturn(Optional.of(courier));
        when(actions.findByAccountIdAndScopeOrderByCreatedAtDescIdDesc(courier.getId(), "OPERATION"))
                .thenReturn(List.of());

        AccountDetail detail = service.courierDetail(courier.getId());

        assertThat(detail.actions()).isEmpty();
        assertThat(detail.courier().available()).isFalse();
        verify(actions, never()).findByAccountIdOrderByCreatedAtDescIdDesc(UUID.randomUUID());
    }
}
