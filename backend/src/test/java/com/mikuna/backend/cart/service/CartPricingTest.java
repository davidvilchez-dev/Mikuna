package com.mikuna.backend.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.cart.dto.TotalsResponse;
import com.mikuna.backend.common.config.DeliveryProperties;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RF-13: importes solo en el servidor, con una única regla de redondeo (HALF_UP, dos decimales). */
class CartPricingTest {

    private static CartPricing pricing(String fee, String rate) {
        return new CartPricing(new DeliveryProperties(List.of("Ayacucho"), new BigDecimal(fee), new BigDecimal(rate)));
    }

    private static List<BigDecimal> lines(String... totals) {
        return Arrays.stream(totals).map(BigDecimal::new).toList();
    }

    @Test
    void calculaSubtotalEnvioComisionYTotalConDireccion() {
        TotalsResponse totals = pricing("5.00", "5").compute(lines("37.00", "20.00"), true);

        assertThat(totals.subtotal()).isEqualByComparingTo("57.00");
        assertThat(totals.deliveryFee()).isEqualByComparingTo("5.00");
        assertThat(totals.commission()).isEqualByComparingTo("2.85");
        assertThat(totals.total()).isEqualByComparingTo("64.85");
        assertThat(totals.pending()).isNull();
    }

    @Test
    void sinDireccionSoloHaySubtotalYElRestoQuedaPendiente() {
        TotalsResponse totals = pricing("5.00", "5").compute(lines("37.00"), false);

        assertThat(totals.subtotal()).isEqualByComparingTo("37.00");
        assertThat(totals.deliveryFee()).isNull();
        assertThat(totals.commission()).isNull();
        assertThat(totals.total()).isNull();
        assertThat(totals.pending()).isEqualTo("ADDRESS");
    }

    @Test
    void sinLineasElegiblesTodoEsCero() {
        TotalsResponse totals = pricing("5.00", "5").compute(List.of(), true);

        assertThat(totals.subtotal()).isEqualByComparingTo("0.00");
        assertThat(totals.deliveryFee()).isEqualByComparingTo("0.00");
        assertThat(totals.commission()).isEqualByComparingTo("0.00");
        assertThat(totals.total()).isEqualByComparingTo("0.00");
        assertThat(totals.pending()).isEqualTo("EMPTY");
    }

    @Test
    void laComisionSeRedondeaUnaSolaVezMedioHaciaArriba() {
        // 33.33 x 5 % = 1.6665 -> 1.67; 10.10 x 5 % = 0.505 -> 0.51; 0.10 x 5 % = 0.005 -> 0.01
        assertThat(pricing("0", "5").compute(lines("33.33"), true).commission()).isEqualByComparingTo("1.67");
        assertThat(pricing("0", "5").compute(lines("10.10"), true).commission()).isEqualByComparingTo("0.51");
        assertThat(pricing("0", "5").compute(lines("0.10"), true).commission()).isEqualByComparingTo("0.01");
        assertThat(pricing("0", "5").compute(lines("0.09"), true).commission()).isEqualByComparingTo("0.00");
    }

    @Test
    void laComisionSeCalculaSobreElSubtotalYNoLineaPorLinea() {
        // por linea serian 0.01 x 3 = 0.03; sobre el subtotal 0.30 x 5 % = 0.015 -> 0.02
        TotalsResponse totals = pricing("0", "5").compute(lines("0.10", "0.10", "0.10"), true);

        assertThat(totals.commission()).isEqualByComparingTo("0.02");
    }

    @Test
    void elTotalEsLaSumaExactaDeSusComponentesRedondeados() {
        for (String subtotal : List.of("0.01", "9.99", "33.33", "123.45", "9999.99")) {
            TotalsResponse t = pricing("3.50", "7.5").compute(lines(subtotal), true);
            assertThat(t.total()).isEqualTo(t.subtotal().add(t.deliveryFee()).add(t.commission()));
        }
    }

    @Test
    void elTotalDeLineaEsExactoSinErroresDeComaFlotante() {
        assertThat(CartPricing.lineTotal(new BigDecimal("0.10"), 3)).isEqualTo(new BigDecimal("0.30"));
        assertThat(CartPricing.lineTotal(new BigDecimal("18.50"), 20)).isEqualTo(new BigDecimal("370.00"));
        assertThat(CartPricing.lineTotal(new BigDecimal("9999.99"), 1)).isEqualTo(new BigDecimal("9999.99"));
    }

    @Test
    void todosLosImportesSalenConEscalaDos() {
        TotalsResponse t = pricing("5", "5").compute(lines("10"), true);

        assertThat(t.subtotal().scale()).isEqualTo(2);
        assertThat(t.deliveryFee().scale()).isEqualTo(2);
        assertThat(t.commission().scale()).isEqualTo(2);
        assertThat(t.total().scale()).isEqualTo(2);
        assertThat(t.deliveryFee().toPlainString()).isEqualTo("5.00");
    }

    @Test
    void tarifaYComisionEnCeroSonValidas() {
        TotalsResponse t = pricing("0", "0").compute(lines("10.00"), true);

        assertThat(t.total()).isEqualByComparingTo("10.00");
        assertThat(t.deliveryFee()).isEqualByComparingTo("0.00");
        assertThat(t.commission()).isEqualByComparingTo("0.00");
    }
}
