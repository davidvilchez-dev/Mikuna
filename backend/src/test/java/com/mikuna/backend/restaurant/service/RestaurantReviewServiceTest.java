package com.mikuna.backend.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.exception.RestaurantConflictException;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.restaurant.dto.AdminRestaurantResponse;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.support.TestAccounts;
import com.mikuna.backend.support.TestRestaurants;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** RF-06: revisión administrativa de solicitudes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T20:00:00Z");

    @Mock
    private RestaurantRepository restaurants;

    @Mock
    private AccountRepository accounts;

    private final UUID admin = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private RestaurantReviewService service;
    private Account ownerAccount;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RestaurantReviewService(restaurants, accounts, new OrderEligibility(clock, "America/Lima"), clock,
                event -> { });
        ownerAccount = TestAccounts.account(Role.RESTAURANT, "local@mail.com", "{hash}");
        when(accounts.findById(owner)).thenReturn(Optional.of(ownerAccount));
    }

    @Test
    void listaPendientesPorDefectoConDatosDelTitular() {
        Restaurant pending = TestRestaurants.restaurant(owner, RestaurantStatus.PENDING);
        when(restaurants.findByStatusOrderBySubmittedAtAsc("PENDING")).thenReturn(List.of(pending));

        List<AdminRestaurantResponse> result = service.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ownerEmail()).isEqualTo("local@mail.com");
        assertThat(result.get(0).ownerName()).isEqualTo(ownerAccount.getName());
        assertThat(result.get(0).acceptingOrders()).isFalse();
    }

    @Test
    void listaPorEstadoIndicadoYVaciaSiNoHay() {
        when(restaurants.findByStatusOrderBySubmittedAtAsc("REJECTED")).thenReturn(List.of());
        assertThat(service.list(" REJECTED ")).isEmpty();
        assertThat(service.list("  ")).isEmpty();
    }

    @Test
    void estadoDesconocidoSeRechaza() {
        assertThatThrownBy(() -> service.list("TODOS")).isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void sinCuentaDelTitularLaListaSigueFuncionando() {
        UUID ghost = UUID.randomUUID();
        when(restaurants.findByStatusOrderBySubmittedAtAsc("PENDING"))
                .thenReturn(List.of(TestRestaurants.restaurant(ghost, RestaurantStatus.PENDING)));
        when(accounts.findById(ghost)).thenReturn(Optional.empty());
        assertThat(service.list("PENDING").get(0).ownerEmail()).isEmpty();
    }

    @Test
    void aprobarUsaLaResolucionCondicionalDesdePendiente() {
        Restaurant approved = TestRestaurants.restaurant(owner, RestaurantStatus.APPROVED);
        when(restaurants.resolve(approved.getId(), "PENDING", "APPROVED", null, admin, NOW)).thenReturn(1);
        when(restaurants.findById(approved.getId())).thenReturn(Optional.of(approved));

        AdminRestaurantResponse response = service.approve(admin, approved.getId());

        assertThat(response.status()).isEqualTo(RestaurantStatus.APPROVED);
        assertThat(response.available()).isFalse();
    }

    @Test
    void rechazarGuardaElMotivoSinEspacios() {
        Restaurant rejected = TestRestaurants.restaurant(owner, RestaurantStatus.REJECTED);
        when(restaurants.resolve(rejected.getId(), "PENDING", "REJECTED", "Faltan datos", admin, NOW)).thenReturn(1);
        when(restaurants.findById(rejected.getId())).thenReturn(Optional.of(rejected));

        assertThat(service.reject(admin, rejected.getId(), "  Faltan datos ").status()).isEqualTo(RestaurantStatus.REJECTED);
    }

    @Test
    void rechazarExigeMotivoDeHasta300Caracteres() {
        UUID id = UUID.randomUUID();
        for (String reason : new String[] {null, "", "   ", "x".repeat(301)}) {
            assertThatThrownBy(() -> service.reject(admin, id, reason)).isInstanceOf(RegistrationValidationException.class);
        }
        verify(restaurants, never()).resolve(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolverUnaSolicitudYaResueltaEsConflicto() {
        UUID id = UUID.randomUUID();
        when(restaurants.resolve(id, "PENDING", "APPROVED", null, admin, NOW)).thenReturn(0);
        when(restaurants.existsById(id)).thenReturn(true);
        assertThatThrownBy(() -> service.approve(admin, id)).isInstanceOf(RestaurantConflictException.class);
    }

    @Test
    void resolverUnaSolicitudInexistenteEsNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(restaurants.resolve(id, "PENDING", "REJECTED", "Motivo", admin, NOW)).thenReturn(0);
        when(restaurants.existsById(id)).thenReturn(false);
        assertThatThrownBy(() -> service.reject(admin, id, "Motivo")).isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void siDesapareceTrasResolverEsNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(restaurants.resolve(id, "PENDING", "APPROVED", null, admin, NOW)).thenReturn(1);
        when(restaurants.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approve(admin, id)).isInstanceOf(RestaurantNotFoundException.class);
        verify(restaurants).resolve(eq(id), eq("PENDING"), eq("APPROVED"), isNull(), eq(admin), eq(NOW));
    }
}
