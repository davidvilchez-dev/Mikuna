package com.mikuna.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.common.exception.RefundFailedException;
import com.mikuna.backend.payment.service.StripeGateway.Authorization;
import com.mikuna.backend.payment.service.StripeGateway.Outcome;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Cliente de Stripe con un servidor simulado: sin red ni credenciales reales (research §1 y §2). */
class StripeGatewayTest {

    private static final String SECRET = "sk_test_clave_de_prueba";
    private static final StripeProperties PROPERTIES = new StripeProperties(SECRET, "pk_test_publica", "pen");

    private static StripeGateway gateway(MockRestServiceServer[] holder) {
        RestClient.Builder builder = RestClient.builder();
        holder[0] = MockRestServiceServer.bindTo(builder).build();
        return new StripeGateway(builder, PROPERTIES);
    }

    @Test
    void autorizaConCapturaManualYDevuelveElIntentoReservado() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andExpect(header("Idempotency-Key", "clave-1"))
                .andExpect(content().string(containsString("amount=5225")))
                .andExpect(content().string(containsString("currency=pen")))
                .andExpect(content().string(containsString("payment_method=pm_card_visa")))
                .andExpect(content().string(containsString("capture_method=manual")))
                .andExpect(content().string(containsString("confirm=true")))
                .andExpect(content().string(containsString("allow_redirects%5D=never")))
                .andRespond(withSuccess("{\"id\":\"pi_1\",\"status\":\"requires_capture\"}",
                        MediaType.APPLICATION_JSON));

        Authorization result = gateway.authorize(5225L, "pen", "pm_card_visa", "clave-1", "order-1");

        assertThat(result).isEqualTo(new Authorization(Outcome.REQUIRES_CAPTURE, "pi_1", null, null));
        server[0].verify();
    }

    @Test
    void unaTarjetaQueExigeVerificacionSeDevuelveComoRequiereAccion() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents"))
                .andRespond(withSuccess("{\"id\":\"pi_3ds\",\"status\":\"requires_action\"}",
                        MediaType.APPLICATION_JSON));

        Authorization result = gateway.authorize(100L, "pen", "pm_x", "k", "o");

        assertThat(result.outcome()).isEqualTo(Outcome.REQUIRES_ACTION);
        assertThat(result.intentId()).isEqualTo("pi_3ds");
    }

    @Test
    void unEstadoInesperadoSeTrataComoRechazoConElIntento() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents"))
                .andRespond(withSuccess("{\"id\":\"pi_x\",\"status\":\"processing\"}", MediaType.APPLICATION_JSON));

        Authorization result = gateway.authorize(100L, "pen", "pm_x", "k", "o");

        assertThat(result.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(result.intentId()).isEqualTo("pi_x");
    }

    @Test
    void unaTarjetaRechazadaSeDevuelveComoRechazoConSuCodigo() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"card_error\",\"code\":\"card_declined\","
                                + "\"decline_code\":\"insufficient_funds\"}}"));

        Authorization result = gateway.authorize(100L, "pen", "pm_x", "k", "o");

        assertThat(result.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(result.code()).isEqualTo("insufficient_funds");
        assertThat(result.intentId()).isNull();
    }

    @Test
    void unRechazoSinCodigoUsaUnCodigoGenerico() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"card_error\"}}"));

        assertThat(gateway.authorize(100L, "pen", "pm_x", "k", "o").code()).isEqualTo("card_declined");
    }

    @Test
    void unMetodoDePagoInvalidoSeTrataComoRechazo() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"invalid_request_error\",\"param\":\"payment_method\","
                                + "\"code\":\"resource_missing\"}}"));

        assertThat(gateway.authorize(100L, "pen", "pm_x", "k", "o").outcome()).isEqualTo(Outcome.DECLINED);
    }

    @Test
    void unFalloDelServidorOUnaClaveInvalidaSeTrataComoPasarelaNoDisponible() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents")).andRespond(withServerError());
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid API Key provided: " + SECRET + "\"}}"));

        assertThatThrownBy(() -> gateway.authorize(100L, "pen", "pm_x", "k", "o"))
                .isInstanceOf(PaymentUnavailableException.class);
        assertThatThrownBy(() -> gateway.authorize(100L, "pen", "pm_x", "k", "o"))
                .isInstanceOf(PaymentUnavailableException.class)
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(SECRET));
    }

    @Test
    void unTiempoAgotadoSeTrataComoPasarelaNoDisponible() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents"))
                .andRespond((request) -> {
                    throw new IOException("Read timed out");
                });

        assertThatThrownBy(() -> gateway.authorize(100L, "pen", "pm_x", "k", "o"))
                .isInstanceOf(PaymentUnavailableException.class);
    }

    @Test
    void capturaYLiberacionLlamanALosEndpointsDelIntentoConSuClaveDeIdempotencia() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents/pi_9/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "capture-1"))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andRespond(withSuccess("{\"id\":\"pi_9\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents/pi_9/cancel"))
                .andExpect(header("Idempotency-Key", "release-1"))
                .andExpect(content().string(not(containsString(SECRET))))
                .andRespond(withSuccess("{\"id\":\"pi_9\",\"status\":\"canceled\"}", MediaType.APPLICATION_JSON));

        gateway.capture("pi_9", "capture-1");
        gateway.cancel("pi_9", "release-1");

        server[0].verify();
    }

    @Test
    void fallosAlCapturarOLiberarSeTratanComoPasarelaNoDisponible() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents/pi_9/capture"))
                .andRespond(withServerError());
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents/pi_9/cancel"))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> gateway.capture("pi_9", "c")).isInstanceOf(PaymentUnavailableException.class);
        assertThatThrownBy(() -> gateway.cancel("pi_9", "r")).isInstanceOf(PaymentUnavailableException.class);
    }

    private static final String EXPIRED_BODY =
            "{\"error\":{\"code\":\"payment_intent_unexpected_state\",\"payment_intent\":{\"status\":\"canceled\"}}}";

    @Test
    void capturarUnaReservaVencidaOCanceladaLanzaReservaVencida() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents/pi_9/capture"))
                .andRespond(withBadRequest().body(EXPIRED_BODY).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.capture("pi_9", "c"))
                .isInstanceOf(com.mikuna.backend.common.exception.ReservationExpiredException.class);
    }

    @Test
    void liberarUnIntentoYaCanceladoSeDaPorHechoPeroUnoYaCobradoEsUnFallo() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents/pi_9/cancel"))
                .andRespond(withBadRequest().body(EXPIRED_BODY).contentType(MediaType.APPLICATION_JSON));
        server[0].expect(requestTo("https://api.stripe.com/v1/payment_intents/pi_8/cancel"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(
                        "{\"error\":{\"code\":\"payment_intent_unexpected_state\","
                                + "\"payment_intent\":{\"status\":\"succeeded\"}}}"));

        gateway.cancel("pi_9", "r1");
        assertThatThrownBy(() -> gateway.cancel("pi_8", "r2")).isInstanceOf(PaymentUnavailableException.class);
    }

    // Spec 014: reembolso total del intento cobrado.

    @Test
    void reembolsaElIntentoCompletoConSuClaveDeIdempotencia() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andExpect(header("Idempotency-Key", "refund-1"))
                .andExpect(content().string(containsString("payment_intent=pi_9")))
                .andExpect(content().string(not(containsString("amount"))))
                .andRespond(withSuccess("{\"id\":\"re_1\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        server[0].expect(requestTo("https://api.stripe.com/v1/refunds"))
                .andRespond(withSuccess("{\"id\":\"re_2\",\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        gateway.refund("pi_9", "refund-1");
        gateway.refund("pi_9", "refund-1");

        server[0].verify();
    }

    @Test
    void unReembolsoFallidoOCanceladoEsUnRechazo() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/refunds"))
                .andRespond(withSuccess("{\"id\":\"re_1\",\"status\":\"failed\"}", MediaType.APPLICATION_JSON));
        server[0].expect(requestTo("https://api.stripe.com/v1/refunds"))
                .andRespond(withSuccess("{\"id\":\"re_2\",\"status\":\"canceled\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.refund("pi_9", "k")).isInstanceOf(RefundFailedException.class);
        assertThatThrownBy(() -> gateway.refund("pi_9", "k")).isInstanceOf(RefundFailedException.class);
    }

    @Test
    void unCargoYaReembolsadoSeDaPorHecho() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/refunds"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"charge_already_refunded\"}}"));

        gateway.refund("pi_9", "k");

        server[0].verify();
    }

    @Test
    void otrosErroresAlReembolsarSonPasarelaNoDisponible() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        StripeGateway gateway = gateway(server);
        server[0].expect(requestTo("https://api.stripe.com/v1/refunds")).andRespond(withServerError());
        server[0].expect(requestTo("https://api.stripe.com/v1/refunds"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"resource_missing\"}}"));
        server[0].expect(requestTo("https://api.stripe.com/v1/refunds"))
                .andRespond((request) -> {
                    throw new IOException("Read timed out");
                });

        assertThatThrownBy(() -> gateway.refund("pi_9", "k")).isInstanceOf(PaymentUnavailableException.class);
        assertThatThrownBy(() -> gateway.refund("pi_9", "k")).isInstanceOf(PaymentUnavailableException.class);
        assertThatThrownBy(() -> gateway.refund("pi_9", "k")).isInstanceOf(PaymentUnavailableException.class);
    }
}
