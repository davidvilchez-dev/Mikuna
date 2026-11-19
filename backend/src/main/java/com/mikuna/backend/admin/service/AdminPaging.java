package com.mikuna.backend.admin.service;

import com.mikuna.backend.common.exception.RegistrationValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Validación común de página y filtros de los listados de administración (misma regla que la spec 010). */
public final class AdminPaging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private AdminPaging() {
    }

    /** {@code page} desde 0 (por defecto 0) y {@code size} de 1 a 100 (por defecto 20); si no, 400 con el campo. */
    public static Pageable pageable(Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw new RegistrationValidationException("page", "La página debe ser 0 o mayor");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new RegistrationValidationException("size", "El tamaño debe estar entre 1 y " + MAX_SIZE);
        }
        return PageRequest.of(pageNumber, pageSize);
    }

    /** Convierte un filtro opcional; vacío es «sin filtro» y un valor desconocido es 400 con el campo. */
    public static <E extends Enum<E>> E enumFilter(String field, String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ex) {
            throw new RegistrationValidationException(field, "Valor no válido");
        }
    }

    /** Patrón {@code like} en minúsculas con el carácter de escape {@code !}; sin texto coincide con todo. */
    public static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        String escaped = q.trim().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
