package com.mikuna.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mikuna.backend.common.exception.InvalidOrderTransitionException;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChangedEvent;
import com.mikuna.backend.order.model.PaymentMethod;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.repository.OrderStatusChangeRepository;
import com.mikuna.backend.payment.repository.PaymentRepository;
import com.mikuna.backend.payment.service.PaymentService;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Spec 009: cada cambio de estado guardado publica un evento con estados y responsable correctos. */
class OrderStatusEventTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC);

    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OrderStatusService service = new OrderStatusService(mock(OrderRepository.class),
            mock(OrderStatusChangeRepository.class), mock(PaymentRepository.class), mock(PaymentService.class),
            mock(RestaurantRepository.class), CLOCK, events);

    private Order newOrder() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Local", "k",
                PaymentMethod.CARD, new Order.Amounts(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN),
                new Order.Delivery(null, "Calle 1", "Centro", null, null), Instant.now(CLOCK));
    }

    @Test
    void changeStatusPublicaUnEventoConLosEstadosYElResponsable() {
        UUID actor = UUID.randomUUID();
        Order order = newOrder();
        order.transitionTo(OrderStatus.ACCEPTED);

        service.changeStatus(order, OrderStatus.PREPARING, actor, ActorRole.RESTAURANT, null);

        ArgumentCaptor<OrderStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        OrderStatusChangedEvent event = captor.getValue();
        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.from()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(event.to()).isEqualTo(OrderStatus.PREPARING);
        assertThat(event.actorId()).isEqualTo(actor);
        assertThat(event.actorRole()).isEqualTo(ActorRole.RESTAURANT);
    }

    @Test
    void unaTransicionInvalidaNoPublicaNada() {
        Order order = newOrder();

        assertThatThrownBy(() -> service.changeStatus(order, OrderStatus.DELIVERED, null, ActorRole.SYSTEM, null))
                .isInstanceOf(InvalidOrderTransitionException.class);

        verifyNoInteractions(events);
    }
}
