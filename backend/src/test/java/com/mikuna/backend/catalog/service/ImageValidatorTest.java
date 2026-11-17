package com.mikuna.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikuna.backend.common.exception.ImageTooLargeException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.support.TestProducts;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Validación de imágenes por contenido y tamaño (JPG, PNG, WebP, hasta 2 MB). */
class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    private static MockMultipartFile file(String type, byte[] content) {
        return new MockMultipartFile("image", "foto", type, content);
    }

    private static byte[] sized(byte[] signature, int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(signature, 0, bytes, 0, Math.min(signature.length, size));
        return bytes;
    }

    @Test
    void aceptaJpegPngYWebpValidos() {
        assertThat(validator.validate(file("image/jpeg", TestProducts.JPEG))).isEqualTo(TestProducts.JPEG);
        assertThat(validator.validate(file("image/png", TestProducts.PNG))).isEqualTo(TestProducts.PNG);
        assertThat(validator.validate(file("image/webp", TestProducts.WEBP))).isEqualTo(TestProducts.WEBP);
        assertThat(validator.validate(file("IMAGE/JPEG", TestProducts.JPEG))).isNotEmpty();
    }

    @Test
    void rechazaUnArchivoCuyoContenidoNoEsUnaImagenAunqueDigaSerlo() {
        byte[] pdf = "%PDF-1.7 contenido".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> validator.validate(file("image/jpeg", pdf)))
                .isInstanceOfSatisfying(RegistrationValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("image"));
        assertThatThrownBy(() -> validator.validate(file("image/png", new byte[] {(byte) 0x89, 'P'})))
                .isInstanceOf(RegistrationValidationException.class);
        byte[] riffSinWebp = "RIFF....WAVE".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> validator.validate(file("image/webp", riffSinWebp)))
                .isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void rechazaTiposDeclaradosNoAdmitidos() {
        assertThatThrownBy(() -> validator.validate(file("image/gif", TestProducts.JPEG)))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> validator.validate(file("application/pdf", TestProducts.JPEG)))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> validator.validate(file(null, TestProducts.JPEG)))
                .isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void rechazaArchivoVacioOAusente() {
        assertThatThrownBy(() -> validator.validate(file("image/jpeg", new byte[0])))
                .isInstanceOfSatisfying(RegistrationValidationException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo("La imagen es obligatoria"));
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void ellimiteDeDosMegabytesEsExacto() {
        int limit = 2 * 1024 * 1024;
        assertThat(validator.validate(file("image/jpeg", sized(TestProducts.JPEG, limit)))).hasSize(limit);
        assertThatThrownBy(() -> validator.validate(file("image/jpeg", sized(TestProducts.JPEG, limit + 1))))
                .isInstanceOf(ImageTooLargeException.class);
    }
}
