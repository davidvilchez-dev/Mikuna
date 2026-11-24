package com.mikuna.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.repository.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/** Spec 012: el servicio de vencimiento, el barrido y la política de plazo, con dependencias simuladas. */
class PendingOrderExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderStatusService statusService = mock(OrderStatusService.class);
    private final PendingOrderExpiryService service = new PendingOrderExpiryService(orders, statusService, CLOCK);

    @Test
    void venceElPedidoBloqueadoDelegandoEnElServicioDeEstado() {
        Order order = mock(Order.class);
        UUID id = UUID.randomUUID();
        when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(order));
        when(statusService.expireIfDue(order)).thenReturn(true);

        assertThat(service.expire(id)).isTrue();
        verify(statusService).expireIfDue(order);
    }

    @Test
    void unPedidoInexistenteNoSeVence() {
        UUID id = UUID.randomUUID();
        when(orders.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThat(service.expire(id)).isFalse();
        verify(statusService, never()).expireIfDue(any());
    }

    @Test
    void buscaLosPendientesVencidosAhoraEnLotesDeCien() {
        List<UUID> ids = List.of(UUID.randomUUID());
        when(orders.findExpiredIds(OrderStatus.PENDING, NOW, PageRequest.of(0, 100))).thenReturn(ids);

        assertThat(service.dueOrderIds()).isEqualTo(ids);
    }

    @Test
    void elBarridoSigueConLosDemasSiUnoFallaYCuentaLosVencidos() {
        PendingOrderExpiryService expiry = mock(PendingOrderExpiryService.class);
        UUID failing = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        UUID notDue = UUID.randomUUID();
        when(expiry.dueOrderIds()).thenReturn(List.of(failing, ok, notDue));
        doThrow(new IllegalStateException("pasarela caída")).when(expiry).expire(failing);
        when(expiry.expire(ok)).thenReturn(true);
        when(expiry.expire(notDue)).thenReturn(false);

        assertThat(new PendingOrderExpirySweeper(expiry).sweep()).isEqualTo(1);
        verify(expiry).expire(ok);
        verify(expiry).expire(notDue);
    }

    @Test
    void laPoliticaFijaElVencimientoYCalculaSegundosSinNegativos() {
        OrderExpiryPolicy policy = new OrderExpiryPolicy(Duration.ofMinutes(15));
        assertThat(policy.expiresAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));

        Order pending = mock(Order.class);
        when(pending.getStatus()).thenReturn(OrderStatus.PENDING);
        when(pending.getExpiresAt()).thenReturn(NOW.plusSeconds(90));
        assertThat(OrderExpiryPolicy.secondsToExpire(pending, NOW)).isEqualTo(90L);
        assertThat(OrderExpiryPolicy.secondsToExpire(pending, NOW.plusSeconds(200))).isZero();

        Order accepted = mock(Order.class);
        when(accepted.getStatus()).thenReturn(OrderStatus.ACCEPTED);
        when(accepted.getExpiresAt()).thenReturn(NOW.plusSeconds(90));
        assertThat(OrderExpiryPolicy.secondsToExpire(accepted, NOW)).isNull();
    }

    @Test
    void laPoliticaRechazaUnPlazoNoPositivo() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new OrderExpiryPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
