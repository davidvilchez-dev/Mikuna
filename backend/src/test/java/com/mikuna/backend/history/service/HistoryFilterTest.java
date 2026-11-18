package com.mikuna.backend.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.order.model.OrderStatus;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryFilterTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static HistoryFilter parse(String status, String from, String to, Integer page, Integer size) {
        return HistoryFilter.parse(status, from, to, page, size, LIMA);
    }

    @Test
    void sinParametrosUsaLosValoresPorDefecto() {
        HistoryFilter filter = parse(null, null, null, null, null);

        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(20);
        assertThat(filter.status()).isNull();
        assertThat(filter.statuses(HistoryFilter.allStatuses())).containsExactlyElementsOf(
                List.of(OrderStatus.values()));
        assertThat(filter.fromInstant()).isEqualTo(Instant.EPOCH);
        assertThat(filter.toInstant()).isAfter(Instant.parse("2999-01-01T00:00:00Z"));
    }

    @Test
    void parametrosVaciosSeTratanComoAusentes() {
        HistoryFilter filter = parse(" ", "", " ", null, null);

        assertThat(filter.status()).isNull();
        assertThat(filter.from()).isNull();
        assertThat(filter.to()).isNull();
    }

    @Test
    void rechazaTamanosYPaginasFueraDeRango() {
        assertThatThrownBy(() -> parse(null, null, null, null, 0)).isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> parse(null, null, null, null, 101)).isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> parse(null, null, null, -1, null)).isInstanceOf(RegistrationValidationException.class);
        assertThat(parse(null, null, null, 3, 100).size()).isEqualTo(100);
        assertThat(parse(null, null, null, 3, 1).size()).isEqualTo(1);
    }

    @Test
    void rechazaEstadoInvalidoYFechasMalFormadas() {
        assertThatThrownBy(() -> parse("EN_CAMINO", null, null, null, null))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> parse(null, "18/11/2026", null, null, null))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> parse(null, null, "2026-13-40", null, null))
                .isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void rechazaUnRangoInvertido() {
        assertThatThrownBy(() -> parse(null, "2026-11-18", "2026-11-17", null, null))
                .isInstanceOf(RegistrationValidationException.class).extracting("field").isEqualTo("from");
        assertThat(parse(null, "2026-11-18", "2026-11-18", null, null)).isNotNull();
    }

    @Test
    void elRangoEsInclusivoEnLaZonaDeLaPlataforma() {
        HistoryFilter filter = parse("DELIVERED", "2026-11-17", "2026-11-18", null, null);

        // Lima es UTC-5: el día 17 empieza a las 05:00Z y el rango termina al empezar el día 19 (05:00Z, exclusivo).
        assertThat(filter.fromInstant()).isEqualTo(Instant.parse("2026-11-17T05:00:00Z"));
        assertThat(filter.toInstant()).isEqualTo(Instant.parse("2026-11-19T05:00:00Z"));
        assertThat(filter.statuses(HistoryFilter.allStatuses())).containsExactly(OrderStatus.DELIVERED);
    }

    @Test
    void elFiltroDeEstadoDebeEstarEntreLosAdmitidos() {
        HistoryFilter pending = parse("PENDING", null, null, null, null);

        assertThatThrownBy(() -> pending.statuses(HistoryService.COURIER_STATUSES))
                .isInstanceOf(RegistrationValidationException.class);
        assertThat(parse("DELIVERED", null, null, null, null).statuses(HistoryService.COURIER_STATUSES))
                .containsExactly(OrderStatus.DELIVERED);
        assertThat(parse(null, null, null, null, null).statuses(HistoryService.COURIER_STATUSES))
                .containsExactlyElementsOf(HistoryService.COURIER_STATUSES);
    }
}
