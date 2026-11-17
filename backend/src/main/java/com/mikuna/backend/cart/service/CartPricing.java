package com.mikuna.backend.cart.service;

import com.mikuna.backend.cart.dto.TotalsResponse;
import com.mikuna.backend.common.config.DeliveryProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RF-13: cálculo de importes, solo en el servidor y solo con {@link BigDecimal}. La regla de redondeo es
 * única: el total de cada línea es exacto (precio × cantidad), la comisión se redondea una sola vez al
 * céntimo (medio hacia arriba) y el total suma componentes ya redondeados. La confirmación del pedido
 * (spec 006) debe volver a invocarlo; no se confía en importes mostrados antes.
 */
@Component
public class CartPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final DeliveryProperties properties;

    public CartPricing(DeliveryProperties properties) {
        this.properties = properties;
    }

    /** Total exacto de una línea: precio de dos decimales × cantidad entera. */
    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, ROUNDING);
    }

    /**
     * @param eligibleLineTotals totales de las líneas elegibles (las no elegibles no se incluyen)
     * @param hasValidAddress si hay una dirección seleccionada dentro de la zona cubierta
     */
    public TotalsResponse compute(List<BigDecimal> eligibleLineTotals, boolean hasValidAddress) {
        if (eligibleLineTotals.isEmpty()) {
            return new TotalsResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    TotalsResponse.PENDING_EMPTY);
        }
        BigDecimal subtotal = eligibleLineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, ROUNDING);
        if (!hasValidAddress) {
            return new TotalsResponse(subtotal, null, null, null, TotalsResponse.PENDING_ADDRESS);
        }
        BigDecimal fee = properties.fee().setScale(2, ROUNDING);
        BigDecimal commission = subtotal.multiply(properties.commissionRate()).divide(HUNDRED, 2, ROUNDING);
        BigDecimal total = subtotal.add(fee).add(commission);
        return new TotalsResponse(subtotal, fee, commission, total, null);
    }
}
