package com.mikuna.backend.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * En pruebas, cada contexto parte de un esquema vacío y aplica todas las migraciones: así las pruebas validan las
 * migraciones reales (con {@code ddl-auto=validate}) y no dependen de lo que dejó una ejecución anterior. Solo
 * existe en el classpath de pruebas; requiere {@code spring.flyway.clean-disabled=false}.
 */
@Configuration(proxyBeanMethods = false)
public class FlywayTestConfig {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
