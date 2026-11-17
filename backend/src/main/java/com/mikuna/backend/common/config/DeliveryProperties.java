package com.mikuna.backend.common.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Reglas de negocio de reparto acordadas con el usuario (spec 005): distritos cubiertos, tarifa fija de
 * envío en soles y comisión como porcentaje del subtotal. No tienen valores por defecto (constitución):
 * se leen de {@code backend/.env} y, si faltan o son inválidos, el arranque falla.
 */
@Validated
@ConfigurationProperties("mikuna.delivery")
public record DeliveryProperties(
        @NotEmpty(message = "mikuna.delivery.districts debe indicar al menos un distrito")
        List<@NotBlank(message = "mikuna.delivery.districts no admite distritos vacíos") String> districts,

        @NotNull(message = "mikuna.delivery.fee es obligatoria")
        @DecimalMin(value = "0", message = "mikuna.delivery.fee no puede ser negativa")
        @Digits(integer = 6, fraction = 2, message = "mikuna.delivery.fee admite hasta 2 decimales")
        BigDecimal fee,

        @NotNull(message = "mikuna.delivery.commission-rate es obligatoria")
        @DecimalMin(value = "0", message = "mikuna.delivery.commission-rate debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "mikuna.delivery.commission-rate debe estar entre 0 y 100")
        BigDecimal commissionRate) {
}
