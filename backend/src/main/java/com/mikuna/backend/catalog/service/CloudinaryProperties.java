package com.mikuna.backend.catalog.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales y carpeta de Cloudinary. Las claves {@code cloudinary.cloud-name}, {@code cloudinary.api-key}
 * y {@code cloudinary.api-secret} se leen de {@code backend/.env} y nunca se registran ni se versionan.
 */
@ConfigurationProperties("cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret, String folder) {

    public boolean isConfigured() {
        return notBlank(cloudName) && notBlank(apiKey) && notBlank(apiSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
