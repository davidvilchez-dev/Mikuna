package com.mikuna.backend.order.dto;

import com.mikuna.backend.order.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Confirmación del pedido (RF-14, RF-15). El cliente solo indica el método, el identificador de método de pago
 * creado por Stripe.js (nunca datos de tarjeta) y el total que vio; el total esperado solo se compara, el monto
 * reservado es siempre el recalculado en el servidor.
 */
public record ConfirmOrderRequest(
        @NotNull(message = "Elige un método de pago")
        PaymentMethod paymentMethod,

        @Size(max = 255, message = "Método de pago inválido")
        @Pattern(regexp = "^pm_[A-Za-z0-9_]+$", message = "Método de pago inválido")
        String paymentMethodId,

        @NotNull(message = "Falta el total esperado")
        @DecimalMin(value = "0", message = "El total esperado no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El total esperado admite hasta 2 decimales")
        BigDecimal expectedTotal,

        @Size(max = 300, message = "La nota para la tienda admite hasta 300 caracteres")
        String storeNote,

        /** Hora de entrega elegida; nula es entrega inmediata. */
        Instant scheduledFor,

        /** Datos de quien recibe cuando no es el cliente; nulo es "yo mismo". */
        @Valid
        Receiver receiver) {

    public record Receiver(
            @NotBlank(message = "Indica el nombre de quien recibe")
            @Size(max = 80, message = "El nombre admite hasta 80 caracteres")
            String name,

            @NotBlank(message = "Indica el teléfono de quien recibe")
            @Pattern(regexp = "^[0-9+ ()-]{6,20}$", message = "Teléfono inválido")
            String phone,

            @Size(max = 300, message = "La nota admite hasta 300 caracteres")
            String note) {
    }
}
