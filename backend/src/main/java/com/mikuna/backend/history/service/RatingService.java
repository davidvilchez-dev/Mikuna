package com.mikuna.backend.history.service;

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
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calificación única de un pedido propio entregado (RF-31). */
@Service
public class RatingService {

    public static final String INVALID_VALUE = "La calificación debe ser un número entero de 1 a 5";

    private final OrderRepository orders;
    private final RatingRepository ratings;
    private final Clock clock;

    public RatingService(OrderRepository orders, RatingRepository ratings, Clock clock) {
        this.orders = orders;
        this.ratings = ratings;
        this.clock = clock;
    }

    /**
     * Valida el valor, la pertenencia y el estado, y registra. La unicidad en PostgreSQL resuelve las solicitudes
     * simultáneas: quien pierde la carrera recibe el mismo conflicto, sin modificar la calificación original.
     */
    @Transactional
    public Rating rate(UUID customerId, UUID orderId, BigDecimal rawValue) {
        int value = validValue(rawValue);
        Order order = orders.findByIdAndCustomerId(orderId, customerId).orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new RatingConflictException(RatingConflictException.ORDER_NOT_DELIVERED,
                    "Solo puedes calificar un pedido que ya te entregaron");
        }
        if (ratings.existsByOrderId(orderId)) {
            throw alreadyRated();
        }
        try {
            return ratings.saveAndFlush(new Rating(orderId, customerId, value, Instant.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            throw alreadyRated();
        }
    }

    static int validValue(BigDecimal raw) {
        if (raw == null) {
            throw new RegistrationValidationException("value", INVALID_VALUE);
        }
        BigDecimal stripped = raw.stripTrailingZeros();
        if (stripped.scale() > 0 || stripped.compareTo(BigDecimal.ONE) < 0
                || stripped.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new RegistrationValidationException("value", INVALID_VALUE);
        }
        return stripped.intValueExact();
    }

    private static RatingConflictException alreadyRated() {
        return new RatingConflictException(RatingConflictException.ALREADY_EXISTS, "Este pedido ya fue calificado");
    }
}
