# Implementation Plan: Rendimiento, caché y pruebas de carga

**Branch**: sin rama nueva (`main`) | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/015-rendimiento-y-carga/spec.md`

## Summary

Las consultas públicas (búsqueda, filtros y catálogo) se sirven desde una caché de datos, no de respuestas:

- **Qué se guarda**: una instantánea de restaurantes visibles y la lista de productos disponibles de cada
  restaurante. El estado de atención se calcula en cada petición.
- **Proveedores**: Caffeine en memoria por defecto (la aplicación arranca como hoy) y Redis con el perfil
  `redis`.
- **Invalidación**: los descartes ocurren después del commit, con un segundo descarte diferido de 1 s; 10 min
  de vida como respaldo.
- **Fallas**: si Redis falla, se lee de la base de datos.
- **Contadores**: el freno del inicio de sesión y el límite del asistente pasan a contadores compartidos en
  Redis, con respaldo en memoria.
- **Base de datos**: se elimina el N+1 de la portada de respaldo y el pool queda configurable. El índice
  parcial previsto (V2) se midió y se descartó porque no mejoraba ningún plan (tasks T050).
- **Carga**: pruebas k6 de humo, carga (200 usuarios) y estrés, con semilla de volumen e informes
  reproducibles, antes y después de la caché.
- **Restricciones**: sin cambios en la API pública ni en el frontend; sin configuración de AWS, HTTPS ni TLS.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); k6 (JavaScript) para las pruebas de carga

**Primary Dependencies**:

- Existentes: Spring Web MVC, Spring Data JPA, Spring Security (JWT), Flyway y Actuator.
- Nuevas: `spring-boot-starter-cache`, Caffeine y `spring-boot-starter-data-redis` (Lettuce).

**Storage**:

- PostgreSQL. Sin migración nueva: la V2 de índices prevista se descartó tras medirla (tasks T050).
- Redis 8 opcional: contenedor en local, servicio en CI.

**Testing**:

- JUnit 5, Mockito y MockMvc contra `mikuna_test_db`.
- Las pruebas que requieren Redis se condicionan a `MIKUNA_TEST_REDIS=true`: siempre en CI, opcionales en
  local.
- k6 con umbrales.

**Target Platform**: backend local en 8080, y en 8081 para la prueba de dos instancias. Despliegue futuro en
AWS gratuito, solo como contexto.

**Project Type**: web application. Este incremento solo toca el backend y añade `load-tests/`.

**Performance Goals**: con 200 usuarios concurrentes, p95 < 2 s y errores < 1 % (SC-001); ≥ 80 % de aciertos
(SC-002); p95 del catálogo ≥ 50 % mejor que sin caché (SC-003).

**Constraints**:

- Arranque sin Redis ni Docker.
- Cambios visibles en < 1 s (SC-004).
- Ningún dato personal en la caché ni en las claves.
- Instancia de 1 GB en el despliegue: montón de unos 512 MB y Redis de 64 MB.

**Scale/Scope**:

- 4 clases nuevas de caché o contadores, 1 configuración y unas 8 clases modificadas.
- 1 migración y 1 carpeta `load-tests/` (3 escenarios, módulo común, semilla e informes).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
|-----------|--------------|--------|
| I. Monolito modular | Caché del catálogo en `catalog/service`; contadores en `common` (los usan `auth` y `assistant`); sin módulos ni servicios nuevos. La interfaz `SlidingWindowCounter` tiene dos implementaciones reales (research §5) | ✅ |
| II. Separación | Controladores intactos; caché e invalidación en servicios; Redis tras la abstracción de caché de Spring | ✅ |
| III. Seguridad | Sin TLS, certificados ni llaves locales; `/actuator/metrics` solo para ADMIN; claves de contador con hash, sin correos ni IP en claro; el pedido sigue validando contra la BD (FR-007) | ✅ |
| IV. Consistencia | Descarte después del commit, más uno diferido; el barrido de vencimientos ya bloquea la fila y ya tiene una prueba con dos barridos (research §6); "contar y agregar" atómico con Lua | ✅ |
| V. Pruebas | Integración de aciertos, estado en vivo, invalidación, carrera, Redis caído, contadores, barrido doble y métricas; ≥ 90 % de las clases nuevas; k6 permitido por la 6.0.0 | ✅ |
| VI. Trazabilidad | RF-09, RF-10, RNF-01, RNF-02, RNF-03, RNF-04 y RNF-07; RNF-01, 02 y 04 quedan **parciales** (solo local y con 200 usuarios); RNF-10 fuera | ✅ |
| VII. Incremental | Sin cambios de API ni de frontend; sin balanceador ni archivos de infraestructura; Redis opcional | ✅ |
| Aplazamientos 6.0.0 | Redis local opcional ✅; sin archivos ni carpetas de AWS ✅ (el arranque de Redis va como comando en quickstart); sin HTTPS ni TLS ✅ | ✅ |

Re-check tras el diseño: sin violaciones. Complejidad: ninguna que justificar.

## Project Structure

### Documentation (this feature)

```text
specs/015-rendimiento-y-carga/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/configuracion-y-metricas.md
├── checklists/requirements.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                            # + starter-cache, caffeine, starter-data-redis
└── src/
    ├── main/java/com/mikuna/backend/
    │   ├── common/cache/
    │   │   ├── CacheConfig.java                      # nuevo: Caffeine/Redis, TTL, estadísticas, CacheErrorHandler
    │   │   └── LoggingCacheErrorHandler.java         # nuevo: falla del almacén = ausencia (FR-006)
    │   ├── common/ratelimit/
    │   │   ├── SlidingWindowCounter.java             # nuevo: interfaz
    │   │   ├── InMemorySlidingWindowCounter.java     # nuevo: lógica actual extraída
    │   │   └── RedisSlidingWindowCounter.java        # nuevo: Lua + respaldo en memoria (perfil redis)
    │   ├── common/security/SecurityConfig.java       # + /actuator/metrics/** solo ADMIN
    │   ├── catalog/
    │   │   ├── dto/RestaurantSnapshot.java           # nuevo (+ RestaurantSnapshotList, PublicProductList)
    │   │   ├── repository/ProductRepository.java     # + portada de respaldo en una consulta (DISTINCT ON)
    │   │   └── service/
    │   │       ├── PublicCatalogCache.java           # nuevo: lectura/armado y descartes post-commit + diferido
    │   │       └── PublicCatalogService.java         # usa la instantánea; filtra, agrupa y pagina en memoria
    │   ├── catalog/service/ProductService.java       # + descartes
    │   ├── restaurant/service/
    │   │   ├── OrderEligibility.java                 # attentionStatus desde disponibilidad + horario
    │   │   ├── RestaurantService.java                # + descartes
    │   │   ├── RestaurantImageService.java           # + descartes
    │   │   └── RestaurantReviewService.java          # + descartes
    │   ├── admin/service/AdminAccountService.java    # + descartes
    │   ├── auth/service/LoginAttemptLimiter.java     # usa SlidingWindowCounter
    │   └── assistant/service/AssistantUsageLimiter.java  # usa SlidingWindowCounter
    ├── main/resources/
    │   ├── application.properties                    # Caffeine por defecto, TTL, Hikari, métricas, salud de Redis apagada
    │   ├── application-redis.properties              # nuevo: cache.type=redis, conexión por variables, tiempos de espera
    └── test/java/com/mikuna/backend/
        ├── catalog/PublicCatalogCacheIntegrationTest.java    # aciertos, estado en vivo, invalidación, carrera
        ├── catalog/PublicCatalogRedisDownIntegrationTest.java
        ├── common/ratelimit/*Test.java                       # memoria (unit) y Redis (condicionada)
        └── common/ActuatorMetricsSecurityIntegrationTest.java

load-tests/
├── README.md               # cómo correr y leer informes
├── lib/flows.js            # catálogo, login, pedido (efectivo), reparto en ronda de BASE_URLS
├── lib/report.js           # handleSummary → reports/*.md|json, aciertos desde métricas
├── smoke.js | load.js | stress.js
├── seed/seed.sql           # volumen para mikuna_load_db; aborta en mikuna_db
└── reports/                # informes versionados

.github/workflows/backend.yml   # + servicio redis y MIKUNA_TEST_REDIS=true
```

**Structure Decision**: aplicación web existente. Los cambios se concentran en `backend/`, y las pruebas de
carga van en `load-tests/` en la raíz, porque recorren todo el sistema y no pertenecen a un solo proyecto. No
se crea ninguna carpeta de infraestructura ni de despliegue.

## Complexity Tracking

Sin violaciones que justificar.
