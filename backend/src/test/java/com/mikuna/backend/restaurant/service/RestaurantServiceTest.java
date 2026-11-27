package com.mikuna.backend.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.exception.RestaurantConflictException;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.common.exception.RestaurantStateException;
import com.mikuna.backend.restaurant.dto.AvailabilityRequest;
import com.mikuna.backend.restaurant.dto.OpeningHourDto;
import com.mikuna.backend.restaurant.dto.RestaurantRequest;
import com.mikuna.backend.restaurant.dto.RestaurantResponse;
import com.mikuna.backend.restaurant.dto.ScheduleRequest;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.support.TestAccounts;
import com.mikuna.backend.support.TestRestaurants;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** RF-05 y RF-07: reglas de la solicitud, los datos, el horario y la disponibilidad del restaurante propio. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantServiceTest {

    /** Lunes 2026-11-16 15:00 en Lima. */
    private static final Instant NOW = Instant.parse("2026-11-16T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private RestaurantRepository restaurants;

    @Mock
    private AccountRepository accounts;

    private final UUID owner = UUID.randomUUID();
    private RestaurantService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantService(restaurants, accounts, new OrderEligibility(CLOCK, "America/Lima"), CLOCK,
                event -> { });
        Account account = TestAccounts.account(Role.RESTAURANT, "local@mail.com", "{hash}");
        when(accounts.findById(owner)).thenReturn(Optional.of(account));
        when(restaurants.saveAndFlush(any(Restaurant.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static RestaurantRequest request(String name, String category, String description, String address,
            String zone, String phone) {
        return new RestaurantRequest(name, category, description, address, zone, phone);
    }

    private static RestaurantRequest valid() {
        return request("  La Picantería ", "Criolla", "Comida casera", "Jr. Lima 123", "Ayacucho", "+51 966 123 456");
    }

    // ---- RF-05: solicitud ----

    @Test
    void crearSolicitudValidaQuedaPendienteYNoDisponible() {
        RestaurantResponse response = service.create(owner, valid());

        ArgumentCaptor<Restaurant> saved = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurants).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(owner);
        assertThat(saved.getValue().getName()).isEqualTo("La Picantería");
        assertThat(saved.getValue().getStatus()).isEqualTo(RestaurantStatus.PENDING);
        assertThat(saved.getValue().isAvailable()).isFalse();
        assertThat(saved.getValue().getSubmittedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(RestaurantStatus.PENDING);
        assertThat(response.acceptingOrders()).isFalse();
    }

    @Test
    void descripcionVaciaSeGuardaComoNula() {
        service.create(owner, request("La Picantería", "Criolla", "   ", "Jr. Lima 123", "Ayacucho", "966123456"));
        ArgumentCaptor<Restaurant> saved = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurants).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDescription()).isNull();
    }

    static Stream<Arguments> invalidRequests() {
        String long300 = "x".repeat(301);
        return Stream.of(
                Arguments.of("name", request(" ", "Criolla", null, "Jr. Lima 123", "Ayacucho", "966123456")),
                Arguments.of("name", request("A", "Criolla", null, "Jr. Lima 123", "Ayacucho", "966123456")),
                Arguments.of("name", request("N".repeat(101), "Criolla", null, "Jr. Lima 123", "Ayacucho", "966123456")),
                Arguments.of("name", request(null, "Criolla", null, "Jr. Lima 123", "Ayacucho", "966123456")),
                Arguments.of("category", request("Local", "C", null, "Jr. Lima 123", "Ayacucho", "966123456")),
                Arguments.of("category", request("Local", "C".repeat(51), null, "Jr. Lima 123", "Ayacucho", "966123456")),
                Arguments.of("description", request("Local", "Criolla", long300, "Jr. Lima 123", "Ayacucho", "966123456")),
                Arguments.of("address", request("Local", "Criolla", null, "Jr.", "Ayacucho", "966123456")),
                Arguments.of("address", request("Local", "Criolla", null, "J".repeat(201), "Ayacucho", "966123456")),
                Arguments.of("zone", request("Local", "Criolla", null, "Jr. Lima 123", "A", "966123456")),
                Arguments.of("zone", request("Local", "Criolla", null, "Jr. Lima 123", "Z".repeat(101), "966123456")),
                Arguments.of("phone", request("Local", "Criolla", null, "Jr. Lima 123", "Ayacucho", "123456")),
                Arguments.of("phone", request("Local", "Criolla", null, "Jr. Lima 123", "Ayacucho", "1234567890123456")),
                Arguments.of("phone", request("Local", "Criolla", null, "Jr. Lima 123", "Ayacucho", "96612a456")),
                Arguments.of("phone", request("Local", "Criolla", null, "Jr. Lima 123", "Ayacucho", null)));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void datosInvalidosSeRechazanSinGuardar(String field, RestaurantRequest request) {
        assertThatThrownBy(() -> service.create(owner, request))
                .isInstanceOfSatisfying(RegistrationValidationException.class, ex -> assertThat(ex.getField()).isEqualTo(field));
        verify(restaurants, never()).saveAndFlush(any(Restaurant.class));
    }

    @Test
    void solicitudNulaSeRechaza() {
        assertThatThrownBy(() -> service.create(owner, null)).isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void segundaSolicitudDeLaMismaCuentaEsConflicto() {
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(TestRestaurants.restaurant(owner, RestaurantStatus.PENDING)));
        assertThatThrownBy(() -> service.create(owner, valid())).isInstanceOf(RestaurantConflictException.class);
        verify(restaurants, never()).saveAndFlush(any(Restaurant.class));
    }

    @Test
    void duplicadoConcurrenteSeTraduceAConflicto() {
        when(restaurants.saveAndFlush(any(Restaurant.class))).thenThrow(new DataIntegrityViolationException("uk_restaurantes_owner"));
        assertThatThrownBy(() -> service.create(owner, valid())).isInstanceOf(RestaurantConflictException.class);
    }

    @Test
    void consultarSinSolicitudEsNoEncontrado() {
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMine(owner)).isInstanceOf(RestaurantNotFoundException.class);
        assertThatThrownBy(() -> service.updateMine(owner, valid())).isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void consultarDevuelveElRestauranteConSuEstado() {
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(TestRestaurants.restaurant(owner, RestaurantStatus.PENDING)));
        assertThat(service.getMine(owner).status()).isEqualTo(RestaurantStatus.PENDING);
    }

    @Test
    void editarDatosConservaElEstado() {
        Restaurant approved = TestRestaurants.restaurant(owner, RestaurantStatus.APPROVED);
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(approved));

        RestaurantResponse response = service.updateMine(owner, valid());

        assertThat(response.name()).isEqualTo("La Picantería");
        assertThat(response.phone()).isEqualTo("+51 966 123 456");
        assertThat(response.status()).isEqualTo(RestaurantStatus.APPROVED);
    }

    @Test
    void edicionConcurrenteSeTraduceAConflicto() {
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(TestRestaurants.restaurant(owner, RestaurantStatus.PENDING)));
        when(restaurants.saveAndFlush(any(Restaurant.class))).thenThrow(new ObjectOptimisticLockingFailureException(Restaurant.class, "id"));
        assertThatThrownBy(() -> service.updateMine(owner, valid())).isInstanceOf(RestaurantConflictException.class);
    }

    @Test
    void reenviarSoloEsPosibleDesdeRechazado() {
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(TestRestaurants.restaurant(owner, RestaurantStatus.PENDING)));
        assertThatThrownBy(() -> service.resubmit(owner)).isInstanceOf(RestaurantStateException.class);
        verify(restaurants, never()).resubmit(any(), any(), any());
    }

    @Test
    void reenviarUnRechazadoUsaLaActualizacionCondicional() {
        Restaurant rejected = TestRestaurants.restaurant(owner, RestaurantStatus.REJECTED);
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(rejected));
        when(restaurants.resubmit(rejected.getId(), owner, NOW)).thenReturn(1);

        service.resubmit(owner);

        verify(restaurants).resubmit(rejected.getId(), owner, NOW);
    }

    @Test
    void reenvioPerdidoPorCarreraEsConflicto() {
        Restaurant rejected = TestRestaurants.restaurant(owner, RestaurantStatus.REJECTED);
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(rejected));
        when(restaurants.resubmit(eq(rejected.getId()), eq(owner), any())).thenReturn(0);
        assertThatThrownBy(() -> service.resubmit(owner)).isInstanceOf(RestaurantConflictException.class);
    }

    // ---- RF-07: horario y disponibilidad ----

    private static ScheduleRequest week(String mondayOpens, String mondayCloses) {
        List<OpeningHourDto> days = new ArrayList<>();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
            days.add(day.equals("MONDAY") ? new OpeningHourDto(day, mondayOpens, mondayCloses)
                    : new OpeningHourDto(day, null, null));
        }
        return new ScheduleRequest(days);
    }

    private void approvedRestaurant(boolean available) {
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(TestRestaurants.approvedOpenOnMonday(owner, available)));
    }

    @Test
    void guardaElHorarioSemanalCompleto() {
        approvedRestaurant(false);
        RestaurantResponse response = service.updateSchedule(owner, week("08:30", "22:00"));
        assertThat(response.schedule()).hasSize(7);
        assertThat(response.schedule().get(0)).isEqualTo(new OpeningHourDto("MONDAY", "08:30", "22:00"));
        assertThat(response.schedule().get(1)).isEqualTo(new OpeningHourDto("TUESDAY", null, null));
    }

    static Stream<ScheduleRequest> invalidSchedules() {
        List<OpeningHourDto> repeated = new ArrayList<>(week("09:00", "10:00").days());
        repeated.set(1, new OpeningHourDto("MONDAY", null, null));
        List<OpeningHourDto> badDay = new ArrayList<>(week("09:00", "10:00").days());
        badDay.set(1, new OpeningHourDto("FUNDAY", null, null));
        List<OpeningHourDto> nullDay = new ArrayList<>(week("09:00", "10:00").days());
        nullDay.set(1, new OpeningHourDto(null, null, null));
        List<OpeningHourDto> nullEntry = new ArrayList<>(week("09:00", "10:00").days());
        nullEntry.set(1, null);
        return Stream.of(
                null,
                new ScheduleRequest(null),
                new ScheduleRequest(week("09:00", "10:00").days().subList(0, 6)),
                new ScheduleRequest(repeated),
                new ScheduleRequest(badDay),
                new ScheduleRequest(nullDay),
                new ScheduleRequest(nullEntry),
                week("21:00", "09:00"),
                week("09:00", "09:00"),
                week("09:00", null),
                week(null, "21:00"),
                week("9:00", "21:00"),
                week("09:00", "24:00"),
                week("ab:cd", "21:00"));
    }

    @ParameterizedTest
    @MethodSource("invalidSchedules")
    void horarioInvalidoSeRechazaYSeConservaElAnterior(ScheduleRequest request) {
        Restaurant restaurant = TestRestaurants.approvedOpenOnMonday(owner, false);
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(restaurant));
        assertThatThrownBy(() -> service.updateSchedule(owner, request))
                .isInstanceOfAny(RegistrationValidationException.class, NullPointerException.class);
        verify(restaurants, never()).saveAndFlush(any(Restaurant.class));
        assertThat(restaurant.getSchedule().get(0).getOpensAt()).isNotNull();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RestaurantStatus.class, names = {"PENDING", "REJECTED"})
    void horarioYDisponibilidadExigenUnRestauranteAprobado(RestaurantStatus status) {
        when(restaurants.findByOwnerId(owner)).thenReturn(Optional.of(TestRestaurants.restaurant(owner, status)));
        assertThatThrownBy(() -> service.updateSchedule(owner, week("09:00", "21:00"))).isInstanceOf(RestaurantStateException.class);
        assertThatThrownBy(() -> service.setAvailability(owner, new AvailabilityRequest(true))).isInstanceOf(RestaurantStateException.class);
        verify(restaurants, never()).saveAndFlush(any(Restaurant.class));
    }

    @Test
    void activarLaDisponibilidadDentroDelHorarioHabilitaPedidos() {
        approvedRestaurant(false);
        assertThat(service.setAvailability(owner, new AvailabilityRequest(true)).acceptingOrders()).isTrue();
    }

    @Test
    void pausarLaDisponibilidadDeshabilitaPedidos() {
        approvedRestaurant(true);
        RestaurantResponse response = service.setAvailability(owner, new AvailabilityRequest(false));
        assertThat(response.available()).isFalse();
        assertThat(response.acceptingOrders()).isFalse();
    }

    @Test
    void disponibilidadSinValorSeRechaza() {
        assertThatThrownBy(() -> service.setAvailability(owner, new AvailabilityRequest(null)))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.setAvailability(owner, null)).isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void cuentaDelTitularInexistenteNoHabilitaPedidos() {
        when(accounts.findById(owner)).thenReturn(Optional.empty());
        approvedRestaurant(true);
        assertThat(service.setAvailability(owner, new AvailabilityRequest(true)).acceptingOrders()).isFalse();
    }
}
