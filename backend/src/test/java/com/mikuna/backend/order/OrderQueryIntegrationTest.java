package com.mikuna.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikuna.backend.payment.service.PaymentService;
import com.mikuna.backend.support.OrderTestSupport;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RF-17 y RF-04: consulta del pedido propio, estado del pago, transacciones y ausencia de datos sensibles. */
class OrderQueryIntegrationTest extends OrderTestSupport {

    @Autowired
    private PaymentService payments;

    private UUID orderId;

    @BeforeEach
    void confirmOrder() throws Exception {
        confirm(token, "consulta", body(TOTAL));
        orderId = jdbc.queryForObject("select id from pedidos", UUID.class);
    }

    private org.springframework.test.web.servlet.ResultActions query(String bearer, UUID id) throws Exception {
        return mvc.perform(get(ORDERS + "/" + id).header("Authorization", "Bearer " + bearer));
    }

    @Test
    void elPedidoPropioMuestraElEstadoDelPagoAntesYDespuesDeCapturarOLiberar() throws Exception {
        query(token, orderId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));

        payments.capture(orderId);
        query(token, orderId).andExpect(jsonPath("$.payment.status").value("CAPTURED"));
    }

    @Test
    void despuesDeLiberarElPagoApareceLiberado() throws Exception {
        payments.release(orderId);
        query(token, orderId).andExpect(jsonPath("$.payment.status").value("RELEASED"));
    }

    @Test
    void unPedidoAjenoOInexistenteResponde404() throws Exception {
        customer("otro@mail.com");
        String otherToken = loginAndGetToken("otro@mail.com");
        query(otherToken, orderId).andExpect(status().isNotFound());
        query(token, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void sinTokenResponde401ConOtroRolOCuentaInhabilitada403() throws Exception {
        query("token-invalido", orderId).andExpect(status().isUnauthorized());
        query(loginAndGetToken("local@mail.com"), orderId).andExpect(status().isForbidden());
        disable(customer.getId());
        query(token, orderId).andExpect(status().isForbidden());
    }

    @Test
    void laRespuestaNoExponeReferenciasDeLaPasarelaNiDatosDeTarjeta() throws Exception {
        query(token, orderId).andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("pi_test"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("pm_"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("sk_test"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("gateway"))));
    }

    @Test
    void ningunaTablaDePedidosOPagosGuardaDatosDeTarjetaNiClaves() {
        payments.capture(orderId);
        StringBuilder dump = new StringBuilder();
        for (String table : new String[] {"pedidos", "lineas_pedido", "pagos", "transacciones_pago"}) {
            jdbc.queryForList("select * from " + table).forEach(row -> dump.append(row).append('\n'));
        }
        assertThat(dump.toString()).doesNotContain("pm_card_visa").doesNotContain("sk_test")
                .doesNotContain("pk_test").doesNotContain("4242 4242").doesNotContain("4242424242424242").doesNotContainIgnoringCase("cvc");
    }
}
