package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mikuna.backend.support.OrderStatusTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Constitución IV y RNF-05: un único resultado coherente ante acciones simultáneas sobre un pedido. */
class OrderDecisionConcurrencyIntegrationTest extends OrderStatusTestSupport {

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
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        pool.shutdown();
        return statuses;
    }

    private int accept(UUID id) throws Exception {
        return restaurantAct(id, "accept").andReturn().getResponse().getStatus();
    }

    private int reject(UUID id) throws Exception {
        return restaurantAct(id, "reject").andReturn().getResponse().getStatus();
    }

    private int cancel(UUID id) throws Exception {
        return customerCancel(id).andReturn().getResponse().getStatus();
    }

    @Test
    void aceptarYCancelarALaVezDejanExactamenteUnResultadoCoherenteTreintaVeces() throws Exception {
        int accepted = 0;
        int cancelled = 0;
        for (int i = 0; i < 30; i++) {
            UUID id = placeOrder("race-" + i);
            clearInvocations(stripeGateway);

            List<Integer> statuses = race(List.of(() -> accept(id), () -> cancel(id)));

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            String order = orderStatus(id);
            String payment = paymentStatus(id);
            if (order.equals("ACCEPTED")) {
                accepted++;
                assertThat(payment).isEqualTo("CAPTURED");
                verify(stripeGateway, times(1)).capture(any(), any());
                verify(stripeGateway, times(0)).cancel(any(), any());
            } else {
                cancelled++;
                assertThat(order).isEqualTo("CANCELLED");
                assertThat(payment).isEqualTo("RELEASED");
                verify(stripeGateway, times(1)).cancel(any(), any());
                verify(stripeGateway, times(0)).capture(any(), any());
            }
            assertThat(changesOf(id)).as("un solo cambio por pedido").hasSize(1);
        }
        assertThat(accepted + cancelled).isEqualTo(30);
    }

    @Test
    void dosAceptacionesSimultaneasCobranUnaSolaVez() throws Exception {
        UUID id = placeOrder("race-a");
        clearInvocations(stripeGateway);

        List<Integer> statuses = race(List.of(() -> accept(id), () -> accept(id), () -> accept(id)));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409, 409);
        verify(stripeGateway, times(1)).capture(any(), any());
        assertThat(paymentStatus(id)).isEqualTo("CAPTURED");
        assertThat(changesOf(id)).hasSize(1);
    }

    @Test
    void dosRechazosSimultaneosLiberanUnaSolaVez() throws Exception {
        UUID id = placeOrder("race-r");
        clearInvocations(stripeGateway);

        List<Integer> statuses = race(List.of(() -> reject(id), () -> reject(id)));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        verify(stripeGateway, times(1)).cancel(any(), any());
        assertThat(paymentStatus(id)).isEqualTo("RELEASED");
        assertThat(changesOf(id)).hasSize(1);
    }

    @Test
    void rechazarYCancelarALaVezLiberanUnaSolaVez() throws Exception {
        UUID id = placeOrder("race-rc");
        clearInvocations(stripeGateway);

        List<Integer> statuses = race(List.of(() -> reject(id), () -> cancel(id)));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        verify(stripeGateway, times(1)).cancel(any(), any());
        assertThat(orderStatus(id)).isIn("REJECTED", "CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("RELEASED");
    }
}
