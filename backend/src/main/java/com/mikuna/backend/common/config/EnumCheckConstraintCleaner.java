package com.mikuna.backend.common.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Elimina las restricciones CHECK que Hibernate genera para columnas {@code @Enumerated(STRING)}.
 *
 * <p>Con {@code ddl-auto=update} Hibernate crea el CHECK con los valores del enum al crear la tabla,
 * pero nunca lo amplía cuando el enum crece (p. ej. {@code pedidos_status_check} sin {@code ACCEPTED}).
 * Los valores ya se validan en la aplicación al mapear el enum, por lo que el CHECK es redundante y
 * solo provoca errores 23514. Se ejecuta tras la actualización del esquema, en cada arranque.
 */
@Component
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "update")
class EnumCheckConstraintCleaner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumCheckConstraintCleaner.class);

    private static final String FIND_SQL = """
            select c.conrelid::regclass::text as tabla, c.conname as restriccion
            from pg_constraint c
            join pg_namespace n on n.oid = c.connamespace
            where c.contype = 'c'
              and n.nspname = current_schema()
              and c.conname ~ '_(status|type|method|role)_check$'
            """;

    private final JdbcTemplate jdbc;

    EnumCheckConstraintCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String[]> found = jdbc.query(FIND_SQL, (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
            for (String[] row : found) {
                jdbc.execute("alter table " + row[0] + " drop constraint \"" + row[1].replace("\"", "\"\"") + "\"");
                log.info("Restricción CHECK de enum eliminada: {}.{}", row[0], row[1]);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron limpiar las restricciones CHECK de enums: {}", e.getMessage());
        }
    }
}
