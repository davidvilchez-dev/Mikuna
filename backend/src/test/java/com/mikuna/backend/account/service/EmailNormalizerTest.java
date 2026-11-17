package com.mikuna.backend.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    void recortaEspaciosYPasaAMinusculas() {
        assertThat(EmailNormalizer.normalize("  Ana.Quispe@Mail.COM ")).isEqualTo("ana.quispe@mail.com");
    }

    @Test
    void devuelveNullConNull() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }
}
