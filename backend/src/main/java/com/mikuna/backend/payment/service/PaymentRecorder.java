package com.mikuna.backend.payment.service;

import com.mikuna.backend.payment.model.PaymentTransaction;
import com.mikuna.backend.payment.model.TransactionStatus;
import com.mikuna.backend.payment.model.TransactionType;
import com.mikuna.backend.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra transacciones de pago en una transacción propia (RF-17): el registro de un fallo o de una
 * compensación debe persistir aunque la transacción principal se revierta (research §7).
 */
@Component
public class PaymentRecorder {

    private final PaymentTransactionRepository transactions;
    private final Clock clock;

    public PaymentRecorder(PaymentTransactionRepository transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID orderId, UUID customerId, String confirmationKey, TransactionType type,
            TransactionStatus status, BigDecimal amount, String resultCode, String resultMessage,
            String gatewayRef) {
        transactions.save(new PaymentTransaction(orderId, customerId, confirmationKey, type, status, amount,
                resultCode, resultMessage, gatewayRef, Instant.now(clock)));
    }
}
