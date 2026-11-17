package com.mikuna.backend.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración de reparto compartida por los módulos {@code address} y {@code cart}. */
@Configuration
@EnableConfigurationProperties(DeliveryProperties.class)
public class DeliveryConfig {
}
