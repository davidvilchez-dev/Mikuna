# Tasks: Rendimiento, caché y pruebas de carga (015)

**Input**: Design documents from `specs/015-rendimiento-y-carga/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/configuracion-y-metricas.md, quickstart.md

**Tests**: incluidos. La constitución (principio V) exige pruebas de integración de las reglas y carreras
introducidas, y la spec define escenarios verificables (aciertos, invalidación, caída del almacén, contadores).

**Organization**: tareas agrupadas por historia de usuario; rutas relativas a la raíz del repositorio.
Abreviaturas:

- `B/` = `backend/src/main/java/com/mikuna/backend/`
- `BT/` = `backend/src/test/java/com/mikuna/backend/`
- `R/` = `backend/src/main/resources/`
- `L/` = `load-tests/`

**Restricciones del usuario (enmienda 6.0.0)**:

- Nada de HTTPS, SSL, TLS, certificados ni llaves privadas en local.
- Ningún archivo ni carpeta de AWS o de infraestructura.
- La aplicación DEBE arrancar sin Redis ni Docker.

## Phase 1: Setup

- [X] T001 Añadir `spring-boot-starter-cache`, `com.github.ben-manes.caffeine:caffeine` (versión gestionada por Boot) y `spring-boot-starter-data-redis` a `backend/pom.xml`
- [X] T002 En `R/application.properties`:
  - caché `spring.cache.type=caffeine`;
  - `mikuna.cache.ttl=${MIKUNA_CACHE_TTL:PT10M}`;
  - Hikari `spring.datasource.hikari.maximum-pool-size=${DB_POOL_SIZE:10}`, `minimum-idle=${DB_POOL_MIN_IDLE:2}` y `connection-timeout=5000`;
  - `management.endpoints.web.exposure.include=health,metrics`;
  - `management.health.redis.enabled=false`.

  Comentario: Redis solo con el perfil `redis`.
- [X] T003 [P] Crear `R/application-redis.properties`:
  - `spring.cache.type=redis`;
  - `spring.data.redis.host=${REDIS_HOST:localhost}`, `port=${REDIS_PORT:6379}` y `password=${REDIS_PASSWORD:}`;
  - `spring.data.redis.timeout=250ms` y `connect-timeout=500ms`;
  - `management.health.redis.enabled=true`.

  Sin TLS.
- [X] T004 [P] En `.github/workflows/backend.yml`: añadir el servicio `redis` (`redis:8-alpine`, puerto 6379, health-cmd `redis-cli ping`) y `MIKUNA_TEST_REDIS: 'true'` en `env`

---

## Phase 2: Foundational (bloquea todas las historias)

- [X] T005 Crear `B/common/cache/CacheNames.java` con las constantes `PUBLIC_RESTAURANTS = "public-restaurants"` y `PUBLIC_CATALOG = "public-catalog"`
- [X] T006 Crear `B/common/cache/LoggingCacheErrorHandler.java` (implementa `CacheErrorHandler`): en `handleCacheGetError`, `Put`, `Evict` y `Clear` registra `warn` con el nombre de la caché y la clave y **no** relanza (FR-006, research §4)
- [X] T007 Crear `B/common/cache/CacheConfig.java` (`@EnableCaching`, implementa `CachingConfigurer` con `errorHandler()` = T006):
  - Bean `CaffeineCacheManager` (solo si `spring.cache.type=caffeine`) con `expireAfterWrite(mikuna.cache.ttl)`, `maximumSize(1000)` y `recordStats()`.
  - Con `spring.cache.type=redis`, `RedisCacheManagerBuilderCustomizer` con TTL `mikuna.cache.ttl`, `enableStatistics()`, prefijo `mikuna:cache:` y un serializador JSON **tipado por caché** (`RestaurantSnapshotList` para `public-restaurants`, `PublicProductList` para `public-catalog`), sin tipado polimórfico (research §2).
  - `spring.cache.type=none` debe seguir funcionando (SC-003).
- [X] T008 En `BT/support/IntegrationTestSupport.java`: limpiar todas las cachés del `CacheManager` en el `@BeforeEach` existente (`cleanDatabase`), para que una entrada de una prueba no se filtre a otra. Hacer lo mismo al final de los ayudantes que cambian la visibilidad directo en la base de datos sin pasar por los servicios (p. ej. `disable(UUID)`).
- [X] T009 Ejecutar `.\mvnw.cmd verify` en `backend/` sin perfil y confirmar que la suite actual sigue en verde con la caché habilitada pero aún sin uso

**Checkpoint**: la app arranca como antes; la infraestructura de caché está lista y apagable.

---

## Phase 3: User Story 1 - El visitante navega el catálogo rápido (Priority: P1) 🎯 MVP

**Goal**: búsqueda, filtros y catálogo se sirven desde la caché de datos, con el estado de atención calculado
en cada petición y respuestas idénticas a las actuales.

**Independent Test**: consultar dos veces la lista y un catálogo; la segunda vez no hay consultas a la base de
datos y el contenido es igual. Con reloj a las 17:59 y luego a las 18:01, el estado pasa de `OPEN` a
`OUTSIDE_HOURS` sin descartar nada.

### Tests for User Story 1

- [X] T010 [P] [US1] Crear `BT/catalog/PublicCatalogCacheIntegrationTest.java`, extendiendo `IntegrationTestSupport`, con:
  - (a) segunda búsqueda, filtros y catálogo sin llamar a `RestaurantRepository.findVisible` ni a `ProductRepository` (`@MockitoSpyBean` y `verify` del número de llamadas), con un JSON idéntico;
  - (b) restaurante 09:00–18:00 con reloj fijo a las 17:59 → `OPEN`; se avanza el reloj a las 18:01 sin cambiar datos → `OUTSIDE_HOURS`;
  - (c) las búsquedas con textos distintos no crean entradas nuevas en `public-restaurants` (una sola clave);
  - (d) el catálogo de un restaurante que no está en la instantánea da 404 aunque su lista esté guardada.
  - *Hecho como* `BT/catalog/PublicCatalogCacheTest.java`: prueba de Spring acotada (proxy de caché real con Caffeine, repositorios simulados y reloj movible). Contar lecturas y mover la hora sin base de datos es más preciso que espiar un contexto completo.
- [X] T011 [P] [US1] Crear `BT/catalog/PublicCatalogRedisDownIntegrationTest.java` con `@ActiveProfiles("redis")` y `spring.data.redis.port` apuntando a un puerto sin servicio: búsqueda, filtros y catálogo responden `200` con los datos de la base de datos (FR-006, SC-005). No requiere Redis, así que corre siempre.

### Implementation for User Story 1

- [X] T012 [P] [US1] Crear `B/catalog/dto/RestaurantSnapshot.java`, un record con:
  - `id`, `ownerId`, `name`, `category`, `description`, `zone`;
  - `logoUrl`, `coverUrl`, `fallbackCoverUrl`;
  - `latitude`, `longitude`, `submittedAt`, `available`;
  - `List<ScheduleSlot> schedule`, con `ScheduleSlot(DayOfWeek day, LocalTime opensAt, LocalTime closesAt)`.

  Más el envoltorio `RestaurantSnapshotList(List<RestaurantSnapshot> items)`. No incluir dirección, teléfono, estado de aprobación ni datos de cuenta (data-model.md).
  - *Ajuste*: `ScheduleSlot` vive en `B/restaurant/model/ScheduleSlot.java` (no en `catalog.dto`), para no crear un ciclo `restaurant → catalog`; `OpeningHour.contains` delega en él.
- [X] T013 [P] [US1] Crear `B/catalog/dto/PublicProductList.java` (`record PublicProductList(List<PublicProductResponse> items)`)
- [X] T014 [US1] En `B/restaurant/service/OrderEligibility.java`: añadir `attentionStatus(boolean available, List<ScheduleSlot> schedule)` con la misma regla (pausado → `PAUSED`; franja del día actual que contiene la hora → `OPEN`; si no, `OUTSIDE_HOURS`). Hacer que `attentionStatus(Restaurant)` delegue en ella; reutilizar `contains` del horario existente para no cambiar el cálculo de bordes ni de medianoche.
- [X] T015 [US1] En `B/catalog/repository/ProductRepository.java`: añadir una consulta nativa `firstAvailableImageByRestaurant(Collection<UUID> ids)` con `SELECT DISTINCT ON (restaurant_id) restaurant_id, image_url ... WHERE active AND available ORDER BY restaurant_id, category, name`, que devuelva pares id → url (proyección). Es el mismo criterio que `findFirstBy...OrderByCategoryAscNameAsc`.
- [X] T016 [US1] Crear `B/catalog/service/PublicCatalogCache.java` con dos métodos:
  - `restaurants()`: `@Cacheable(PUBLIC_RESTAURANTS, key = "'all'")` → `findVisible()` + T015 → `RestaurantSnapshotList`.
  - `products(UUID restaurantId)`: `@Cacheable(PUBLIC_CATALOG, key = "#restaurantId")` → todos los productos activos y disponibles ordenados por `category, name` → `PublicProductList`.

  Ambos en una transacción de solo lectura. Va en un bean separado de `PublicCatalogService` para que el proxy de caché funcione.
- [X] T017 [US1] Reescribir `B/catalog/service/PublicCatalogService.java` sobre `PublicCatalogCache`:
  - `search` filtra, agrupa por `ownerId`, ordena y calcula la distancia sobre la instantánea, y el estado con T014, en cada petición.
  - `filters` sale de la instantánea.
  - `catalog` busca el restaurante en la instantánea (404 si no está) y pagina `products(id)` en memoria con `Paging`.
  - Se conservan exactamente las reglas, el orden, `branchCount`, `distanceKm` y las validaciones actuales.
  - Actualizar el Javadoc: ya no lee la base de datos en cada consulta (FR-014).
- [X] T018 [US1] Adaptar `B/catalog/dto/PublicRestaurantResponse.java` con una fábrica `from(RestaurantSnapshot, AttentionStatus, Double distanceKm, int branchCount)`, que use `fallbackCoverUrl` cuando `coverUrl` es nulo. Retirar la fábrica basada en la entidad si queda sin uso.
- [X] T019 [US1] Ejecutar `BT/catalog/PublicCatalogIntegrationTest.java`, `BT/catalog/PublicSearchIntegrationTest.java`, T010 y T011, y corregir hasta tenerlos en verde. Las pruebas existentes no deben cambiar sus expectativas; solo se permiten ajustes de preparación por la caché.

**Checkpoint**: US1 funciona con Caffeine; los mismos resultados con menos consultas.

---

## Phase 4: User Story 2 - Los cambios se ven de inmediato (Priority: P1)

**Goal**: toda escritura que afecta lo público descarta la entrada correspondiente después del commit, y otra
vez 1 s después.

**Independent Test**: consultar un catálogo, cambiar el precio de un plato por la API del restaurante y volver
a consultar: aparece el precio nuevo. Repetir con cada operación de research §3.

### Tests for User Story 2

- [X] T020 [P] [US2] Crear `BT/catalog/PublicCatalogInvalidationIntegrationTest.java`. Para cada operación hay que consultar antes para llenar la caché, ejecutar la operación por su endpoint real y comprobar que la siguiente consulta pública refleja el cambio:
  - producto: crear, editar precio, cambiar disponibilidad, retirar y copiar menú (catálogo y portada de respaldo);
  - restaurante: editar datos, horario, disponibilidad e imágenes (con el doble de Cloudinary existente);
  - *Nota*: copiar menú publica el mismo evento (T023), pero no tiene prueba de integración propia, porque exige dos sucursales del mismo titular.
  - administrador: aprobar, rechazar, deshabilitar o habilitar la cuenta del titular y suspender o reactivar la operación (desaparece o aparece en la lista; catálogo 404 o 200).
- [X] T021 [P] [US2] Crear `BT/catalog/PublicCatalogCacheRaceTest.java`, una prueba unitaria con `CaffeineCacheManager` y un reloj o ejecutor controlables. Una lectura empieza antes del commit y guarda el valor viejo después del descarte post-commit; el descarte diferido de 1 s lo elimina. Se verifica con un `ScheduledExecutorService` falso, sin dormir.

### Implementation for User Story 2

- [X] T022 [US2] En `B/catalog/service/PublicCatalogCache.java`: añadir `evictRestaurants()` y `evictCatalog(UUID restaurantId)`, que registran el descarte con `TransactionSynchronizationManager.registerSynchronization(afterCommit)` si hay una transacción activa (si no, lo hacen en el acto) y programan un segundo descarte a 1 s en un `ScheduledExecutorService` de un hilo, inyectable para T021 (research §3)
  - *Ajuste de implementación*: en lugar de llamadas directas, los servicios publican `B/common/cache/PublicCatalogChanged` y `PublicCatalogCache.on(...)` lo escucha con `@TransactionalEventListener(fallbackExecution = true)` (después del commit). Evita el ciclo `restaurant/admin ↔ catalog` (constitución I). T023–T027 publican el evento en vez de llamar a `evict*`.
- [X] T023 [P] [US2] En `B/catalog/service/ProductService.java`: llamar `evictCatalog(restaurantId)` y `evictRestaurants()` en `create`, `update`, `setAvailability`, `withdraw` y `copyMenu` (en el restaurante destino)
- [X] T024 [P] [US2] En `B/restaurant/service/RestaurantService.java`: llamar `evictRestaurants()` en `create`, `addBranch`, `updateMine`, `resubmit`, `updateSchedule` y `setAvailability`
- [X] T025 [P] [US2] En `B/restaurant/service/RestaurantImageService.java`: llamar `evictRestaurants()` en `update`
- [X] T026 [P] [US2] En `B/restaurant/service/RestaurantReviewService.java`: llamar `evictRestaurants()` en `approve` y `reject`
- [X] T027 [P] [US2] En `B/admin/service/AdminAccountService.java`: llamar `evictRestaurants()` en los cambios de estado de cuenta (habilitar o deshabilitar) y de habilitación de operación
- [X] T028 [US2] Ejecutar T020, T021 y la suite de `BT/catalog`, `BT/restaurant` y `BT/admin` en verde

**Checkpoint**: US1 + US2 cumplen SC-004 con Caffeine.

---

## Phase 5: User Story 3 - Varias instancias coherentes (Priority: P2)

**Goal**: el freno del login y el límite del asistente se comparten en Redis con el perfil `redis` y vuelven a
la memoria si Redis falla. Catálogo coherente entre instancias.

**Independent Test**: dos contadores Redis (como dos instancias) sobre la misma clave alcanzan el bloqueo con
el mismo total de intentos que uno solo.

### Tests for User Story 3

- [X] T029 [P] [US3] Crear `BT/common/ratelimit/InMemorySlidingWindowCounterTest.java`, con unidad y reloj fijo: ventana deslizante, máximo, segundos de espera (mismo cálculo que hoy) y `clear(key)`
- [X] T030 [P] [US3] Crear `BT/common/ratelimit/RedisSlidingWindowCounterIntegrationTest.java` con `@EnabledIfEnvironmentVariable(named = "MIKUNA_TEST_REDIS", matches = "true")`. Dos instancias de `RedisSlidingWindowCounter` sobre el mismo Redis:
  - (a) los fallos repartidos llegan al límite combinado;
  - (b) `tryAcquire` concurrente desde 20 hilos no supera el máximo (Lua atómico);
  - (c) la clave expira con la ventana.
- [X] T031 [P] [US3] Crear `BT/common/ratelimit/RedisSlidingWindowCounterFallbackTest.java`: con una conexión a un puerto sin servicio, el contador sigue limitando en memoria (FR-010a) y registra la falla. Corre siempre.
- [X] T032 [P] [US3] Crear `BT/catalog/PublicCatalogRedisIntegrationTest.java` con `@EnabledIfEnvironmentVariable(MIKUNA_TEST_REDIS)` y `@ActiveProfiles("redis")`:
  - acierto y descarte con Redis real;
  - la serialización redonda de `RestaurantSnapshotList` y `PublicProductList` (incluye `BigDecimal`, `Instant` y `LocalTime`);
  - un segundo `RedisCacheManager` (otra "instancia") ve el descarte.

### Implementation for User Story 3

- [X] T033 [US3] Crear `B/common/ratelimit/SlidingWindowCounter.java`, una interfaz con:
  - `long waitSeconds(String key, int max, Duration window)` (0 si puede pasar);
  - `void record(String key, Duration window)`;
  - `long tryAcquire(String key, int max, Duration window)`: registra y devuelve 0, o devuelve la espera sin registrar;
  - `void clear(String key)`;
  - `void clearAll()` (solo pruebas).
- [X] T034 [US3] Crear `B/common/ratelimit/InMemorySlidingWindowCounter.java` moviendo la lógica actual de `ConcurrentHashMap<String, Deque<Instant>>`, la poda y la purga de `LoginAttemptLimiter` y `AssistantUsageLimiter`, sin cambiar el cálculo de espera
- [X] T035 [US3] Crear `B/common/ratelimit/RedisSlidingWindowCounter.java` (`@Profile("redis")`, `@Primary`):
  - Usa `StringRedisTemplate` con un ZSET por clave. Un script Lua para `tryAcquire` y `waitSeconds`: `ZREMRANGEBYSCORE`, `ZCARD`, `ZADD` y `PEXPIRE` de la ventana. `record` usa `ZADD` + `PEXPIRE`, y `clear` usa `DEL`.
  - El tiempo sale del `Clock` de la aplicación y no del reloj de Redis, para que las pruebas con reloj fijo sigan funcionando.
  - Ante cualquier `DataAccessException` o error de conexión, delega en un `InMemorySlidingWindowCounter` interno y registra `warn` como máximo una vez por minuto.
- [X] T036 [US3] Registrar `InMemorySlidingWindowCounter` como bean por defecto en `B/common/ratelimit/RateLimitConfig.java` (`@ConditionalOnMissingBean` o `@Profile("!redis")`)
- [X] T037 [US3] En `B/auth/service/LoginAttemptLimiter.java`: usar `SlidingWindowCounter` con las claves `login:acct:<sha256(correo normalizado)>` y `login:ip:<sha256(ip)>` (`HexFormat` + `MessageDigest`) bajo el prefijo `mikuna:` (data-model.md). Se mantienen la semántica de `check`, `recordFailure` y `recordSuccess`, y `reset()` delega en `clearAll()`. Actualizar el Javadoc ("compartido entre instancias con el perfil redis").
- [X] T038 [US3] En `B/assistant/service/AssistantUsageLimiter.java`: `acquire` usa `tryAcquire("assistant:" + accountId, rateLimitPerMinute, WINDOW)` y lanza `AssistantRateLimitedException(wait)` si `wait > 0`. Actualizar el Javadoc.
- [X] T039 [US3] Ejecutar `BT/assistant/AssistantUsageLimiterTest.java`, `BT/auth/LoginIntegrationTest.java` y T029–T032. Confirmar que FR-010 sigue cubierto por `BT/order/PendingOrderExpiryConcurrencyIntegrationTest.java` (`dosBarridosALaVezVencenUnaSolaVez...`), sin código nuevo (research §6).

**Checkpoint**: con el perfil `redis` los límites son globales; sin él, nada cambia.

---

## Phase 6: User Story 4 - Rendimiento medido con k6 (Priority: P2)

**Goal**: semilla de volumen, escenarios de humo, carga y estrés con umbrales, informes reproducibles y
medición de aciertos; revisión de índices con datos de volumen.

**Independent Test**: `k6 run smoke.js` pasa contra `mikuna_load_db` y deja un informe en `L/reports/`.

### Tests for User Story 4

- [X] T040 [P] [US4] Crear `BT/common/ActuatorMetricsSecurityIntegrationTest.java`: `GET /actuator/metrics/cache.gets` da 401 sin token, 403 con un cliente y 200 con ADMIN (con la medida `cache.gets` presente tras una consulta pública); `/actuator/health` sigue siendo público

### Implementation for User Story 4

- [X] T041 [US4] En `B/common/security/SecurityConfig.java`: añadir `.requestMatchers("/actuator/metrics/**").hasRole("ADMIN")` antes de `anyRequest()`, con un comentario (contrato)
- [X] T042 [P] [US4] Crear `L/seed/seed.sql` para `mikuna_load_db`:
  - Aborta con `RAISE EXCEPTION` si `current_database() = 'mikuna_db'`.
  - Es idempotente: borra solo lo que lleva el dominio `@mikuna.test` o el prefijo `Carga`.
  - Con `generate_series` inserta:
    - 200 titulares RESTAURANT y 200 restaurantes `APPROVED` (20 zonas de `MIKUNA_DELIVERY_DISTRICTS` y 8 categorías), cada uno con horario de lunes a domingo de 00:00 a 23:59 y 30 productos activos y disponibles;
    - 300 clientes `carga<n>@mikuna.test`, cada uno con una dirección en un distrito cubierto;
    - 1 ADMIN `admin-carga@mikuna.test`.
  - Todas las cuentas usan el mismo hash BCrypt precalculado de la contraseña documentada en `L/README.md`.
  - Las columnas salen de `R/db/migration/V1__esquema_inicial.sql`.
- [X] T043 [P] [US4] Crear `L/lib/flows.js`:
  - `baseUrl()` reparte en ronda la lista `BASE_URLS` (por defecto `http://localhost:8080`).
  - `catalogo()`: lista sin filtros, lista con una categoría o zona, `filters` y el catálogo de un restaurante al azar.
  - `login(email)`.
  - `pedido(token)`: `DELETE /api/customer/cart`, `POST /cart/lines` con un producto al azar, `PUT /cart/address` con la dirección del cliente (`GET /api/customer/addresses`), `GET /api/customer/checkout` y `POST /api/customer/orders` con `Idempotency-Key` UUID, `paymentMethod: "CASH"` y `expectedTotal` del checkout (FR-017).
  - Cada petición lleva la etiqueta `flow` y se valida con `check`.
- [X] T044 [P] [US4] Crear `L/lib/report.js`:
  - `setup()` lee `cache.gets` hit y miss por caché con un token de `ADMIN_EMAIL`/`ADMIN_PASSWORD` (si faltan, se omite) y `teardown()` repite la lectura.
  - `handleSummary(data)` escribe `L/reports/<AAAAMMDD-HHMM>-<escenario>-<CACHE_LABEL>-<n>inst.md` y `.json` con:
    - commit (`GIT_SHA` o `unknown`), escenario, usuarios máximos, duración, `BASE_URLS` y conjunto de datos;
    - p50, p95 y p99, y la tasa de errores por `flow`;
    - cumplimiento de umbrales y la proporción de aciertos (data-model.md).
  - Además imprime el resumen estándar.
- [X] T045 [US4] Crear `L/smoke.js`: 2 usuarios por 30 s, con la mezcla catálogo 70 %, login 15 % y pedido 15 %, y umbrales `http_req_duration{flow:*}: p(95)<2000` y `http_req_failed{flow:*}: rate<0.01`
  - *Ajuste del perfil (2026-11-26, decisión del usuario)*: en `smoke.js`, `load.js` y `stress.js` cada usuario virtual inicia sesión una sola vez y reutiliza su token (navegación: catálogo 80 % y pedido 20 %), y el login se mide en un escenario aparte a ritmo fijo (`LOGIN_RATE`, 5 por segundo). El perfil original pedía más logins de los que PBKDF2 (~0,7 s de CPU cada uno, a propósito) permite en este equipo, y esa cola dominaba la corrida. No se tocó el costo del hash.
- [X] T046 [US4] Crear `L/load.js` con la misma mezcla y los mismos umbrales: rampa de 2 min hasta 200 usuarios, 5 min sostenido y 1 min de bajada (SC-001)
- [X] T047 [US4] Crear `L/stress.js`: escalones de 100 en 100 hasta 600 usuarios (2 min cada uno), con umbrales que no abortan la corrida; el informe registra el primer escalón que incumple p95 < 2 s o errores < 1 %
- [X] T048 [P] [US4] Crear `L/README.md` con:
  - prerrequisitos (`winget install k6 --source winget`, `psql` de PostgreSQL 16, base `mikuna_load_db`);
  - cómo arrancar el backend contra esa base, con y sin `SPRING_CACHE_TYPE=none`;
  - comandos de los tres escenarios, variables (`BASE_URLS`, `CACHE_LABEL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `LOAD_PASSWORD` y `GIT_SHA`) y cómo leer los informes.

  Advertir que los resultados locales no acreditan RNF-01 en producción.
- [X] T049 [US4] Ejecutar la semilla T042 sobre `mikuna_load_db` y ejecutar `EXPLAIN (ANALYZE, BUFFERS)` de:
  - el catálogo público;
  - la consulta de portada T015;
  - `findVisible`;
  - el login por correo;
  - las consultas de pedidos del cliente y del restaurante.

  Registrar planes y tiempos en `L/reports/explain-antes.md`.
- [X] T050 [US4] Crear `R/db/migration/V2__indices_rendimiento.sql` con `CREATE INDEX ix_productos_publicos ON productos (restaurant_id, category, name) WHERE active AND available` (nombres de columna según V1), más cualquier otro índice que T049 muestre necesario por un recorrido secuencial costoso. Repetir los `EXPLAIN` en `L/reports/explain-despues.md`.
  - *Resultado (2026-11-26)*: se creó y midió el índice (`L/reports/explain-con-indice-descartado.txt`), pero no mejoró nada medible. La carta pasó de 0,2 a 0,5 ms (ruido, ya cubierta por `uk_productos_activo`) y la portada de respaldo sigue recorriendo la tabla (16 ms frente a 17 ms), lo correcto porque lee todas las filas y solo corre sin caché. Según FR-012 (solo índices que el plan muestre necesarios) **se retiró la V2**. Los índices de la V1 cubren las consultas revisadas (`L/reports/explain-antes.txt`).
- [X] T051 [US4] Ejecutar `k6 run smoke.js` contra `mikuna_load_db` y corregir los flujos hasta que pase
- [X] T052 [US4] Ejecutar `load.js` sin caché (backend con `SPRING_CACHE_TYPE=none`, `CACHE_LABEL=sin-cache`) y con caché (`CACHE_LABEL=cache`), con 1 instancia; conservar ambos informes en `L/reports/`
- [X] T053 [US4] Ejecutar `stress.js` con caché y registrar el límite real. Con Redis local (Docker), ejecutar `load.js` con el perfil `redis` y dos instancias (`BASE_URLS=http://localhost:8080,http://localhost:8081`), según quickstart §4.
- [X] T054 [US4] Crear `L/reports/RESUMEN.md`: tabla antes y después (p95 por flujo, errores, aciertos) frente a SC-001, SC-002 y SC-003; el límite de estrés; el resultado con dos instancias; y el estado de RNF-01, RNF-02, RNF-03 y RNF-04 (parcial o verificado en local; pendiente en producción)

**Checkpoint**: evidencia medible de los criterios 9, 11 y 13.

---

## Phase 7: Polish & Cross-Cutting

- [X] T055 Ejecutar `.\mvnw.cmd verify` en `backend/` y revisar en `backend/target/site/jacoco/` que `B/common/cache`, `B/common/ratelimit` y `B/catalog/service/PublicCatalog*` superan el 90 % de líneas (RNF-11)
- [X] T056 [P] Validar a mano quickstart §0 (arranque sin perfil, sin Redis ni Docker), §4 (dos instancias: 429 compartido, precio visible en la otra) y §5 (`docker stop mikuna-redis` en caliente: catálogo 200, salud `redis: DOWN`, login funcionando)
- [X] T057 [P] Actualizar `docs/CONTEXTO_PARA_CONTINUAR.md` con una sección breve del 015 (perfil `redis`, `load-tests/`, enmienda 6.0.0 y restricciones de TLS y AWS)
- [X] T058 Marcar las tareas completadas en este archivo y recordar al usuario que retire el informe de impacto (comentario inicial) de `.specify/memory/constitution.md` antes de su commit

---

## Phase 8: Ajustes tras la medición (decisión del usuario del 2026-11-26)

**Purpose**: corregir lo que mostraron las pruebas de carga (`L/reports/RESUMEN.md`).

- [X] T059 [US3] Caché de dos niveles con el perfil `redis`: `B/common/cache/TwoLevelCache.java` y `TwoLevelCacheManager.java` (Caffeine local delante de Redis; descarte entre instancias por el canal `mikuna:cache:invalidate`), cableados en `B/common/cache/CacheConfig.java` con métricas del nivel 1 (research §2 bis)
- [X] T060 [US3] `B/common/cache/InvalidationSubscriptionStarter.java`: suscripción en segundo plano con reintento cada 5 s, para que Redis caído no impida arrancar (FR-006)
- [X] T061 [P] [US3] Pruebas: `BT/common/cache/TwoLevelCacheTest.java` (unidad: niveles, avisos, Redis caído) y `BT/common/cache/TwoLevelCacheRedisIntegrationTest.java` (Redis real: un descarte llega a la otra instancia en < 1 s)
- [X] T062 [US4] `DB_POOL_SIZE` por defecto a 25 en `R/application.properties` (corridas D y E del resumen)
- [X] T063 [US4] Sondas `/actuator/health/liveness` y `/actuator/health/readiness` públicas; *readiness* solo incluye la base de datos (`R/application.properties`, `B/common/security/SecurityConfig.java`, `BT/common/ActuatorMetricsSecurityIntegrationTest.java`)
- [X] T064 [US4] Repetir `load.js` con 2 instancias y Redis, y `stress.js` con pool 25; actualizar `L/reports/RESUMEN.md`

---

## Dependencies & Execution Order

- **Setup (T001–T004)** → **Foundational (T005–T009)** → historias.
- **US1 (T010–T019)** es la base de US2, porque los descartes actúan sobre la caché de US1.
- **US2 (T020–T028)** depende de US1.
- **US3 (T029–T039)** solo depende de Foundational. T032 usa la caché de US1 y US2.
- **US4 (T040–T054)**:
  - T040–T048 dependen solo de Foundational.
  - T049 y T050 necesitan T042 y T015.
  - T051–T054 necesitan US1 y US2, y T053 además US3.
- **Polish** al final.

Orden por prioridad: Setup → Foundational → US1 → US2 → US3 → US4 → Polish.

### Parallel Opportunities

- Setup: T003 ∥ T004.
- US1: T010 ∥ T011 ∥ T012 ∥ T013.
- US2: T020 ∥ T021, y T023 ∥ T024 ∥ T025 ∥ T026 ∥ T027, porque son servicios distintos.
- US3: T029 ∥ T030 ∥ T031 ∥ T032. US3 completa puede avanzar en paralelo con US2.
- US4: T040 ∥ T042 ∥ T043 ∥ T044 ∥ T048, que pueden escribirse mientras se implementa US1.

## Parallel Example: User Story 2

```text
Task: "T023 descartes en B/catalog/service/ProductService.java"
Task: "T024 descartes en B/restaurant/service/RestaurantService.java"
Task: "T026 descartes en B/restaurant/service/RestaurantReviewService.java"
Task: "T027 descartes en B/admin/service/AdminAccountService.java"
```

## Implementation Strategy

1. **MVP = Setup + Foundational + US1 + US2.** US1 sin US2 no se puede entregar, porque serviría datos viejos
   hasta 10 min. Con ambas, el catálogo es rápido y correcto con Caffeine y Redis.
2. **US3** habilita varias instancias sin cambiar nada para quien ejecuta sin perfil.
3. **US4** produce la evidencia: humo primero, después la carga sin y con caché, estrés y dos instancias, y
   el resumen.
4. En cada checkpoint, la suite completa en verde y la app arrancando sin Redis.
