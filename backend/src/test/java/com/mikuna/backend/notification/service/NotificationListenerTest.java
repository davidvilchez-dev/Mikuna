package com.mikuna.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.notification.model.NotificationType;
import com.mikuna.backend.notification.service.NotificationRecipients.Draft;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.OrderPlacedEvent;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChangedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** FR-013 y FR-014: reintentos limitados, duplicados ignorados y ningún error hacia el cambio de estado. */
class NotificationListenerTest {

    private final NotificationRecipients recipients = mock(NotificationRecipients.class);
    private final NotificationWriter writer = mock(NotificationWriter.class);
    private final NotificationListener listener = new NotificationListener(recipients, writer);
    private final UUID orderId = UUID.randomUUID();
    private final Draft draft = new Draft(UUID.randomUUID(), NotificationType.ACCEPTED, "Mensaje");
    private final OrderStatusChangedEvent event = new OrderStatusChangedEvent(UUID.randomUUID(), orderId,
            OrderStatus.PENDING, OrderStatus.ACCEPTED, UUID.randomUUID(), ActorRole.RESTAURANT);

    @Test
    void guardaCadaAvisoUnaVezConElCambioComoOrigen() {
        when(recipients.forStatusChange(event)).thenReturn(List.of(draft));

        listener.onStatusChanged(event);

        verify(writer).save(draft, orderId, event.changeId().toString());
    }

    @Test
    void reintentaHastaTresVecesYLuegoSeDescartaSinLanzar() {
        when(recipients.forStatusChange(event)).thenReturn(List.of(draft));
        when(writer.save(any(), any(), any())).thenThrow(new IllegalStateException("fallo"));

        assertThatCode(() -> listener.onStatusChanged(event)).doesNotThrowAnyException();

        verify(writer, times(NotificationListener.ATTEMPTS)).save(any(), any(), any());
    }

    @Test
    void unFalloPuntualSeRecuperaEnElReintento() {
        when(recipients.forStatusChange(event)).thenReturn(List.of(draft));
        when(writer.save(any(), any(), any())).thenThrow(new IllegalStateException("fallo")).thenReturn(true);

        listener.onStatusChanged(event);

        verify(writer, times(2)).save(any(), any(), any());
    }

    @Test
    void unDuplicadoSeIgnoraSinReintentar() {
        when(recipients.forStatusChange(event)).thenReturn(List.of(draft));
        when(writer.save(any(), any(), any())).thenThrow(new DataIntegrityViolationException("duplicado"));

        assertThatCode(() -> listener.onStatusChanged(event)).doesNotThrowAnyException();

        verify(writer, times(1)).save(any(), any(), any());
    }

    @Test
    void siFallaLaResolucionDeDestinatariosNoSeLanzaNiSeGuardaNada() {
        when(recipients.forStatusChange(event)).thenThrow(new IllegalStateException("fallo"));

        assertThatCode(() -> listener.onStatusChanged(event)).doesNotThrowAnyException();

        org.mockito.Mockito.verifyNoInteractions(writer);
    }

    @Test
    void elPedidoNuevoUsaUnOrigenPropioPorPedido() {
        OrderPlacedEvent placed = new OrderPlacedEvent(orderId);
        when(recipients.forPlaced(placed)).thenReturn(List.of(draft));

        listener.onPlaced(placed);

        verify(writer).save(draft, orderId, "PLACED:" + orderId);
    }

    @Test
    void unFalloDeUnDestinatarioNoImpideAvisarALosDemas() {
        Draft other = new Draft(UUID.randomUUID(), NotificationType.ACCEPTED, "Otro");
        when(recipients.forStatusChange(event)).thenReturn(List.of(draft, other));
        when(writer.save(org.mockito.ArgumentMatchers.eq(draft), any(), any()))
                .thenThrow(new IllegalStateException("fallo"));

        listener.onStatusChanged(event);

        verify(writer).save(other, orderId, event.changeId().toString());
    }
}
