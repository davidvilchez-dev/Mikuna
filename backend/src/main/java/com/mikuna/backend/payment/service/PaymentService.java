package com.mikuna.backend.payment.service;

import com.mikuna.backend.common.exception.PaymentDeclinedException;
import com.mikuna.backend.common.exception.PaymentStateException;
import com.mikuna.backend.common.exception.PaymentUnavailableException;
import com.mikuna.backend.common.exception.ReservationExpiredException;
import com.mikuna.backend.payment.model.Payment;
import com.mikuna.backend.payment.model.PaymentStatus;
import com.mikuna.backend.payment.model.PaymentTransaction;
import com.mikuna.backend.payment.model.TransactionStatus;
import com.mikuna.backend.payment.model.TransactionType;
import com.mikuna.backend.payment.repository.PaymentRepository;
import com.mikuna.backend.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida del pago de un pedido (RF-16, RF-17): autorizar (reservar) al confirmar, y capturar o liberar
 * cuando la spec 007 lo decida. Cada transacción queda registrada; nunca se guardan datos de tarjeta ni hay
 * reembolsos. Capturar y liberar bloquean la fila del pago, ocurren a lo sumo una vez y son excluyentes.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    static final String MSG_DECLINED = "Tu tarjeta fue rechazada. Revisa los datos o usa otra tarjeta";
    static final String MSG_ACTION_REQUIRED =
            "Tu tarjeta exige una verificación adicional que aún no admitimos. Usa otra tarjeta";

    private final StripeGateway gateway;
    private final StripeProperties properties;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final PaymentRecorder recorder;
    private final Clock clock;

    public PaymentService(StripeGateway gateway, StripeProperties properties, PaymentRepository payments,
            PaymentTransactionRepository transactions, PaymentRecorder recorder, Clock clock) {
        this.gateway = gateway;
        this.properties = properties;
        this.payments = payments;
        this.transactions = transactions;
        this.recorder = recorder;
        this.clock = clock;
    }

    /** Monto en céntimos, exacto: un importe con más de dos decimales es un error de programación. */
    static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    static String authorizationKey(UUID customerId, String confirmationKey) {
        return "confirm-" + customerId + "-" + confirmationKey;
    }

    /**
     * Reserva {@code amount} en el medio de pago. Devuelve el identificador del intento si quedó autorizado.
     * Un rechazo o una verificación adicional lanzan {@link PaymentDeclinedException}; una pasarela caída,
     * {@link PaymentUnavailableException}. Los fallos se registran en una transacción aparte. Ante un fallo
     * de conexión se reintenta una vez con la misma clave de idempotencia (research §6).
     */
    public String authorize(UUID customerId, UUID orderId, BigDecimal amount, String paymentMethodId,
            String confirmationKey) {
        String key = authorizationKey(customerId, confirmationKey);
        long cents = toCents(amount);
        StripeGateway.Authorization result;
        try {
            result = attempt(cents, paymentMethodId, key, orderId);
        } catch (PaymentUnavailableException first) {
            try {
                result = attempt(cents, paymentMethodId, key, orderId);
            } catch (PaymentUnavailableException second) {
                recordFailure(customerId, confirmationKey, amount, "gateway_unavailable", null);
                throw second;
            }
            if (result.outcome() == StripeGateway.Outcome.REQUIRES_CAPTURE) {
                // El primer intento sí llegó a Stripe: la confirmación se da por fallida y se libera.
                cancelQuietly(customerId, confirmationKey, amount, result.intentId());
                recordFailure(customerId, confirmationKey, amount, "gateway_unavailable", result.intentId());
                throw first;
            }
        }
        switch (result.outcome()) {
            case REQUIRES_CAPTURE:
                return result.intentId();
            case REQUIRES_ACTION:
                cancelQuietly(customerId, confirmationKey, amount, result.intentId());
                recordFailure(customerId, confirmationKey, amount, result.code(), result.intentId());
                throw new PaymentDeclinedException(PaymentDeclinedException.ACTION_REQUIRED, MSG_ACTION_REQUIRED);
            default:
                if (result.intentId() != null) {
                    cancelQuietly(customerId, confirmationKey, amount, result.intentId());
                }
                recordFailure(customerId, confirmationKey, amount, result.code(), result.intentId());
                throw new PaymentDeclinedException(PaymentDeclinedException.DECLINED, MSG_DECLINED);
        }
    }

    private StripeGateway.Authorization attempt(long cents, String paymentMethodId, String key, UUID orderId) {
        return gateway.authorize(cents, properties.currency(), paymentMethodId, key, orderId.toString());
    }

    private void recordFailure(UUID customerId, String confirmationKey, BigDecimal amount, String code,
            String gatewayRef) {
        recorder.record(null, customerId, confirmationKey, TransactionType.AUTHORIZATION,
                TransactionStatus.FAILED, amount, code, null, gatewayRef);
    }

    /** Guarda el pago autorizado y su transacción en la transacción del pedido (RF-17). */
    public void recordAuthorized(UUID customerId, UUID orderId, String confirmationKey, BigDecimal amount,
            String intentId) {
        Instant now = Instant.now(clock);
        payments.saveAndFlush(Payment.authorized(orderId, customerId, amount, properties.currency(), intentId, now));
        transactions.save(new PaymentTransaction(orderId, customerId, confirmationKey, TransactionType.AUTHORIZATION,
                TransactionStatus.AUTHORIZED, amount, null, null, intentId, now));
    }

    /**
     * Compensación: libera una reserva cuyo pedido no llegó a guardarse. Es de mejor esfuerzo y nunca lanza;
     * su resultado queda registrado en una transacción aparte. Sin pedido, no hay {@code order_id}.
     */
    public void cancelQuietly(UUID customerId, String confirmationKey, BigDecimal amount, String intentId) {
        try {
            gateway.cancel(intentId, "compensate-" + intentId);
            recorder.record(null, customerId, confirmationKey, TransactionType.RELEASE, TransactionStatus.RELEASED,
                    amount, "compensation", null, intentId);
        } catch (RuntimeException e) {
            log.error("No se pudo liberar la reserva {} tras un fallo de la confirmación", intentId);
            recorder.record(null, customerId, confirmationKey, TransactionType.RELEASE, TransactionStatus.FAILED,
                    amount, "compensation_failed", null, intentId);
        }
    }

    /**
     * Cobra por completo el monto reservado; solo desde {@code AUTHORIZED}. Si la reserva ya venció en la pasarela
     * no hay cobro: el pago queda {@code RELEASED} (código {@code reservation_expired}) y se devuelve
     * {@link CaptureResult#RESERVATION_EXPIRED} sin lanzar, para no revertir la transacción del llamador.
     */
    @Transactional
    public CaptureResult capture(UUID orderId) {
        return settle(orderId, TransactionType.CAPTURE);
    }

    /** Libera la reserva sin cobrar; solo desde {@code AUTHORIZED}. La invoca la spec 007. */
    @Transactional
    public void release(UUID orderId) {
        settle(orderId, TransactionType.RELEASE);
    }

    private CaptureResult settle(UUID orderId, TransactionType type) {
        Payment payment = payments.findByOrderIdForUpdate(orderId).orElseThrow(PaymentStateException::new);
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            // Ya cobrado o liberado: se rechaza sin llamar a la pasarela.
            throw new PaymentStateException();
        }
        boolean capture = type == TransactionType.CAPTURE;
        try {
            if (capture) {
                gateway.capture(payment.getGatewayRef(), "capture-" + orderId);
            } else {
                gateway.cancel(payment.getGatewayRef(), "release-" + orderId);
            }
        } catch (ReservationExpiredException e) {
            Instant expiredAt = Instant.now(clock);
            payment.release(expiredAt);
            payments.save(payment);
            transactions.save(new PaymentTransaction(orderId, payment.getCustomerId(), null, TransactionType.RELEASE,
                    TransactionStatus.RELEASED, payment.getAmount(), "reservation_expired", null,
                    payment.getGatewayRef(), expiredAt));
            return CaptureResult.RESERVATION_EXPIRED;
        } catch (PaymentUnavailableException e) {
            recorder.record(orderId, payment.getCustomerId(), null, type, TransactionStatus.FAILED,
                    payment.getAmount(), "gateway_unavailable", null, payment.getGatewayRef());
            throw e;
        }
        Instant now = Instant.now(clock);
        TransactionStatus done;
        if (capture) {
            payment.capture(now);
            done = TransactionStatus.CAPTURED;
        } else {
            payment.release(now);
            done = TransactionStatus.RELEASED;
        }
        payments.save(payment);
        transactions.save(new PaymentTransaction(orderId, payment.getCustomerId(), null, type, done,
                payment.getAmount(), null, null, payment.getGatewayRef(), now));
        return CaptureResult.CAPTURED;
    }
}
