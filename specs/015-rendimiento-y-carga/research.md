# Research: Rendimiento, caché y pruebas de carga (015)

Decisiones tomadas con las tecnologías ya elegidas (constitución VII) y la enmienda 6.0.0.

## §1. Proveedor de caché y cambio por configuración (FR-001, FR-008)

- **Decision**: abstracción de caché de Spring (`spring-boot-starter-cache`) con dos proveedores: Caffeine en
  memoria (por defecto) y Redis (`spring-boot-starter-data-redis`, cliente Lettuce) activado con el perfil
  `redis`. Sin el perfil, la aplicación arranca exactamente como hoy, sin Redis ni Docker. El perfil `redis`
  fija `spring.cache.type=redis`, los contadores compartidos (§5) y el indicador de salud de Redis; host,
  puerto y contraseña vienen de variables de entorno, así que pasar a un servicio gestionado es solo
  configuración.
- **Rationale**: un único interruptor (`SPRING_PROFILES_ACTIVE=redis`) y el mismo código en todos los
  entornos. Caffeine, a diferencia de la caché `simple`, admite tiempo de vida, tamaño máximo y estadísticas.
- **Alternatives considered**: solo Redis (obligaría a tener Docker en local, contra la enmienda 6.0.0);
  `spring.cache.type=simple` (sin tiempo de vida ni estadísticas).

## §2. Qué se guarda: datos sin estado de atención (FR-001, FR-002, FR-014)

- **Decision**: dos cachés.
  - `public-restaurants`: una sola entrada con la **instantánea de restaurantes visibles**. Cada elemento
    lleva los datos públicos, coordenadas, `ownerId`, fecha de envío, disponibilidad, horario y la portada de
    respaldo ya resuelta. Búsqueda, filtros, agrupación por marca, distancia y orden se calculan en memoria
    sobre la instantánea en cada petición, y el estado de atención se calcula con la hora actual
    (`OrderEligibility` recibe disponibilidad y horario en lugar de la entidad). `filters()` sale de la misma
    instantánea.
  - `public-catalog`: clave = id del restaurante; valor = **todos** sus productos activos y disponibles, ya
    ordenados por categoría y nombre. La paginación se hace en memoria.
- **Rationale**: el texto de búsqueda y la página no forman parte de ninguna clave, así que las búsquedas
  libres no llenan el almacén (caso límite) y un cambio invalida todas las páginas de un catálogo. Al no
  guardar el estado calculado se cumple FR-002 sin depender del tiempo de vida. Con la instantánea, una
  búsqueda con la caché llena no consulta la base de datos (FR-014).
- **Alternatives considered**: guardar la respuesta final por combinación de parámetros (claves sin límite,
  estado de atención viejo, invalidación por patrón); guardar entidades JPA (no se pueden serializar de forma
  segura y arrastran relaciones perezosas).

## §2 bis. Caché de dos niveles con el perfil `redis` (medición del 2026-11-26)

- **Decision**: con el perfil `redis`, cada caché tiene dos niveles (`common/cache/TwoLevelCacheManager`):
  - Nivel 1: Caffeine en la memoria de la instancia, sin serializar. Resuelve casi todas las lecturas.
  - Nivel 2: Redis, compartido entre instancias. Evita que cada instancia vuelva a la base de datos tras un
    descarte.

  Al descartar se borran ambos niveles y se publica `<caché>|<clave>` en el canal `mikuna:cache:invalidate`.
  Las demás instancias borran su nivel 1 al recibirlo (SC-004 entre instancias).
  - Vida del nivel 1: `MIKUNA_CACHE_LOCAL_TTL`, 1 min. Es solo un respaldo si se pierde un aviso.
  - La suscripción arranca en segundo plano (`InvalidationSubscriptionStarter`) y reintenta cada 5 s, así que
    Redis caído no impide arrancar (FR-006).
  - Las métricas `cache.gets` miden el nivel 1.
- **Rationale**: con Redis como único nivel, cada consulta del catálogo descargaba y deserializaba la instantánea
  (174 KB de JSON). Con 2 instancias y 200 usuarios produjo unos 18 000 *command timed out* por instancia y un
  p95 de 5,4 s (`load-tests/reports/RESUMEN.md` §3).
- **Alternatives considered**: Caffeine por instancia con TTL corto (otra instancia vería cambios con retraso,
  incumple SC-004); subir el tiempo de espera de Redis (no ataca la causa).

## §3. Invalidación y carreras (FR-004, FR-005, SC-004)

- **Decision**: los servicios que escriben publican el evento `PublicCatalogChanged` (en `common/cache`; con el
  id del restaurante cuando cambió su carta). `PublicCatalogCache` lo escucha con
  `@TransactionalEventListener(fallbackExecution = true)`, así que el descarte se ejecuta **después del commit**
  (sin transacción, en el acto; si se revierte, no hay descarte). Tras ese descarte se repite uno diferido de
  1 s, para cubrir la lectura que empezó antes del commit y guardó el dato viejo. Tiempo de vida de respaldo:
  10 min en ambas cachés.
  - *Ajuste de implementación (2026-11-26)*: el plan original llamaba a `PublicCatalogCache` directamente
    desde los servicios de `restaurant` y `admin`. Eso creaba un ciclo `restaurant ↔ catalog` (constitución I).
    El evento elimina el ciclo y aporta la semántica "después del commit" sin código manual. El proyecto ya
    usa eventos (`OrderPlacedEvent`).

  | Operación | Descarta |
  |-----------|----------|
  | `ProductService`: crear, editar, disponibilidad, retirar, copiar menú | catálogo del restaurante + instantánea (la portada de respaldo depende del primer plato) |
  | `RestaurantService`: crear, sucursal, editar, horario, disponibilidad, reenviar | instantánea |
  | `RestaurantImageService.update` | instantánea |
  | `RestaurantReviewService`: aprobar, rechazar | instantánea |
  | `AdminAccountService`: habilitar o deshabilitar cuenta, habilitar o suspender operación | instantánea |

- **Rationale**: descartar antes del commit deja que otra lectura vuelva a guardar el valor viejo. El doble
  descarte es la técnica habitual y deja la ventana residual por debajo de 1 s (SC-004). Una instantánea
  única hace que todo cambio de restaurante sea un solo descarte, sin claves por patrón.
- **Alternatives considered**: `@CacheEvict` en los métodos (se ejecuta dentro de la transacción, antes del
  commit); versión o generación en la clave (más piezas para el mismo resultado a esta escala).

## §4. Fallas del almacén (FR-006)

- **Decision**: un `CacheErrorHandler` propio que registra la falla y continúa: un fallo al leer cuenta como
  ausencia (se lee de la base de datos) y un fallo al guardar o descartar se ignora. Tiempos de espera de Redis
  cortos (conexión 500 ms, comando 250 ms) para que la caída no frene cada petición.
- **Rationale**: el manejador por defecto propaga la excepción y convierte la caída de la caché en un 500. Si
  un descarte falla porque Redis está caído, las lecturas también fallan, así que el dato viejo solo podría
  verse tras la recuperación y el tiempo de vida lo acota.

## §5. Contadores compartidos con respaldo en memoria (FR-009, FR-010a)

- **Decision**: se extrae la ventana deslizante a una interfaz `SlidingWindowCounter` con dos
  implementaciones. La de memoria contiene la lógica actual de `LoginAttemptLimiter` y
  `AssistantUsageLimiter`. La de Redis usa un conjunto ordenado por clave (marca de tiempo como puntuación) y
  un script Lua atómico que poda, cuenta, agrega y fija la expiración. La de Redis atrapa cualquier error de
  conexión y delega en la de memoria. Las claves no llevan datos personales: `mikuna:login:acct:<sha256 del
  correo>`, `mikuna:login:ip:<sha256 de la IP>` y `mikuna:assistant:<id de cuenta>`. Los límites y la ventana
  no cambian.
- **Rationale**: Lua hace atómico "contar y agregar" entre instancias (el límite del asistente no se puede
  pasar por carrera). La interfaz se justifica porque hay dos implementaciones reales (constitución I). El
  respaldo en memoria es la respuesta Q2 de la clarificación.
- **Alternatives considered**: tabla en PostgreSQL (más carga en la base de datos que se quiere aliviar);
  `INCR` con ventana fija (cambia la semántica de ventana deslizante probada en las specs 002 y 013).

## §6. Barrido de vencimientos con varias instancias (FR-010)

- **Decision**: sin cambios de código. `PendingOrderExpiryService.expire` ya bloquea la fila
  (`findByIdForUpdate`) y `expireIfDue` comprueba el estado antes de cancelar, así que una segunda instancia
  encuentra el pedido ya Cancelado y no hace nada. La prueba ya existe:
  `PendingOrderExpiryConcurrencyIntegrationTest.dosBarridosALaVezVencenUnaSolaVezConUnAvisoPorDestinatario`
  (spec 012). Solo se enlaza como evidencia.
- **Alternatives considered**: ShedLock o un líder elegido (infraestructura innecesaria ante un bloqueo de
  fila ya correcto).

## §7. Base de datos: consultas, índices y pool (FR-012, FR-013)

- **Decision**:
  - La portada de respaldo deja de costar una consulta por restaurante: al armar la instantánea se resuelve
    en una sola consulta nativa con `DISTINCT ON (restaurant_id)` sobre los productos activos y disponibles.
  - Migración `V2__indices_rendimiento.sql` con un índice parcial
    `productos (restaurant_id, category, name) WHERE active AND available`, que cubre el catálogo público, el
    respaldo de portada y la validación del pedido. Antes de fijarla, se ejecuta `EXPLAIN ANALYZE` sobre los
    datos de volumen. Cualquier otro índice solo se añade si el plan muestra un recorrido secuencial costoso
    (pedidos, login y cuentas ya tienen índices en la V1).
  - *Medición (2026-11-26)*: el índice parcial no mejoró ningún plan con datos de volumen (ver tasks T050); se retiró y no hay migración V2. Las consultas de mayor tráfico ya usan los índices de la V1, y la de portadas solo corre cuando falta la instantánea.
  - Hikari configurable: `DB_POOL_SIZE` (por defecto 10), `DB_POOL_MIN_IDLE` (2) y espera de conexión de
    5 s. Regla: instancias × pool < `max_connections` de la base menos un margen de 10.
- **Rationale**: el costo dominante de la búsqueda era el N+1 de la portada y la carga completa por consulta;
  con la caché y la consulta única, la base de datos solo trabaja cuando falta la instantánea.

## §8. Pruebas de carga con k6 (FR-015 a FR-019)

- **Decision**: carpeta `load-tests/` en la raíz, con:
  - `smoke.js`, `load.js` y `stress.js` sobre un módulo común de flujos: catálogo público (buscar, filtrar,
    abrir catálogo), inicio de sesión, y creación de pedido (carrito → dirección → confirmar con **efectivo**,
    FR-017).
  - Umbrales `p(95)<2000` y `rate<0.01` por flujo, con etiquetas.
  - Perfiles: humo 2 usuarios por 30 s; carga hasta 200 usuarios (SC-001) en rampa de 2 min, 5 min sostenido
    y 1 min de bajada; estrés escalonado de 100 en 100 hasta 600 usuarios.
  - `BASE_URLS` con una o varias URL, que se reparten en ronda: dos instancias sin balanceador.
  - `handleSummary` escribe `load-tests/reports/<fecha>-<escenario>-<config>.md/.json` con versión (commit),
    datos, duración, usuarios, configuración y resultados (FR-019).
  - Datos de volumen: `load-tests/seed/seed.sql` (200 restaurantes aprobados con horario completo,
    30 productos cada uno, 300 clientes con contraseña y dirección conocidas, generados con
    `generate_series`) que se ejecuta con `psql` sobre una base dedicada `mikuna_load_db`, nunca sobre
    `mikuna_db` (FR-018).
- **Rationale**: k6 es la herramienta que conserva la constitución. Una base dedicada evita mezclar datos de
  carga con los del desarrollo. Repartir entre URLs evita montar un balanceador (fuera de alcance).
- **Alternatives considered**: sembrar desde la aplicación con un perfil (código de producción solo para
  pruebas); balanceador local (infraestructura adicional aplazada).

## §9. Medición de aciertos (FR-020, SC-002)

- **Decision**: estadísticas activadas en ambos proveedores (`recordStats` en Caffeine; `enableStatistics`
  en Redis) y publicadas por Micrometer como `cache.gets{result=hit|miss}`. Se expone
  `/actuator/metrics` **solo para ADMIN**; `/actuator/health` sigue siendo público. La prueba de carga lee
  las métricas al inicio y al final con un token de administrador y escribe la proporción de aciertos en el
  informe.
- **Rationale**: es una medición de aciertos sin código propio y la lee la misma corrida.
- **Alternatives considered**: contar aciertos en registros (impreciso); exponer las métricas sin
  protección (rompe RNF-07).

## §10. Redis en local y en CI

- **Decision**: en local, Redis es opcional con `docker run --name mikuna-redis -p 6379:6379 -d
  redis:8-alpine` (sin archivos de infraestructura en el repositorio). En CI, `backend.yml` agrega un
  servicio `redis` y la variable `MIKUNA_TEST_REDIS=true`. Las pruebas que requieren Redis se marcan con
  `@EnabledIfEnvironmentVariable(named = "MIKUNA_TEST_REDIS", matches = "true")`: en CI siempre corren; en
  local, solo si se levantó Redis. El resto de la suite usa Caffeine.
- **Rationale**: cumple la restricción del usuario (nada que dependa de Docker para ejecutar o probar en
  local) sin dejar sin probar el camino de Redis.
- **Alternatives considered**: Testcontainers (exige Docker en cada `mvn verify` local).

## §11. Despliegue (solo contexto, sin archivos)

- Las cifras de destino (EC2 de 1 GB con backend y Redis, base gestionada gratuita) solo dimensionan:
  - montón del JVM de unos 512 MB;
  - `maxmemory` de Redis de unos 64 MB con `allkeys-lru`;
  - pool de 10 conexiones.

  No se crea configuración de AWS, HTTPS ni TLS (restricción del usuario, enmienda 6.0.0).
