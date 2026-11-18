package com.mikuna.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.common.exception.ReservationExpiredException;
import com.mikuna.backend.payment.model.Payment;
import com.mikuna.backend.payment.model.PaymentStatus;
import com.mikuna.backend.payment.model.PaymentTransaction;
import com.mikuna.backend.payment.model.TransactionStatus;
import com.mikuna.backend.payment.model.TransactionType;
import com.mikuna.backend.payment.repository.PaymentRepository;
import com.mikuna.backend.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** FR-011a: una reserva vencida no se cobra, deja el pago liberado y no lanza (no revierte la transacción). */
class PaymentServiceCaptureExpiredTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    private final StripeGateway gateway = mock(StripeGateway.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    private final PaymentRecorder recorder = mock(PaymentRecorder.class);
    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private PaymentService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new PaymentService(gateway, new StripeProperties("sk_test_x", "pk_test_x", "pen"), payments,
                transactions, recorder, CLOCK);
        payment = Payment.authorized(orderId, customerId, new BigDecimal("52.25"), "pen", "pi_1",
                Instant.now(CLOCK));
        when(payments.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
    }

    @Test
    void reservaVencidaDevuelveElResultadoMarcaLiberadoYRegistraLaTransaccion() {
        doThrow(new ReservationExpiredException()).when(gateway).capture(any(), any());

        assertThat(service.capture(orderId)).isEqualTo(CaptureResult.RESERVATION_EXPIRED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RELEASED);
        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(tx.capture());
        assertThat(tx.getValue().getType()).isEqualTo(TransactionType.RELEASE);
        assertThat(tx.getValue().getStatus()).isEqualTo(TransactionStatus.RELEASED);
        assertThat(tx.getValue().getResultCode()).isEqualTo("reservation_expired");
    }

    @Test
    void unFalloTransitorioSigueSiendoUnaCaidaYNoTocaElPago() {
        doThrow(new PaymentUnavailableException()).when(gateway).capture(any(), any());

        assertThatThrownBy(() -> service.capture(orderId)).isInstanceOf(PaymentUnavailableException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(transactions, never()).save(any());
    }

    @Test
    void capturarConExitoDevuelveCapturado() {
        assertThat(service.capture(orderId)).isEqualTo(CaptureResult.CAPTURED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }
}
