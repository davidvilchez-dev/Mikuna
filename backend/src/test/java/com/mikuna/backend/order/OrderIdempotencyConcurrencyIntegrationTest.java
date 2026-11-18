package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.payment.service.StripeGateway.Authorization;
import com.mikuna.backend.payment.service.StripeGateway.Outcome;
import com.mikuna.backend.support.OrderTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** RF-14, RF-16 y constitución IV: un pedido y una reserva por confirmación, aunque se repita o sea simultánea. */
class OrderIdempotencyConcurrencyIntegrationTest extends OrderTestSupport {

    @Test
    void repetirLaMismaClaveDevuelveElMismoPedidoSinNuevaReserva() throws Exception {
        String first = confirm(token, "r1", body(TOTAL)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = confirm(token, "r1", body(TOTAL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        verify(stripeGateway, times(1)).authorize(anyLong(), any(), any(), any(), any());
        assertThat(count("pedidos")).isEqualTo(1);
        assertThat(count("pagos")).isEqualTo(1);
        assertThat(count("transacciones_pago")).isEqualTo(1);
    }

    @Test
    void reutilizarLaClaveConOtroTotalEsConflictoYNoDevuelveElPedidoAnteriorEnSilencio() throws Exception {
        confirm(token, "r2", body(TOTAL)).andExpect(status().isCreated());
        confirm(token, "r2", body("10.00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMOUNTS_CHANGED"));
        assertThat(count("pedidos")).isEqualTo(1);
    }

    @Test
    void diezConfirmacionesSimultaneasDeLaMismaClaveDejanUnPedidoYUnaReserva() throws Exception {
        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return confirm(token, "simultanea", body(TOTAL)).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(statuses).filteredOn(code -> code == 200).hasSize(attempts - 1);
        assertThat(count("pedidos")).isEqualTo(1);
        assertThat(count("pagos")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from transacciones_pago where type = 'AUTHORIZATION' "
                + "and status = 'AUTHORIZED'", Integer.class)).isEqualTo(1);
        verify(stripeGateway, times(1)).authorize(anyLong(), any(), any(), any(), any());
    }

    @Test
    void doClavesDistintasConElMismoCarritoSoloUnaProsperaPorqueElCarritoSeVacia() throws Exception {
        confirm(token, "d1", body(TOTAL)).andExpect(status().isCreated());
        confirm(token, "d2", body(TOTAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));

        assertThat(count("pedidos")).isEqualTo(1);
        verify(stripeGateway, times(1)).authorize(anyLong(), any(), any(), any(), any());
    }

    @Test
    void laRestriccionUnicaDeLaBaseDeDatosEsLaRedDeSeguridadContraDuplicados() {
        insertOrderWithKey("dup");
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> insertOrderWithKey("dup"));
        assertThat(count("pedidos")).isEqualTo(1);
    }

    @Test
    void siLaClaveYaExisteTrasPasarLaBusquedaSeDevuelveElPedidoExistenteSinLiberarSuReserva() throws Exception {
        // Simula una carrera que evitó el bloqueo: el pedido con la misma clave aparece mientras se autoriza.
        UUID existing = UUID.randomUUID();
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any())).thenAnswer(invocation -> {
            // En otro hilo: así se confirma por su cuenta, como una solicitud concurrente real, y no
            // dentro de la transacción de la confirmación en curso.
            CompletableFuture.runAsync(() -> insertOrderWithKey("carrera", existing)).join();
            return new Authorization(Outcome.REQUIRES_CAPTURE, "pi_test_1", null, null);
        });

        confirm(token, "carrera", body(TOTAL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.toString()));

        verify(stripeGateway, never()).cancel(any(), any());
        assertThat(count("pedidos")).isEqualTo(1);
    }

    private void insertOrderWithKey(String key) {
        insertOrderWithKey(key, UUID.randomUUID());
    }

    private void insertOrderWithKey(String key, UUID id) {
        jdbc.update("""
                insert into pedidos (id, customer_id, restaurant_id, status, confirmation_key, payment_method,
                    subtotal, delivery_fee, commission, total, restaurant_name, delivery_street, delivery_district,
                    created_at)
                values (?, ?, ?, 'PENDING', ?, 'CARD', 45.00, 5.00, 2.25, 52.25, 'La Picantería', 'Jr. Lima 123',
                    'Ayacucho', ?)""", id, customer.getId(), restaurantId, key, Timestamp.from(Instant.now()));
    }
}
