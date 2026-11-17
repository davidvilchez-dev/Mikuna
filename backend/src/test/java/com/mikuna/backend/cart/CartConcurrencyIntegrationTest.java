package com.mikuna.backend.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestProducts;
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
import org.springframework.http.MediaType;

/** Constitución IV: altas simultáneas en un mismo carrito no pierden, duplican ni mezclan restaurantes. */
class CartConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final String LINES = "/api/customer/cart/lines";

    private Account customer;
    private String token;
    private UUID restaurantId;

    @BeforeEach
    void prepare() throws Exception {
        customer = customer("cliente@mail.com");
        token = loginAndGetToken("cliente@mail.com");
        restaurantId = TestProducts.approvedRestaurant(jdbc, restaurantOwner("local@mail.com").getId(),
                "La Picantería", "Criolla", "Ayacucho");
    }

    private int add(UUID product, int quantity) throws Exception {
        return mvc.perform(post(LINES).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"%s\",\"quantity\":%d}".formatted(product, quantity)))
                .andReturn().getResponse().getStatus();
    }

    /** Lanza todas las tareas a la vez y devuelve los estados HTTP obtenidos. */
    private List<Integer> runTogether(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
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

    @Test
    void altasSimultaneasDelMismoProductoSumanSinPerderNiDuplicar() throws Exception {
        UUID lomo = TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> add(lomo, 2));
        }

        List<Integer> statuses = runTogether(tasks);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(jdbc.queryForObject("select count(*) from carritos", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lineas_carrito", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity from lineas_carrito", Integer.class)).isEqualTo(16);
    }

    @Test
    void elMaximoPorProductoSeRespetaBajoConcurrencia() throws Exception {
        UUID lomo = TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> add(lomo, 5)); // 30 unidades pedidas; solo caben 20
        }

        List<Integer> statuses = runTogether(tasks);

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(4);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(2);
        assertThat(jdbc.queryForObject("select quantity from lineas_carrito", Integer.class)).isEqualTo(20);
    }

    @Test
    void productosDistintosDelMismoRestauranteGeneranUnaLineaCadaUno() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID product = TestProducts.product(jdbc, restaurantId, "Plato " + i, "Fondos", "10.00", true, true);
            tasks.add(() -> add(product, 1));
        }

        List<Integer> statuses = runTogether(tasks);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(jdbc.queryForObject("select count(*) from lineas_carrito", Integer.class)).isEqualTo(6);
    }

    @Test
    void dosRestaurantesALaVezEnUnCarritoVacioNoSeMezclan() throws Exception {
        UUID otherRestaurant = TestProducts.approvedRestaurant(jdbc, restaurantOwner("otro@mail.com").getId(),
                "Pizzería", "Pizza", "Ayacucho");
        UUID lomo = TestProducts.product(jdbc, restaurantId, "Lomo", "Fondos", "22.50", true, true);
        UUID pizza = TestProducts.product(jdbc, otherRestaurant, "Pizza", "Pizzas", "30.00", true, true);

        List<Integer> statuses = runTogether(List.of(() -> add(lomo, 1), () -> add(pizza, 1)));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from lineas_carrito", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(distinct restaurant_id) from carritos", Integer.class))
                .isEqualTo(1);
    }
}
