package com.mikuna.backend.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.support.HistoryTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** SC-005: ante solicitudes simultáneas un pedido nunca acumula más de una calificación. */
class RatingConcurrencyIntegrationTest extends HistoryTestSupport {

    @Test
    void variasSolicitudesSimultaneasRegistranExactamenteUna() throws Exception {
        for (int round : new int[] {2, 5}) {
            UUID id = deliveredOrder("rc-" + round);
            List<Integer> codes = fire(id, round);

            assertThat(codes).as("ronda de " + round).filteredOn(c -> c == 201).hasSize(1);
            assertThat(codes).filteredOn(c -> c == 409).hasSize(round - 1);
            assertThat(ratingRows(id)).isEqualTo(1);
        }
    }

    private List<Integer> fire(UUID orderId, int requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                int value = (i % 5) + 1;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return rate(token, orderId, "{\"value\":" + value + "}").andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                codes.add(future.get());
            }
            return codes;
        } finally {
            pool.shutdownNow();
        }
    }
}
