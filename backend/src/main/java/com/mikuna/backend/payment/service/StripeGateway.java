package com.mikuna.backend.payment.service;

import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.common.exception.RefundFailedException;
import com.mikuna.backend.common.exception.ReservationExpiredException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Integración con Stripe (modo de pruebas) mediante su API REST sobre HTTPS (RF-16, research §1 y §2). Se
 * aísla de las reglas de negocio (constitución II) y las pruebas la sustituyen por un mock. Nunca recibe
 * datos de tarjeta: solo el identificador del método de pago creado por Stripe.js. La clave secreta no se
 * registra en logs ni en excepciones.
 */
@Component
@EnableConfigurationProperties(StripeProperties.class)
public class StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);
    private static final String BASE_URL = "https://api.stripe.com/v1";
    private static final Pattern ALREADY_CANCELED = Pattern.compile("\"status\"\\s*:\\s*\"canceled\"");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /** Resultado de intentar reservar el monto. */
    public enum Outcome {
        /** Reserva autorizada: el monto queda retenido pendiente de captura. */
        REQUIRES_CAPTURE,
        /** La tarjeta exige una verificación adicional (3D Secure), no soportada en este incremento. */
        REQUIRES_ACTION,
        /** Rechazada por la pasarela o estado inesperado; no hay reserva. */
        DECLINED
    }

    /** {@code intentId} puede ser {@code null} si la pasarela no llegó a crear el intento. */
    public record Authorization(Outcome outcome, String intentId, String code, String message) {
    }

    private final RestClient client;
    private final StripeProperties properties;

    @Autowired
    public StripeGateway(StripeProperties properties) {
        this(RestClient.builder().requestFactory(timeoutFactory()), properties);
    }

    /** Permite a las pruebas enlazar un servidor simulado al constructor del cliente. */
    StripeGateway(RestClient.Builder builder, StripeProperties properties) {
        this.client = builder.baseUrl(BASE_URL).build();
        this.properties = properties;
    }

    private static JdkClientHttpRequestFactory timeoutFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /**
     * Crea y confirma un intento con captura manual: reserva {@code amountCents} sin cobrarlos. Un rechazo de
     * la tarjeta se devuelve como {@link Outcome#DECLINED}; un fallo de la pasarela, como
     * {@link PaymentUnavailableException}.
     */
    public Authorization authorize(long amountCents, String currency, String paymentMethodId,
            String idempotencyKey, String orderId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", Long.toString(amountCents));
        form.add("currency", currency);
        form.add("payment_method", paymentMethodId);
        form.add("capture_method", "manual");
        form.add("confirm", "true");
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("automatic_payment_methods[allow_redirects]", "never");
        form.add("metadata[order_id]", orderId);
        try {
            Map<?, ?> body = post("/payment_intents", form, idempotencyKey);
            String intentId = text(body, "id");
            String status = text(body, "status");
            if ("requires_capture".equals(status)) {
                return new Authorization(Outcome.REQUIRES_CAPTURE, intentId, null, null);
            }
            if ("requires_action".equals(status)) {
                return new Authorization(Outcome.REQUIRES_ACTION, intentId, "requires_action",
                        "La tarjeta exige una verificación adicional");
            }
            return new Authorization(Outcome.DECLINED, intentId, "unexpected_status",
                    "La autorización no se completó");
        } catch (HttpStatusCodeException e) {
            if (isCardRejection(e)) {
                return new Authorization(Outcome.DECLINED, null, errorCode(e), "La tarjeta fue rechazada");
            }
            throw unavailable("autorizar", e);
        } catch (RestClientException e) {
            throw unavailable("autorizar", e);
        }
    }

    /**
     * Cobra el monto reservado (captura completa). Si la reserva venció o fue cancelada lanza
     * {@link ReservationExpiredException}; cualquier otro fallo es {@link PaymentUnavailableException}.
     */
    public void capture(String intentId, String idempotencyKey) {
        try {
            post("/payment_intents/" + intentId + "/capture", new LinkedMultiValueMap<>(), idempotencyKey);
        } catch (HttpStatusCodeException e) {
            if (isUnusableReservation(e)) {
                throw new ReservationExpiredException();
            }
            throw unavailable("capturar", e);
        } catch (RestClientException e) {
            throw unavailable("capturar", e);
        }
    }

    /**
     * Cancela el intento y libera la reserva sin cobrar. Si el intento ya estaba cancelado (la reserva venció),
     * no hay nada que liberar y se considera hecho.
     */
    public void cancel(String intentId, String idempotencyKey) {
        try {
            post("/payment_intents/" + intentId + "/cancel", new LinkedMultiValueMap<>(), idempotencyKey);
        } catch (HttpStatusCodeException e) {
            if (isUnusableReservation(e) && ALREADY_CANCELED.matcher(e.getResponseBodyAsString()).find()) {
                return;
            }
            throw unavailable("liberar", e);
        } catch (RestClientException e) {
            throw unavailable("liberar", e);
        }
    }

    /**
     * Reembolsa por completo el intento ya cobrado (spec 014). {@code succeeded} o {@code pending} cuentan como hecho
     * (la acreditación bancaria no depende de la plataforma); {@code failed} o {@code canceled} lanzan
     * {@link RefundFailedException}. Un cargo ya reembolsado (respuesta anterior perdida) se da por hecho; cualquier
     * otro fallo es {@link PaymentUnavailableException}.
     */
    public void refund(String intentId, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", intentId);
        try {
            String status = text(post("/refunds", form, idempotencyKey), "status");
            if ("failed".equals(status) || "canceled".equals(status)) {
                log.warn("Stripe rechazó el reembolso del intento {} (estado {})", intentId, status);
                throw new RefundFailedException();
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 400 && e.getResponseBodyAsString().contains("charge_already_refunded")) {
                return;
            }
            throw unavailable("reembolsar", e);
        } catch (RestClientException e) {
            throw unavailable("reembolsar", e);
        }
    }

    private static boolean isUnusableReservation(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        return e.getStatusCode().value() == 400
                && (body.contains("payment_intent_unexpected_state") || body.contains("charge_expired_for_capture"));
    }

    private Map<?, ?> post(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        return client.post().uri(path)
                .header("Authorization", "Bearer " + properties.secretKey())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
    }

    private static boolean isCardRejection(HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        return status == 402 || (status == 400 && e.getResponseBodyAsString().contains("\"invalid_request_error\"")
                && e.getResponseBodyAsString().contains("payment_method"));
    }

    private static String errorCode(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        int index = body.indexOf("\"decline_code\"");
        if (index < 0) {
            index = body.indexOf("\"code\"");
        }
        if (index < 0) {
            return "card_declined";
        }
        int start = body.indexOf('"', body.indexOf(':', index) + 1);
        int end = start < 0 ? -1 : body.indexOf('"', start + 1);
        return start < 0 || end < 0 || end - start > 64 ? "card_declined" : body.substring(start + 1, end);
    }

    private static String text(Map<?, ?> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : value.toString();
    }

    /** No incluye la clave ni el cuerpo de la respuesta: solo la operación y el tipo de fallo. */
    private static PaymentUnavailableException unavailable(String operation, Exception cause) {
        String detail = cause instanceof HttpStatusCodeException status
                ? "estado " + status.getStatusCode().value() : cause.getClass().getSimpleName();
        log.warn("Stripe no pudo {} el pago ({})", operation, detail);
        return new PaymentUnavailableException();
    }
}
