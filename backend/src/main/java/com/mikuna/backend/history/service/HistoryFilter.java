package com.mikuna.backend.history.service;

import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.order.model.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Parámetros validados de un historial (FR-006, FR-007): página, tamaño, estado y rango de fechas sobre la fecha de
 * creación del pedido. El rango es inclusivo en ambos extremos, en la zona horaria de la plataforma.
 */
public record HistoryFilter(OrderStatus status, LocalDate from, LocalDate to, int page, int size, ZoneId zone) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final Instant MIN = Instant.EPOCH;
    private static final Instant MAX = Instant.parse("3000-01-01T00:00:00Z");

    public static HistoryFilter parse(String status, String from, String to, Integer page, Integer size,
            ZoneId zone) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw new RegistrationValidationException("page", "La página debe ser 0 o mayor");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new RegistrationValidationException("size", "El tamaño debe estar entre 1 y " + MAX_SIZE);
        }
        OrderStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                parsedStatus = OrderStatus.valueOf(status.trim());
            } catch (IllegalArgumentException ex) {
                throw new RegistrationValidationException("status", "Estado no válido");
            }
        }
        LocalDate fromDate = date("from", from);
        LocalDate toDate = date("to", to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new RegistrationValidationException("from", "La fecha inicial no puede ser posterior a la final");
        }
        return new HistoryFilter(parsedStatus, fromDate, toDate, pageNumber, pageSize, zone);
    }

    private static LocalDate date(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new RegistrationValidationException(field, "Usa el formato aaaa-mm-dd");
        }
    }

    /** Estados que admite la consulta: el filtro, si lo hay, o todos los de {@code allowed}. */
    public List<OrderStatus> statuses(List<OrderStatus> allowed) {
        if (status == null) {
            return allowed;
        }
        if (!allowed.contains(status)) {
            throw new RegistrationValidationException("status", "Estado no válido para este historial");
        }
        return List.of(status);
    }

    public static List<OrderStatus> allStatuses() {
        return Arrays.asList(OrderStatus.values());
    }

    /** Inicio inclusivo del rango. */
    public Instant fromInstant() {
        return from == null ? MIN : from.atStartOfDay(zone).toInstant();
    }

    /** Fin exclusivo del rango: el comienzo del día siguiente a la fecha final. */
    public Instant toInstant() {
        return to == null ? MAX : to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
