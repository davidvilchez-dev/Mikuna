package com.mikuna.backend.order.service;

import com.mikuna.backend.order.dto.CheckoutResponse;
import com.mikuna.backend.order.dto.ConfirmOrderRequest;
import com.mikuna.backend.order.dto.OrderResponse;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Confirmación del pedido y consulta del resumen (RF-14, RF-15). No es transaccional: delega en
 * {@link CheckoutTransaction}, de modo que un duplicado de la clave de confirmación se resuelva aquí, fuera de
 * la transacción que PostgreSQL abortó, devolviendo el pedido ya registrado sin tocar la reserva.
 */
@Service
public class CheckoutService {

    private final CheckoutTransaction transaction;

    public CheckoutService(CheckoutTransaction transaction) {
        this.transaction = transaction;
    }

    public CheckoutResponse checkout(UUID customerId) {
        return transaction.summary(customerId);
    }

    public CheckoutTransaction.Confirmation confirm(UUID customerId, String confirmationKey,
            ConfirmOrderRequest request) {
        try {
            return transaction.confirm(customerId, confirmationKey, request);
        } catch (DataIntegrityViolationException e) {
            if (CheckoutTransaction.isDuplicateConfirmation(e)) {
                return transaction.findByKey(customerId, confirmationKey, request).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    public OrderResponse get(UUID customerId, UUID orderId) {
        return transaction.find(customerId, orderId);
    }
}
