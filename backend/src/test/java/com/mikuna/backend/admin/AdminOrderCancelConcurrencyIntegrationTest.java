package com.mikuna.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.admin.service.AdminOrderCancellationService;
import com.mikuna.backend.delivery.service.CourierDeliveryService;
import com.mikuna.backend.support.DeliveryTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 014, FR-006 y FR-010: cancelaciones simultáneas, o cancelación junto con la entrega del repartidor, nunca
 * reembolsan dos veces ni dejan un pedido Entregado y Reembolsado (cada carrera se repite 20 veces).
 */
class AdminOrderCancelConcurrencyIntegrationTest extends DeliveryTestSupport {

    private static final int REPEATS = 20;

    @Autowired
    private AdminOrderCancellationService cancellation;

    @Autowired
    private CourierDeliveryService courierService;

    private UUID adminId;
    private Account courier;
    private String courierToken;

    @BeforeEach
    void actors() throws Exception {
        adminId = admin("admin@mail.com").getId();
        courier = courierAccount("rep@mail.com");
        courierToken = loginAndGetToken("rep@mail.com");
    }

    private List<Boolean> race(List<Callable<Object>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Object> action : actions) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    action.call();
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }

    private int refunds(UUID orderId) {
        return jdbc.queryForObject("select count(*) from transacciones_pago where order_id = ? and type = 'REFUND' "
                + "and status = 'REFUNDED'", Integer.class, orderId);
    }

    private long changesTo(UUID orderId, String status) {
        return changesOf(orderId).stream().filter(c -> status.equals(c.get("to_status"))).count();
    }

    @Test
    void dosCancelacionesSimultaneasReembolsanUnaSolaVez() throws Exception {
        for (int i = 0; i < REPEATS; i++) {
            UUID order = placeOrder("doble-" + i);
            restaurantAct(order, "accept").andExpect(status().isOk());

            List<Boolean> results = race(List.of(() -> cancellation.cancel(adminId, order, "Motivo A"),
                    () -> cancellation.cancel(adminId, order, "Motivo B")));

            assertThat(results.stream().filter(r -> r).count()).as("repetición %d", i).isEqualTo(1);
            assertThat(orderStatus(order)).isEqualTo("CANCELLED");
            assertThat(paymentStatus(order)).isEqualTo("REFUNDED");
            assertThat(refunds(order)).isEqualTo(1);
            assertThat(changesTo(order, "CANCELLED")).isEqualTo(1);
        }
    }

    @Test
    void cancelarYEntregarALaVezDejaUnUnicoDesenlaceCoherente() throws Exception {
        setAvailable(courierToken, true);
        for (int i = 0; i < REPEATS; i++) {
            UUID order = readyOrder("carrera-" + i);
            assertThat(courierOf(order)).isEqualTo(courier.getId());
            courierAct(courierToken, order, "pickup").andExpect(status().isOk());

            List<Boolean> results = race(List.of(() -> cancellation.cancel(adminId, order, "Incidente"),
                    () -> courierService.complete(courier.getId(), order)));

            assertThat(results.stream().filter(r -> r).count()).as("repetición %d", i).isEqualTo(1);
            String finalStatus = orderStatus(order);
            if (finalStatus.equals("DELIVERED")) {
                assertThat(paymentStatus(order)).isEqualTo("CAPTURED");
                assertThat(refunds(order)).isZero();
            } else {
                assertThat(finalStatus).isEqualTo("CANCELLED");
                assertThat(paymentStatus(order)).isEqualTo("REFUNDED");
                assertThat(refunds(order)).isEqualTo(1);
            }
            assertThat(changesTo(order, "DELIVERED") + changesTo(order, "CANCELLED")).isEqualTo(1);
        }
    }
}
