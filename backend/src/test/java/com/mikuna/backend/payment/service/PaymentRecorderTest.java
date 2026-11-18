package com.mikuna.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mikuna.backend.payment.model.PaymentTransaction;
import com.mikuna.backend.payment.model.TransactionStatus;
import com.mikuna.backend.payment.model.TransactionType;
import com.mikuna.backend.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** El registro guarda solo códigos y mensajes seguros y la referencia de la pasarela (RF-17). */
class PaymentRecorderTest {

    @Test
    void guardaLaTransaccionConLosDatosRecibidosYLaFechaDelReloj() {
        PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
        Instant now = Instant.parse("2026-11-17T12:00:00Z");
        PaymentRecorder recorder = new PaymentRecorder(repository, Clock.fixed(now, ZoneOffset.UTC));
        UUID customer = UUID.randomUUID();

        recorder.record(null, customer, "k1", TransactionType.AUTHORIZATION, TransactionStatus.FAILED,
                new BigDecimal("52.25"), "card_declined", "rechazada", "pi_1");

        ArgumentCaptor<PaymentTransaction> saved = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repository).save(saved.capture());
        PaymentTransaction tx = saved.getValue();
        assertThat(tx.getOrderId()).isNull();
        assertThat(tx.getCustomerId()).isEqualTo(customer);
        assertThat(tx.getConfirmationKey()).isEqualTo("k1");
        assertThat(tx.getType()).isEqualTo(TransactionType.AUTHORIZATION);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getAmount()).isEqualByComparingTo("52.25");
        assertThat(tx.getResultCode()).isEqualTo("card_declined");
        assertThat(tx.getResultMessage()).isEqualTo("rechazada");
        assertThat(tx.getGatewayRef()).isEqualTo("pi_1");
        assertThat(tx.getCreatedAt()).isEqualTo(now);
    }
}
