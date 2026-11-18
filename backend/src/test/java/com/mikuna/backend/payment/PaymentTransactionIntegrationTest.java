package com.mikuna.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.payment.service.PaymentService;
import com.mikuna.backend.payment.service.StripeGateway.Authorization;
import com.mikuna.backend.payment.service.StripeGateway.Outcome;
import com.mikuna.backend.support.OrderTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RF-17: cada transacción de pago queda registrada con su tipo, estado, monto, resultado y referencia. */
class PaymentTransactionIntegrationTest extends OrderTestSupport {

    @Autowired
    private PaymentService payments;

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("select order_id, customer_id, confirmation_key, type, status, amount, "
                + "result_code, gateway_ref, created_at from transacciones_pago order by created_at, type");
    }

    @Test
    void autorizacionCobroYLiberacionQuedanAsociadasAlPedido() throws Exception {
        confirm(token, "t1", body(TOTAL));
        UUID orderId = jdbc.queryForObject("select id from pedidos", UUID.class);
        payments.capture(orderId);

        List<Map<String, Object>> rows = rows();
        assertThat(rows).extracting(row -> row.get("type")).containsExactly("AUTHORIZATION", "CAPTURE");
        assertThat(rows).extracting(row -> row.get("status")).containsExactly("AUTHORIZED", "CAPTURED");
        for (Map<String, Object> row : rows) {
            assertThat(row.get("order_id")).isEqualTo(orderId);
            assertThat(row.get("customer_id")).isEqualTo(customer.getId());
            assertThat(row.get("amount")).isEqualTo(new BigDecimal("52.25"));
            assertThat(row.get("gateway_ref")).isEqualTo("pi_test_1");
            assertThat(row.get("created_at")).isNotNull();
        }
        assertThat(rows.get(0).get("confirmation_key")).isEqualTo("t1");
    }

    @Test
    void laLiberacionQuedaRegistradaYNoExisteCobro() throws Exception {
        confirm(token, "t2", body(TOTAL));
        UUID orderId = jdbc.queryForObject("select id from pedidos", UUID.class);
        payments.release(orderId);

        assertThat(rows()).extracting(row -> row.get("type")).containsExactly("AUTHORIZATION", "RELEASE");
        assertThat(rows()).extracting(row -> row.get("status")).containsExactly("AUTHORIZED", "RELEASED");
    }

    @Test
    void unFalloDeAutorizacionSeRegistraSinPedidoConElResultado() throws Exception {
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any()))
                .thenReturn(new Authorization(Outcome.DECLINED, null, "card_declined", "x"));
        confirm(token, "t3", body(TOTAL));

        List<Map<String, Object>> rows = rows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("type", "AUTHORIZATION").containsEntry("status", "FAILED")
                .containsEntry("result_code", "card_declined").containsEntry("confirmation_key", "t3");
        assertThat(rows.get(0).get("order_id")).isNull();
        assertThat(rows.get(0).get("customer_id")).isEqualTo(customer.getId());
    }

    @Test
    void unFalloAlCapturarSeRegistraConSuPedido() throws Exception {
        confirm(token, "t4", body(TOTAL));
        UUID orderId = jdbc.queryForObject("select id from pedidos", UUID.class);
        doThrow(new PaymentUnavailableException()).when(stripeGateway).capture(any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(PaymentUnavailableException.class,
                () -> payments.capture(orderId));

        assertThat(rows()).anySatisfy(row -> {
            assertThat(row).containsEntry("type", "CAPTURE").containsEntry("status", "FAILED")
                    .containsEntry("result_code", "gateway_unavailable");
            assertThat(row.get("order_id")).isEqualTo(orderId);
        });
    }
}
