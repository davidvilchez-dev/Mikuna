package com.mikuna.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila del listado administrativo de pedidos: estado, importe total, partes y estado del pago. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminOrderSummary(UUID id, String status, Instant createdAt, BigDecimal total, Party customer,
        Party restaurant, String paymentStatus) {

    /** Persona o negocio que participa en el pedido; el correo solo se informa para cuentas de acceso. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Party(UUID id, String name, String email) {
    }
}
