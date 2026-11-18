package com.mikuna.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mikuna.backend.common.exception.PaymentStateException;
import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.payment.service.PaymentService;
import com.mikuna.backend.payment.service.StripeGateway;
import com.mikuna.backend.support.OrderTestSupport;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RF-16 y RF-17: capturar y liberar la reserva a lo sumo una vez, de forma excluyente y sin reembolsos. */
class PaymentLifecycleIntegrationTest extends OrderTestSupport {

    @Autowired
    private PaymentService payments;

    private UUID orderId;

    @BeforeEach
    void confirmOrder() throws Exception {
        confirm(token, "ciclo", body(TOTAL));
        orderId = jdbc.queryForObject("select id from pedidos", UUID.class);
    }

    private String paymentStatus() {
        return jdbc.queryForObject("select status from pagos where order_id = ?", String.class, orderId);
    }

    private List<Map<String, Object>> transactions(String type) {
        return jdbc.queryForList("select status, amount, gateway_ref from transacciones_pago where order_id = ? "
                + "and type = ?", orderId, type);
    }

    @Test
    void capturarCobraElMontoReservadoYRegistraLaTransaccion() {
        payments.capture(orderId);

        verify(stripeGateway).capture("pi_test_1", "capture-" + orderId);
        verify(stripeGateway, never()).cancel(any(), any());
        assertThat(paymentStatus()).isEqualTo("CAPTURED");
        List<Map<String, Object>> rows = transactions("CAPTURE");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("status", "CAPTURED").containsEntry("gateway_ref", "pi_test_1");
        assertThat(rows.get(0).get("amount")).isEqualTo(new java.math.BigDecimal("52.25"));
    }

    @Test
    void liberarNuncaCobraYRegistraLaTransaccion() {
        payments.release(orderId);

        verify(stripeGateway).cancel("pi_test_1", "release-" + orderId);
        verify(stripeGateway, never()).capture(any(), any());
        assertThat(paymentStatus()).isEqualTo("RELEASED");
        assertThat(transactions("RELEASE")).hasSize(1);
        assertThat(transactions("CAPTURE")).isEmpty();
    }

    @Test
    void repetirOMezclarOperacionesSeRechazaSinVolverAContactarALaPasarela() {
        payments.capture(orderId);
        assertThatThrownBy(() -> payments.capture(orderId)).isInstanceOf(PaymentStateException.class);
        assertThatThrownBy(() -> payments.release(orderId)).isInstanceOf(PaymentStateException.class);
        verify(stripeGateway, times(1)).capture(any(), any());
        verify(stripeGateway, never()).cancel(any(), any());
        assertThat(paymentStatus()).isEqualTo("CAPTURED");
    }

    @Test
    void capturarTrasLiberarSeRechaza() {
        payments.release(orderId);
        assertThatThrownBy(() -> payments.capture(orderId)).isInstanceOf(PaymentStateException.class);
        assertThatThrownBy(() -> payments.release(orderId)).isInstanceOf(PaymentStateException.class);
        verify(stripeGateway, never()).capture(any(), any());
        verify(stripeGateway, times(1)).cancel(any(), any());
        assertThat(paymentStatus()).isEqualTo("RELEASED");
    }

    @Test
    void unPedidoSinPagoSeRechaza() {
        assertThatThrownBy(() -> payments.capture(UUID.randomUUID())).isInstanceOf(PaymentStateException.class);
        assertThatThrownBy(() -> payments.release(UUID.randomUUID())).isInstanceOf(PaymentStateException.class);
    }

    @Test
    void siLaPasarelaFallaSeRegistraElFalloElEstadoNoCambiaYSePuedeReintentar() {
        doThrow(new PaymentUnavailableException()).when(stripeGateway).capture(any(), any());

        assertThatThrownBy(() -> payments.capture(orderId)).isInstanceOf(PaymentUnavailableException.class);
        assertThat(paymentStatus()).isEqualTo("AUTHORIZED");
        assertThat(transactions("CAPTURE")).extracting(row -> row.get("status")).containsExactly("FAILED");

        doNothing().when(stripeGateway).capture(any(), any());
        payments.capture(orderId);
        assertThat(paymentStatus()).isEqualTo("CAPTURED");
        assertThat(transactions("CAPTURE")).extracting(row -> row.get("status"))
                .containsExactlyInAnyOrder("FAILED", "CAPTURED");
    }

    @Test
    void siLaPasarelaFallaAlLiberarElEstadoNoCambia() {
        doThrow(new PaymentUnavailableException()).when(stripeGateway).cancel(any(), eq("release-" + orderId));

        assertThatThrownBy(() -> payments.release(orderId)).isInstanceOf(PaymentUnavailableException.class);
        assertThat(paymentStatus()).isEqualTo("AUTHORIZED");
        assertThat(transactions("RELEASE")).extracting(row -> row.get("status")).containsExactly("FAILED");
    }

    @Test
    void capturaYLiberacionSimultaneasSoloUnaProspera() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> capture = pool.submit(() -> attempt(start, true));
        Future<Boolean> release = pool.submit(() -> attempt(start, false));
        start.countDown();
        boolean captured = capture.get();
        boolean released = release.get();
        pool.shutdown();

        assertThat(captured ^ released).as("exactamente una operación prospera").isTrue();
        assertThat(paymentStatus()).isEqualTo(captured ? "CAPTURED" : "RELEASED");
        assertThat(transactions("CAPTURE").size() + transactions("RELEASE").size()).isEqualTo(1);
        verify(stripeGateway, times(captured ? 1 : 0)).capture(any(), any());
        verify(stripeGateway, times(captured ? 0 : 1)).cancel(any(), any());
    }

    private boolean attempt(CountDownLatch start, boolean capture) throws InterruptedException {
        start.await();
        try {
            if (capture) {
                payments.capture(orderId);
            } else {
                payments.release(orderId);
            }
            return true;
        } catch (PaymentStateException e) {
            return false;
        }
    }

    @Test
    void noExisteNingunaOperacionDeReembolso() {
        List<String> names = new ArrayList<>();
        for (Class<?> type : new Class<?>[] {PaymentService.class, StripeGateway.class}) {
            for (Method method : type.getDeclaredMethods()) {
                names.add(method.getName().toLowerCase());
            }
        }
        assertThat(names).noneMatch(name -> name.contains("refund") || name.contains("reembols"));
    }
}
