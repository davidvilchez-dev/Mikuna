package com.mikuna.backend.account.service;

import java.util.Locale;

/** Normaliza correos para unicidad y acceso: sin espacios exteriores y en minúsculas. */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
