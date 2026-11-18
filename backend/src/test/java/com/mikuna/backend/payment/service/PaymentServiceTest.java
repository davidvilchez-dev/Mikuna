package com.mikuna.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.PaymentDeclinedException;
import com.mikuna.backend.common.exception.PaymentStateException;
import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.payment.model.Payment;
import com.mikuna.backend.payment.model.PaymentStatus;
import com.mikuna.backend.payment.model.PaymentTransaction;
import com.mikuna.backend.payment.model.TransactionStatus;
import com.mikuna.backend.payment.model.TransactionType;
import com.mikuna.backend.payment.repository.PaymentRepository;
import com.mikuna.backend.payment.repository.PaymentTransactionRepository;
import com.mikuna.backend.payment.service.StripeGateway.Authorization;
import com.mikuna.backend.payment.service.StripeGateway.Outcome;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Reglas del ciclo de vida del pago con la pasarela simulada (Mockito): sin red, base de datos ni claves. */
class PaymentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
    private static final BigDecimal AMOUNT = new BigDecimal("52.25");

    private final StripeGateway gateway = mock(StripeGateway.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    private final PaymentRecorder recorder = mock(PaymentRecorder.class);
    private PaymentService service;
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentService(gateway, new StripeProperties("sk_test_x", "pk_test_x", "pen"), payments,
                transactions, recorder, CLOCK);
    }

    private static Authorization approved(String intent) {
        return new Authorization(Outcome.REQUIRES_CAPTURE, intent, null, null);
    }

    @Test
    void convierteElMontoACentimosSinRedondeosYRechazaMasDeDosDecimales() {
        assertThat(PaymentService.toCents(new BigDecimal("52.25"))).isEqualTo(5225L);
        assertThat(PaymentService.toCents(new BigDecimal("0.10"))).isEqualTo(10L);
        assertThat(PaymentService.toCents(new BigDecimal("1000"))).isEqualTo(100000L);
        assertThatThrownBy(() -> PaymentService.toCents(new BigDecimal("1.005")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void laClaveDeIdempotenciaDependeDelClienteYDeLaClaveDeConfirmacion() {
        assertThat(PaymentService.authorizationKey(customerId, "abc")).isEqualTo("confirm-" + customerId + "-abc");
    }

    @Test
    void autorizaYDevuelveElIntentoConLaMonedaYLaClaveEsperadas() {
        when(gateway.authorize(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(approved("pi_1"));

        String intent = service.authorize(customerId, orderId, AMOUNT, "pm_1", "k1");

        assertThat(intent).isEqualTo("pi_1");
        verify(gateway).authorize(5225L, "pen", "pm_1", "confirm-" + customerId + "-k1", orderId.toString());
        verify(recorder, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unRechazoRegistraElFalloYLanzaPagoRechazadoSinCancelarNada() {
        when(gateway.authorize(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Authorization(Outcome.DECLINED, null, "card_declined", "x"));

        assertThatThrownBy(() -> service.authorize(customerId, orderId, AMOUNT, "pm_1", "k1"))
                .isInstanceOfSatisfying(PaymentDeclinedException.class,
                        e -> assertThat(e.getCode()).isEqualTo(PaymentDeclinedException.DECLINED));
        verify(recorder).record(isNull(), eq(customerId), eq("k1"), eq(TransactionType.AUTHORIZATION),
                eq(TransactionStatus.FAILED), eq(AMOUNT), eq("card_declined"), isNull(), isNull());
        verify(gateway, never()).cancel(any(), any());
    }

    @Test
    void unRechazoConIntentoLoCancelaPorSeguridad() {
        when(gateway.authorize(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Authorization(Outcome.DECLINED, "pi_raro", "unexpected_status", "x"));

        assertThatThrownBy(() -> service.authorize(customerId, orderId, AMOUNT, "pm_1", "k1"))
                .isInstanceOf(PaymentDeclinedException.class);
        verify(gateway).cancel(eq("pi_raro"), anyString());
    }

    @Test
    void laVerificacionAdicionalSeCancelaYSeTrataComoFallo() {
        when(gateway.authorize(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Authorization(Outcome.REQUIRES_ACTION, "pi_3ds", "requires_action", "x"));

        assertThatThrownBy(() -> service.authorize(customerId, orderId, AMOUNT, "pm_1", "k1"))
                .isInstanceOfSatisfying(PaymentDeclinedException.class,
                        e -> assertThat(e.getCode()).isEqualTo(PaymentDeclinedException.ACTION_REQUIRED));
        verify(gateway).cancel(eq("pi_3ds"), anyString());
        verify(recorder).record(isNull(), eq(customerId), eq("k1"), eq(TransactionType.AUTHORIZATION),
                eq(TransactionStatus.FAILED), eq(AMOUNT), eq("requires_action"), isNull(), eq("pi_3ds"));
    }

    @Test
    void unFalloDeConexionSeReintentaUnaVezConLaMismaClaveYSiVuelveAFallarSeRegistra() {
        when(gateway.authorize(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new PaymentUnavailableException());

        assertThatThrownBy(() -> service.authorize(customerId, orderId, AMOUNT, "pm_1", "k1"))
                .isInstanceOf(PaymentUnavailableException.class);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).authorize(anyLong(), anyString(), anyString(), keys.capture(), anyString());
        assertThat(keys.getAllValues()).containsOnly("confirm-" + customerId + "-k1");
        verify(recorder).record(isNull(), eq(customerId), eq("k1"), eq(TransactionType.AUTHORIZATION),
                eq(TransactionStatus.FAILED), eq(AMOUNT), eq("gateway_unavailable"), isNull(), isNull());
    }

    @Test
    void siElReintentoRecuperaUnaReservaDelPrimerIntentoSeLiberaYLaConfirmacionFalla() {
        when(gateway.authorize(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new PaymentUnavailableException()).thenReturn(approved("pi_tardio"));

        assertThatThrownBy(() -> service.authorize(customerId, orderId, AMOUNT, "pm_1", "k1"))
                .isInstanceOf(PaymentUnavailableException.class);
        verify(gateway).cancel(eq("pi_tardio"), anyString());
    }

    @Test
    void siElReintentoEsRechazadoSeInformaElRechazo() {
        when(gateway.authorize(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new PaymentUnavailableException())
                .thenReturn(new Authorization(Outcome.DECLINED, null, "card_declined", "x"));

        assertThatThrownBy(() -> service.authorize(customerId, orderId, AMOUNT, "pm_1", "k1"))
                .isInstanceOf(PaymentDeclinedException.class);
    }

    @Test
    void registraElPagoAutorizadoYSuTransaccion() {
        service.recordAuthorized(customerId, orderId, "k1", AMOUNT, "pi_1");

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getValue().getGatewayRef()).isEqualTo("pi_1");
        assertThat(payment.getValue().getCurrency()).isEqualTo("pen");
        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(tx.capture());
        assertThat(tx.getValue().getType()).isEqualTo(TransactionType.AUTHORIZATION);
        assertThat(tx.getValue().getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);
        assertThat(tx.getValue().getOrderId()).isEqualTo(orderId);
    }

    @Test
    void laCompensacionLiberaLaReservaYNuncaLanza() {
        service.cancelQuietly(customerId, "k1", AMOUNT, "pi_1");
        verify(gateway).cancel(eq("pi_1"), anyString());
        verify(recorder).record(isNull(), eq(customerId), eq("k1"), eq(TransactionType.RELEASE),
                eq(TransactionStatus.RELEASED), eq(AMOUNT), eq("compensation"), isNull(), eq("pi_1"));

        doThrow(new PaymentUnavailableException()).when(gateway).cancel(eq("pi_2"), anyString());
        service.cancelQuietly(customerId, "k2", AMOUNT, "pi_2");
        verify(recorder).record(isNull(), eq(customerId), eq("k2"), eq(TransactionType.RELEASE),
                eq(TransactionStatus.FAILED), eq(AMOUNT), eq("compensation_failed"), isNull(), eq("pi_2"));
    }

    private Payment authorizedPayment() {
        Payment payment = Payment.authorized(orderId, customerId, AMOUNT, "pen", "pi_1", Instant.now(CLOCK));
        when(payments.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        return payment;
    }

    @Test
    void capturarCobraYPasaACapturado() {
        Payment payment = authorizedPayment();

        service.capture(orderId);

        verify(gateway).capture("pi_1", "capture-" + orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        ArgumentCaptor<PaymentTransaction> tx = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(tx.capture());
        assertThat(tx.getValue().getType()).isEqualTo(TransactionType.CAPTURE);
        assertThat(tx.getValue().getStatus()).isEqualTo(TransactionStatus.CAPTURED);
    }

    @Test
    void liberarNoCobraYPasaALiberado() {
        Payment payment = authorizedPayment();

        service.release(orderId);

        verify(gateway).cancel("pi_1", "release-" + orderId);
        verify(gateway, never()).capture(any(), any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RELEASED);
    }

    @Test
    void unPagoYaCobradoOLiberadoSeRechazaSinContactarALaPasarela() {
        Payment captured = authorizedPayment();
        captured.capture(Instant.now(CLOCK));

        assertThatThrownBy(() -> service.capture(orderId)).isInstanceOf(PaymentStateException.class);
        assertThatThrownBy(() -> service.release(orderId)).isInstanceOf(PaymentStateException.class);
        verify(gateway, never()).capture(any(), any());
        verify(gateway, never()).cancel(any(), any());
    }

    @Test
    void unPedidoSinPagoSeRechaza() {
        when(payments.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.capture(orderId)).isInstanceOf(PaymentStateException.class);
    }

    @Test
    void siLaPasarelaFallaSeRegistraYElPagoSigueAutorizado() {
        Payment payment = authorizedPayment();
        doThrow(new PaymentUnavailableException()).when(gateway).capture(any(), any());

        assertThatThrownBy(() -> service.capture(orderId)).isInstanceOf(PaymentUnavailableException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(recorder).record(eq(orderId), eq(customerId), isNull(), eq(TransactionType.CAPTURE),
                eq(TransactionStatus.FAILED), eq(AMOUNT), eq("gateway_unavailable"), isNull(), eq("pi_1"));
        verify(transactions, never()).save(any());
    }
}
