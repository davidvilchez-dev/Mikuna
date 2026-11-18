package com.mikuna.backend.order.dto;

import com.mikuna.backend.order.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Confirmación del pedido (RF-14, RF-15). El cliente solo indica el método, el identificador de método de pago
 * creado por Stripe.js (nunca datos de tarjeta) y el total que vio; el total esperado solo se compara, el monto
 * reservado es siempre el recalculado en el servidor.
 */
public record ConfirmOrderRequest(
        @NotNull(message = "Elige un método de pago")
        PaymentMethod paymentMethod,

        @NotBlank(message = "Falta el método de pago de la tarjeta")
        @Size(max = 255, message = "Método de pago inválido")
        @Pattern(regexp = "^pm_[A-Za-z0-9_]+$", message = "Método de pago inválido")
        String paymentMethodId,

        @NotNull(message = "Falta el total esperado")
        @DecimalMin(value = "0", message = "El total esperado no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El total esperado admite hasta 2 decimales")
        BigDecimal expectedTotal) {
}
