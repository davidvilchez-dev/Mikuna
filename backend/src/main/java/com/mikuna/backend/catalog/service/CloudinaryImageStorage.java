package com.mikuna.backend.catalog.service;

import com.mikuna.backend.common.exception.ImageUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Almacén de imágenes de productos en Cloudinary (RF-08) mediante su API REST firmada sobre HTTPS.
 * Se aísla de las reglas de negocio (constitución II) y las pruebas lo sustituyen por un mock.
 */
@Component
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryImageStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);
    private static final String BASE_URL = "https://api.cloudinary.com/v1_1";

    /** Imagen guardada: URL pública segura e identificador para borrarla después. */
    public record StoredImage(String secureUrl, String publicId) {
    }

    private final RestClient client;
    private final CloudinaryProperties properties;
    private final Clock clock;

    @Autowired
    public CloudinaryImageStorage(CloudinaryProperties properties, Clock clock) {
        this(RestClient.builder(), properties, clock);
    }

    /** Permite a las pruebas enlazar un servidor simulado al constructor del cliente. */
    CloudinaryImageStorage(RestClient.Builder builder, CloudinaryProperties properties, Clock clock) {
        this.client = builder.build();
        this.properties = properties;
        this.clock = clock;
    }

    public StoredImage upload(byte[] content, String filename) {
        if (!properties.isConfigured()) {
            throw new ImageUnavailableException();
        }
        String timestamp = timestamp();
        Map<String, String> signed = new java.util.TreeMap<>();
        signed.put("timestamp", timestamp);
        if (properties.folder() != null && !properties.folder().isBlank()) {
            signed.put("folder", properties.folder());
        }

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("api_key", properties.apiKey());
        signed.forEach(form::add);
        form.add("signature", sign(signed));

        try {
            Map<?, ?> response = client.post()
                    .uri(BASE_URL + "/" + properties.cloudName() + "/image/upload")
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object url = response == null ? null : response.get("secure_url");
            Object publicId = response == null ? null : response.get("public_id");
            if (!(url instanceof String secureUrl) || !(publicId instanceof String id)) {
                throw new ImageUnavailableException();
            }
            return new StoredImage(secureUrl, id);
        } catch (ImageUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException failure) {
            log.warn("No se pudo subir la imagen a Cloudinary: {}", failure.getClass().getSimpleName());
            throw new ImageUnavailableException();
        }
    }

    /** Borrado de mejor esfuerzo: un fallo solo se registra (la imagen queda huérfana, sin afectar al negocio). */
    public void delete(String publicId) {
        if (!properties.isConfigured() || publicId == null || publicId.isBlank()) {
            return;
        }
        String timestamp = timestamp();
        Map<String, String> signed = new java.util.TreeMap<>();
        signed.put("public_id", publicId);
        signed.put("timestamp", timestamp);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("api_key", properties.apiKey());
        signed.forEach(form::add);
        form.add("signature", sign(signed));
        try {
            client.post()
                    .uri(BASE_URL + "/" + properties.cloudName() + "/image/destroy")
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException failure) {
            log.warn("No se pudo borrar la imagen {} de Cloudinary: {}", publicId, failure.getClass().getSimpleName());
        }
    }

    private String timestamp() {
        return Long.toString(clock.instant().getEpochSecond());
    }

    /** SHA-1 hexadecimal de los parámetros firmables ordenados («a=1&b=2») seguidos del secreto. */
    String sign(Map<String, String> sortedParams) {
        StringBuilder toSign = new StringBuilder();
        sortedParams.forEach((key, value) -> {
            if (!toSign.isEmpty()) {
                toSign.append('&');
            }
            toSign.append(key).append('=').append(value);
        });
        toSign.append(properties.apiSecret());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(toSign.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
