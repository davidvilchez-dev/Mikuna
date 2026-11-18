package com.mikuna.backend.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.common.exception.RatingConflictException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.history.model.Rating;
import com.mikuna.backend.history.repository.RatingRepository;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RatingServiceTest {

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private OrderRepository orders;
    private RatingRepository ratings;
    private RatingService service;
    private Order order;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        ratings = mock(RatingRepository.class);
        order = mock(Order.class);
        when(order.getStatus()).thenReturn(OrderStatus.DELIVERED);
        when(orders.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(ratings.saveAndFlush(any(Rating.class))).thenAnswer(call -> call.getArgument(0));
        Clock clock = Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneOffset.UTC);
        service = new RatingService(orders, ratings, clock);
    }

    @Test
    void aceptaLosExtremosYGuardaClienteYFecha() {
        Rating one = service.rate(customerId, orderId, BigDecimal.ONE);
        Rating five = service.rate(customerId, orderId, new BigDecimal("5.0"));

        assertThat(one.getValue()).isEqualTo(1);
        assertThat(five.getValue()).isEqualTo(5);
        assertThat(one.getCustomerId()).isEqualTo(customerId);
        assertThat(one.getOrderId()).isEqualTo(orderId);
        assertThat(one.getCreatedAt()).isEqualTo(Instant.parse("2026-11-18T15:00:00Z"));
    }

    @Test
    void rechazaValoresInvalidosSinGuardar() {
        for (String raw : new String[] {"0", "6", "-1", "3.5", "4.01", "100"}) {
            assertThatThrownBy(() -> service.rate(customerId, orderId, new BigDecimal(raw)))
                    .as(raw).isInstanceOf(RegistrationValidationException.class);
        }
        assertThatThrownBy(() -> service.rate(customerId, orderId, null))
                .isInstanceOf(RegistrationValidationException.class);
        verify(ratings, never()).saveAndFlush(any());
    }

    @Test
    void unPedidoNoEntregadoSeRechazaConSuCodigo() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.DELIVERED) {
                continue;
            }
            when(order.getStatus()).thenReturn(status);
            assertThatThrownBy(() -> service.rate(customerId, orderId, BigDecimal.valueOf(3)))
                    .as(status.name()).isInstanceOfSatisfying(RatingConflictException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(RatingConflictException.ORDER_NOT_DELIVERED));
        }
        verify(ratings, never()).saveAndFlush(any());
    }

    @Test
    void unPedidoAjenoOInexistenteEsNoEncontrado() {
        assertThatThrownBy(() -> service.rate(UUID.randomUUID(), orderId, BigDecimal.valueOf(3)))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> service.rate(customerId, UUID.randomUUID(), BigDecimal.valueOf(3)))
                .isInstanceOf(OrderNotFoundException.class);
        verify(ratings, never()).saveAndFlush(any());
    }

    @Test
    void unPedidoYaCalificadoSeRechazaSinEscribir() {
        when(ratings.existsByOrderId(orderId)).thenReturn(true);

        assertThatThrownBy(() -> service.rate(customerId, orderId, BigDecimal.valueOf(3)))
                .isInstanceOfSatisfying(RatingConflictException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(RatingConflictException.ALREADY_EXISTS));
        verify(ratings, never()).saveAndFlush(any());
    }

    @Test
    void laViolacionDeUnicidadSeTraduceEnConflicto() {
        when(ratings.saveAndFlush(any(Rating.class))).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.rate(customerId, orderId, BigDecimal.valueOf(3)))
                .isInstanceOfSatisfying(RatingConflictException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(RatingConflictException.ALREADY_EXISTS));
    }
}
