package com.mikuna.backend.delivery.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.LocationRejectedException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.delivery.dto.LocationRequest;
import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.CourierLocationRepository;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-26: la ubicación solo se acepta con una entrega en curso y con una hora no futura. */
class LocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final CourierLocationRepository locations = mock(CourierLocationRepository.class);
    private final LocationService service = new LocationService(deliveries, locations,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID courierId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @Test
    void conEntregaEnCursoGuardaLaUbicacionDeEsaEntrega() {
        when(deliveries.findActiveByCourier(any(), any())).thenReturn(List.of(new Delivery(orderId, NOW)));

        service.update(courierId, new LocationRequest(-13.15, -74.22, NOW.minusSeconds(5)));

        verify(locations).upsert(courierId, orderId, -13.15, -74.22, NOW.minusSeconds(5));
    }

    @Test
    void sinEntregaEnCursoSeRechazaSinGuardar() {
        when(deliveries.findActiveByCourier(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.update(courierId, new LocationRequest(-13.15, -74.22, NOW)))
                .isInstanceOf(LocationRejectedException.class);

        verify(locations, never()).upsert(any(), any(), anyDouble(), anyDouble(), any());
    }

    @Test
    void unaHoraFuturaMasAllaDeLaToleranciaSeRechaza() {
        when(deliveries.findActiveByCourier(any(), any())).thenReturn(List.of(new Delivery(orderId, NOW)));

        assertThatThrownBy(() -> service.update(courierId, new LocationRequest(-13.15, -74.22, NOW.plusSeconds(61))))
                .isInstanceOf(RegistrationValidationException.class);

        verify(locations, never()).upsert(any(), any(), anyDouble(), anyDouble(), any());
    }

    @Test
    void unaHoraFuturaDentroDeLaToleranciaSeAcepta() {
        when(deliveries.findActiveByCourier(any(), any())).thenReturn(List.of(new Delivery(orderId, NOW)));

        service.update(courierId, new LocationRequest(-13.15, -74.22, NOW.plusSeconds(60)));

        verify(locations).upsert(courierId, orderId, -13.15, -74.22, NOW.plusSeconds(60));
    }
}
