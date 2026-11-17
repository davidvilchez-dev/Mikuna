package com.mikuna.backend.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.address.dto.AddressRequest;
import com.mikuna.backend.address.dto.AddressResponse;
import com.mikuna.backend.address.model.Address;
import com.mikuna.backend.address.repository.AddressRepository;
import com.mikuna.backend.common.config.DeliveryProperties;
import com.mikuna.backend.common.exception.AddressNotFoundException;
import com.mikuna.backend.common.exception.CartConflictException;
import com.mikuna.backend.common.exception.OutsideDeliveryZoneException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");

    @Mock AddressRepository addresses;
    @Mock ApplicationEventPublisher events;

    private AddressService service;

    @BeforeEach
    void setUp() {
        DeliveryZone zone = new DeliveryZone(new DeliveryProperties(List.of("Ayacucho"), BigDecimal.ONE, BigDecimal.ONE));
        service = new AddressService(addresses, zone, events, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(addresses.save(any(Address.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static AddressRequest request(String street, String district) {
        return new AddressRequest("  Casa ", street, district, " ", "");
    }

    private static Address saved(UUID customer) {
        Address address = Address.create(customer, null, "Jr. Lima 123", "Ayacucho", null, null, NOW);
        ReflectionTestUtils.setField(address, "id", UUID.randomUUID());
        return address;
    }

    @Test
    void registraRecortandoYAsociandoAlClienteDeLaSesion() {
        AddressResponse response = service.create(CUSTOMER, request("  Jr. Lima 123 ", " Ayacucho "));

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addresses).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(response.street()).isEqualTo("Jr. Lima 123");
        assertThat(response.district()).isEqualTo("Ayacucho");
        assertThat(response.label()).isEqualTo("Casa");
        assertThat(response.reference()).isNull();
        assertThat(response.contactPhone()).isNull();
    }

    @Test
    void rechazaUnaDireccionFueraDeLaZonaSinGuardar() {
        assertThatThrownBy(() -> service.create(CUSTOMER, request("Jr. Lima 123", "Huanta")))
                .isInstanceOf(OutsideDeliveryZoneException.class);
        verify(addresses, never()).save(any());
    }

    @Test
    void rechazaLaUndecimaDireccion() {
        when(addresses.countByCustomerId(CUSTOMER)).thenReturn(10L);

        assertThatThrownBy(() -> service.create(CUSTOMER, request("Jr. Lima 123", "Ayacucho")))
                .isInstanceOfSatisfying(CartConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CartConflictException.ADDRESS_LIMIT));
        verify(addresses, never()).save(any());
    }

    @Test
    void editarHaciaUnDistritoFueraDeZonaNoModifica() {
        Address address = saved(CUSTOMER);
        when(addresses.findByIdAndCustomerId(address.getId(), CUSTOMER)).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> service.update(CUSTOMER, address.getId(), request("Av. Nueva 55", "Huanta")))
                .isInstanceOf(OutsideDeliveryZoneException.class);
        assertThat(address.getStreet()).isEqualTo("Jr. Lima 123");
        verify(addresses, never()).save(any());
    }

    @Test
    void editaUnaDireccionPropia() {
        Address address = saved(CUSTOMER);
        when(addresses.findByIdAndCustomerId(address.getId(), CUSTOMER)).thenReturn(Optional.of(address));

        AddressResponse response = service.update(CUSTOMER, address.getId(), request("Av. Nueva 55", "Ayacucho"));

        assertThat(response.street()).isEqualTo("Av. Nueva 55");
    }

    @Test
    void editarOEliminarUnaDireccionAjenaOInexistenteEsNoEncontrada() {
        UUID id = UUID.randomUUID();
        when(addresses.findByIdAndCustomerId(id, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(CUSTOMER, id, request("Jr. Lima 123", "Ayacucho")))
                .isInstanceOf(AddressNotFoundException.class);
        assertThatThrownBy(() -> service.delete(CUSTOMER, id)).isInstanceOf(AddressNotFoundException.class);
        verify(addresses, never()).delete(any());
    }

    @Test
    void eliminarPublicaElEventoParaQueElCarritoDejeDeSeleccionarla() {
        Address address = saved(CUSTOMER);
        when(addresses.findByIdAndCustomerId(address.getId(), CUSTOMER)).thenReturn(Optional.of(address));

        service.delete(CUSTOMER, address.getId());

        verify(addresses).delete(address);
        verify(events).publishEvent(new AddressDeletedEvent(CUSTOMER, address.getId()));
    }

    @Test
    void listaLasDireccionesDelCliente() {
        when(addresses.findByCustomerIdOrderByCreatedAtAsc(CUSTOMER)).thenReturn(List.of(saved(CUSTOMER)));

        assertThat(service.list(CUSTOMER)).hasSize(1);
    }
}
