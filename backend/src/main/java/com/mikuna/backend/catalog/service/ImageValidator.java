package com.mikuna.backend.catalog.service;

import com.mikuna.backend.common.exception.ImageTooLargeException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Valida por contenido (firma del archivo) y tamaño las imágenes de producto: JPG, PNG o WebP, hasta 2 MB. */
@Component
public class ImageValidator {

    public static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String FIELD = "image";
    private static final String INVALID = "La imagen debe ser JPG, PNG o WebP";

    /** Devuelve el contenido si es válido; si no, lanza el error del campo {@code image}. */
    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RegistrationValidationException(FIELD, "La imagen es obligatoria");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ImageTooLargeException();
        }
        if (file.getContentType() == null || !TYPES.contains(file.getContentType().toLowerCase())) {
            throw new RegistrationValidationException(FIELD, INVALID);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException unreadable) {
            throw new RegistrationValidationException(FIELD, "No se pudo leer la imagen");
        }
        if (content.length > MAX_BYTES) {
            throw new ImageTooLargeException();
        }
        if (!isJpeg(content) && !isPng(content) && !isWebp(content)) {
            throw new RegistrationValidationException(FIELD, INVALID);
        }
        return content;
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        int[] signature = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((b[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
