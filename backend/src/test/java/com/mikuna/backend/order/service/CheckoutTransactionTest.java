package com.mikuna.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.order.model.Order;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Reconocimiento de la clave de confirmación duplicada, sea cual sea el idioma del servidor PostgreSQL. */
class CheckoutTransactionTest {

    @Test
    void reconocePorElNombreDeLaRestriccionDeHibernate() {
        ConstraintViolationException violation = new ConstraintViolationException("x", new SQLException("x"),
                "insert", ConstraintViolationException.ConstraintKind.UNIQUE, Order.UK_CONFIRMATION);
        assertThat(CheckoutTransaction.isDuplicateConfirmation(new DataIntegrityViolationException("x", violation)))
                .isTrue();
    }

    @Test
    void reconocePorElMensajeAunqueHibernateNoDevuelvaElNombre() {
        Throwable error = new DataIntegrityViolationException("could not execute statement [ERROR: llave duplicada "
                + "viola restricción de unicidad «uk_pedidos_cliente_clave»]");
        assertThat(CheckoutTransaction.isDuplicateConfirmation(error)).isTrue();
    }

    @Test
    void otrasViolacionesNoSonUnaConfirmacionDuplicada() {
        assertThat(CheckoutTransaction.isDuplicateConfirmation(
                new DataIntegrityViolationException("valor nulo en la columna total"))).isFalse();
        assertThat(CheckoutTransaction.isDuplicateConfirmation(new IllegalStateException())).isFalse();
    }
}
