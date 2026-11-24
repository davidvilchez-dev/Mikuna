package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mikuna.backend.order.service.PendingOrderExpiryService;
import com.mikuna.backend.support.OrderStatusTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 012, US2 y constitución IV: el vencimiento simultáneo con aceptar, cancelar u otro barrido deja un único
 * desenlace coherente. Cada caso se repite {@value #RUNS} veces con estado persistido.
 */
class PendingOrderExpiryConcurrencyIntegrationTest extends OrderStatusTestSupport {

    private static final int RUNS = 50;

    @Autowired
    private PendingOrderExpiryService expiry;

    private List<Integer> race(List<Callable<Integer>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> action : actions) {
            futures.add(pool.submit(() -> {
                start.await();
                return action.call();
            }));
        }
        start.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }

    private int expire(UUID id) {
        return expiry.expire(id) ? 1 : 0;
    }

    private int accept(UUID id) throws Exception {
        return restaurantAct(id, "accept").andReturn().getResponse().getStatus();
    }

    private void expireNow(UUID id) {
        jdbc.update("update pedidos set expires_at = ? where id = ?", Timestamp.from(Instant.now().minusSeconds(1)),
                id);
    }

    private long expiryNotices(UUID id) {
        return jdbc.queryForObject("select count(*) from avisos where order_id = ? and type = 'CANCELLED'",
                Long.class, id);
    }

    @Test
    void vencerYAceptarUnPedidoVencidoNuncaCobra() throws Exception {
        for (int i = 0; i < RUNS; i++) {
            UUID id = placeOrder("xa-" + i);
            expireNow(id);
            clearInvocations(stripeGateway);

            List<Integer> results = race(List.of(() -> expire(id), () -> accept(id)));

            assertThat(results.get(1)).as("aceptar responde ORDER_EXPIRED").isEqualTo(409);
            assertThat(orderStatus(id)).isEqualTo("CANCELLED");
            assertThat(paymentStatus(id)).isEqualTo("RELEASED");
            verify(stripeGateway, never()).capture(any(), any());
            verify(stripeGateway, times(1)).cancel(any(), any());
            assertThat(changesOf(id)).singleElement()
                    .satisfies(row -> assertThat(row.get("actor_role")).isEqualTo("SYSTEM"));
        }
    }

    @Test
    void vencerYAceptarUnPedidoVigenteSoloAcepta() throws Exception {
        for (int i = 0; i < RUNS; i++) {
            UUID id = placeOrder("xb-" + i);
            clearInvocations(stripeGateway);

            List<Integer> results = race(List.of(() -> expire(id), () -> accept(id)));

            assertThat(results).containsExactly(0, 200);
            assertThat(orderStatus(id)).isEqualTo("ACCEPTED");
            assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
            verify(stripeGateway, never()).cancel(any(), any());
            assertThat(changesOf(id)).hasSize(1);
        }
    }

    @Test
    void vencerYCancelarElClienteLiberanUnaSolaVez() throws Exception {
        for (int i = 0; i < RUNS; i++) {
            UUID id = placeOrder("xc-" + i);
            expireNow(id);
            clearInvocations(stripeGateway);

            race(List.of(() -> expire(id),
                    () -> customerCancel(id).andReturn().getResponse().getStatus()));

            assertThat(orderStatus(id)).isEqualTo("CANCELLED");
            assertThat(paymentStatus(id)).isEqualTo("RELEASED");
            verify(stripeGateway, times(1)).cancel(any(), any());
            assertThat(changesOf(id)).hasSize(1);
        }
    }

    @Test
    void dosBarridosALaVezVencenUnaSolaVezConUnAvisoPorDestinatario() throws Exception {
        for (int i = 0; i < RUNS; i++) {
            UUID id = placeOrder("xd-" + i);
            expireNow(id);
            clearInvocations(stripeGateway);

            List<Integer> results = race(List.of(() -> expire(id), () -> expire(id)));

            assertThat(results).containsExactlyInAnyOrder(0, 1);
            assertThat(orderStatus(id)).isEqualTo("CANCELLED");
            verify(stripeGateway, times(1)).cancel(any(), any());
            assertThat(changesOf(id)).hasSize(1);
            assertThat(expiryNotices(id)).as("cliente y restaurante, una vez cada uno").isEqualTo(2);
        }
    }
}
