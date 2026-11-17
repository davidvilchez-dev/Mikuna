package com.mikuna.backend.catalog.service;

import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import java.util.List;

/** Reglas de paginación compartidas por la gestión y la consulta pública del catálogo. */
final class Paging {

    static final int MAX_SIZE = 50;

    private Paging() {
    }

    /** {@code page} desde 0 y {@code size} entre 1 y {@value #MAX_SIZE}; si no, 400 con el campo. */
    static void validate(int page, int size) {
        if (page < 0) {
            throw new RegistrationValidationException("page", "La página debe ser 0 o mayor");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new RegistrationValidationException("size", "El tamaño debe estar entre 1 y " + MAX_SIZE);
        }
    }

    /** Pagina una lista ya filtrada y ordenada; una página fuera de rango queda vacía. */
    static <T> PageResponse<T> slice(List<T> all, int page, int size) {
        long from = (long) page * size;
        List<T> items = from >= all.size() ? List.of() : all.subList((int) from, (int) Math.min(from + size, all.size()));
        int totalPages = (int) Math.ceil(all.size() / (double) size);
        return new PageResponse<>(items, page, size, all.size(), totalPages);
    }
}
