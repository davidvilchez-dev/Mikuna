package com.mikuna.backend.restaurant.repository;

import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Lee la sucursal elegida por el titular en la petición actual (cabecera {@code X-Restaurant-Id}). */
final class BranchSelection {

    static final String HEADER = "X-Restaurant-Id";

    private BranchSelection() {
    }

    /** Id de la sucursal pedida, o {@code null} si no hay petición web o la cabecera falta o no es válida. */
    static UUID requested() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        String value = servlet.getRequest().getHeader(HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
