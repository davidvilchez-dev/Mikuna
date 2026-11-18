package com.mikuna.backend.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.delivery.service.CourierAvailabilityService;
import com.mikuna.backend.delivery.service.CourierDeliveryService;
import com.mikuna.backend.delivery.service.DeliveryAdminService;
import com.mikuna.backend.delivery.service.DeliveryAssignmentService;
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
 * RNF-05 y constitución IV: una entrega nunca queda con dos repartidores ni un repartidor con dos entregas activas,
 * comprobado sobre el estado persistido ante solicitudes simultáneas (cada carrera se repite 30 veces).
 */
class DeliveryAssignmentConcurrencyIntegrationTest extends DeliveryTestSupport {

    private static final int REPEATS = 30;

    @Autowired
    private DeliveryAssignmentService assignment;

    @Autowired
    private DeliveryAdminService adminService;

    @Autowired
    private CourierDeliveryService courierService;

    @Autowired
    private CourierAvailabilityService availabilityService;

    private Account a;
    private Account b;
    private Account c;
    private UUID adminId;

    @BeforeEach
    void couriers() throws Exception {
        a = courierAccount("a@mail.com");
        b = courierAccount("b@mail.com");
        c = courierAccount("c@mail.com");
        adminId = admin("admin@mail.com").getId();
    }

    /** Ejecuta las acciones a la vez; devuelve si cada una tuvo éxito (una excepción de negocio es un fracaso). */
    private List<Boolean> race(List<Callable<Boolean>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> action : actions) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return action.call();
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

    private static long successes(List<Boolean> results) {
        return results.stream().filter(r -> r).count();
    }

    private void makeAvailable(Account... couriers) {
        for (Account courier : couriers) {
            jdbc.update("insert into repartidores_disponibilidad (courier_id, available, free_since, updated_at) "
                    + "values (?, true, now(), now())", courier.getId());
        }
    }

    /** Deja a los repartidores libres para la siguiente repetición. */
    private void reset() {
        jdbc.update("update pedidos set status = 'DELIVERED' where status in ('READY', 'ASSIGNED', 'PICKED_UP')");
        jdbc.update("delete from rechazos_entrega");
        jdbc.update("delete from repartidores_disponibilidad");
    }

    private int courierCount(UUID orderId) {
        return jdbc.queryForObject("select count(courier_id) from entregas where order_id = ?", Integer.class,
                orderId);
    }

    private long assignedChanges(UUID orderId) {
        return transitions(orderId).stream().filter("READY→ASSIGNED"::equals).count();
    }

    private int activeDeliveries(UUID courierId) {
        return jdbc.queryForObject("select count(*) from entregas d join pedidos o on o.id = d.order_id "
                + "where d.courier_id = ? and o.status in ('ASSIGNED', 'PICKED_UP')", Integer.class, courierId);
    }

    @Test
    void variasAsignacionesAutomaticasSimultaneasDejanUnSoloRepartidor() throws Exception {
        for (int i = 0; i < REPEATS; i++) {
            UUID order = readyOrder("auto-" + i);
            makeAvailable(a, b, c);

            List<Boolean> results = race(List.of(() -> assignment.assignAutomatically(order),
                    () -> assignment.assignAutomatically(order), () -> assignment.assignAutomatically(order),
                    () -> assignment.assignAutomatically(order), () -> assignment.assignAutomatically(order),
                    () -> assignment.assignAutomatically(order)));

            assertThat(successes(results)).as("repetición %d", i).isEqualTo(1);
            assertThat(courierCount(order)).isEqualTo(1);
            assertThat(orderStatus(order)).isEqualTo("ASSIGNED");
            assertThat(assignedChanges(order)).isEqualTo(1);
            reset();
        }
    }

    @Test
    void asignacionAutomaticaYManualSimultaneasSoloUnaProspera() throws Exception {
        int manualWins = 0;
        for (int i = 0; i < REPEATS; i++) {
            UUID order = readyOrder("mix-" + i);
            makeAvailable(a, b);

            List<Boolean> results = race(List.of(() -> assignment.assignAutomatically(order),
                    () -> adminService.assign(adminId, order, b.getId()) != null));

            assertThat(successes(results)).as("repetición %d", i).isEqualTo(1);
            assertThat(courierCount(order)).isEqualTo(1);
            assertThat(assignedChanges(order)).isEqualTo(1);
            var last = changesOf(order).get(changesOf(order).size() - 1);
            if (results.get(1)) {
                manualWins++;
                assertThat(courierOf(order)).isEqualTo(b.getId());
                assertThat(last.get("actor_role")).isEqualTo("ADMIN");
            } else {
                assertThat(last.get("actor_role")).isEqualTo("SYSTEM");
            }
            reset();
        }
        assertThat(manualWins).isBetween(0, REPEATS);
    }

    @Test
    void rechazoYReasignacionSimultaneosTerminanConUnSoloRepartidorQueNoEsElQueRechazo() throws Exception {
        for (int i = 0; i < REPEATS; i++) {
            UUID order = readyOrder("rej-" + i);
            makeAvailable(a, b);
            assertThat(assignment.assignAutomatically(order)).isTrue();
            UUID first = courierOf(order);
            Account other = first.equals(a.getId()) ? b : a;
            assertThat(first).isIn(a.getId(), b.getId());

            race(List.of(() -> courierService.reject(first, order, null) != null,
                    () -> assignment.assignAutomatically(order)));

            assertThat(courierCount(order)).as("repetición %d", i).isEqualTo(1);
            assertThat(courierOf(order)).isEqualTo(other.getId()).isNotEqualTo(first);
            assertThat(orderStatus(order)).isEqualTo("ASSIGNED");
            assertThat(count("rechazos_entrega")).isEqualTo(1);
            reset();
        }
    }

    @Test
    void dosPedidosYUnSoloRepartidorDisponibleSoloUnoRecibeEntrega() throws Exception {
        for (int i = 0; i < REPEATS; i++) {
            UUID first = readyOrder("two-" + i + "-1");
            UUID second = readyOrder("two-" + i + "-2");
            makeAvailable(a);

            List<Boolean> results = race(List.of(() -> assignment.assignAutomatically(first),
                    () -> assignment.assignAutomatically(second)));

            assertThat(successes(results)).as("repetición %d", i).isEqualTo(1);
            assertThat(activeDeliveries(a.getId())).isEqualTo(1);
            assertThat(courierCount(first) + courierCount(second)).isEqualTo(1);
            reset();
        }
    }

    @Test
    void dosRepartidoresQueSeActivanALaVezConUnaPendienteSoloUnoLaRecibe() throws Exception {
        for (int i = 0; i < REPEATS; i++) {
            UUID order = readyOrder("act-" + i);

            race(List.of(() -> availabilityService.set(a.getId(), true) != null,
                    () -> availabilityService.set(b.getId(), true) != null));

            assertThat(courierCount(order)).as("repetición %d", i).isEqualTo(1);
            assertThat(orderStatus(order)).isEqualTo("ASSIGNED");
            assertThat(activeDeliveries(a.getId()) + activeDeliveries(b.getId())).isEqualTo(1);
            reset();
        }
    }

    @Test
    void elReenvioSimultaneoDeRecogerRegistraUnSoloCambio() throws Exception {
        for (int i = 0; i < REPEATS; i++) {
            UUID order = readyOrder("pick-" + i);
            makeAvailable(a);
            assertThat(assignment.assignAutomatically(order)).isTrue();

            List<Boolean> results = race(List.of(() -> courierService.pickup(a.getId(), order) != null,
                    () -> courierService.pickup(a.getId(), order) != null));

            assertThat(successes(results)).as("repetición %d", i).isEqualTo(1);
            assertThat(orderStatus(order)).isEqualTo("PICKED_UP");
            assertThat(transitions(order).stream().filter("ASSIGNED→PICKED_UP"::equals).count()).isEqualTo(1);
            reset();
        }
    }
}
