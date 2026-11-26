package com.mikuna.backend.payment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikuna.backend.common.exception.PaymentStateException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 014: reembolso total de tarjeta cobrada y anulación del efectivo no entregado. */
class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-11-25T18:00:00Z");

    private static Payment card() {
        return Payment.authorized(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("42.50"), "pen", "pi_1", NOW);
    }

    private static Payment cash() {
        return Payment.cash(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("42.50"), "pen", NOW);
    }

    @Test
    void tarjetaCobradaSeReembolsa() {
        Payment payment = card();
        payment.capture(NOW);
        payment.refund(NOW.plusSeconds(60));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void soloSeReembolsaUnaTarjetaCobrada() {
        assertThatThrownBy(() -> card().refund(NOW)).isInstanceOf(PaymentStateException.class);
        Payment released = card();
        released.release(NOW);
        assertThatThrownBy(() -> released.refund(NOW)).isInstanceOf(PaymentStateException.class);
        Payment refunded = card();
        refunded.capture(NOW);
        refunded.refund(NOW);
        assertThatThrownBy(() -> refunded.refund(NOW)).isInstanceOf(PaymentStateException.class);
    }

    @Test
    void elEfectivoNuncaSeReembolsa() {
        Payment payment = cash();
        payment.capture(NOW);
        assertThatThrownBy(() -> payment.refund(NOW)).isInstanceOf(PaymentStateException.class);
    }

    @Test
    void efectivoPendienteOCobradoAntesDel014QuedaSinCobro() {
        Payment pending = cash();
        pending.voidCash(NOW);
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.RELEASED);

        Payment legacy = cash();
        legacy.capture(NOW);
        legacy.voidCash(NOW);
        assertThat(legacy.getStatus()).isEqualTo(PaymentStatus.RELEASED);
    }

    @Test
    void anularEfectivoNoAplicaATarjetaNiAEfectivoYaAnulado() {
        assertThatThrownBy(() -> card().voidCash(NOW)).isInstanceOf(PaymentStateException.class);
        Payment voided = cash();
        voided.voidCash(NOW);
        assertThatThrownBy(() -> voided.voidCash(NOW)).isInstanceOf(PaymentStateException.class);
    }
}
