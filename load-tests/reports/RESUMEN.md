# Resumen de rendimiento: spec 015 (2026-11-26)

Todas las corridas son locales y en un solo portátil de 12 núcleos, que comparten k6, el backend (o los dos
backends) y PostgreSQL 16. Los datos salen de `seed/seed.sql`: 200 restaurantes, 6 000 platos y 300 clientes; la
semilla se recarga antes de cada corrida. Umbrales de la propuesta: p95 < 2 s y errores < 1 %. **Estos números
miden este equipo; no acreditan RNF-01 en producción.**

## 1. Carga de 200 usuarios, una instancia

| # | Configuración | Catálogo p95 | Login p95 | Pedido p95 | Errores (cat./login/ped.) | Aciertos caché | Informe |
|---|---------------|--------------|-----------|------------|---------------------------|----------------|---------|
| A | Sin caché, pool 10 | 6 641 ms ❌ | 5 824 ms ❌ | 9 299 ms ❌ | 9,5 / 8,9 / 13,4 % | — | `*-1054-load-sin-cache-*` |
| B | Caché, pool 10 | **26 ms ✅** | 8 263 ms ❌ | 9 767 ms ❌ | 0,03 / 29,7 / 33,7 % | 99,7 % | `*-1103-load-cache-*` |
| C | B + login sin transacción | 243 ms ✅ | 28 020 ms ❌ | 288 ms ✅ | 0 / 0 / 0 % | 99,8 % | `*-1113-load-cache-tras-arreglo-login-*` |
| D | C + perfil realista¹, pool 10 | 587 ms ✅ | 32 138 ms ❌ | 5 505 ms ❌ | 0 / 4,3 / 10,7 % | 99,9 % | `*-1929-load-cache-perfil-real-*` |
| **E** | **D con pool 25** | **12 ms ✅** | 2 015 ms ⚠️² | **22 ms ✅** | **0 / 0 / 0 %** | **99,95 %** | `*-1959-load-cache-pool25-*` |

¹ Cada usuario inicia sesión una vez y reutiliza el token. La navegación es 80 % catálogo y 20 % pedido, y los
logins van aparte a 5 por segundo.
² 15 ms por encima del umbral, sin errores. Es el costo de CPU de PBKDF2 (unos 0,7 s por login a propósito)
compartiendo la máquina con k6 y PostgreSQL.

**Qué demuestra:**

- **Caché, SC-002 y SC-003 (A → B):**
  - el catálogo pasa de p95 6,6 s con 9,5 % de errores a 26 ms sin errores;
  - mejora del p95 del catálogo: **99,6 %** (SC-003 pedía ≥ 50 %);
  - aciertos de caché: **99,7–99,95 %** (SC-002 y RNF-03 piden ≥ 80 %).
- **Cuellos de botella encontrados y resueltos con medición:**
  1. *Sin caché, A*: cada consulta del catálogo ocupaba una conexión y el pool de 10 se agotaba. Lo resolvió la
     caché.
  2. *Login, B → C*: `AuthService.login` validaba PBKDF2 dentro de una transacción y retenía una conexión cientos
     de ms. Se quitó la transacción y los errores pasaron de 30–34 % a 0 %.
  3. *Pool, D → E*: con un uso realista, 10 conexiones no alcanzaban para las escrituras de pedidos. Con 25
     (`DB_POOL_SIZE`, FR-013) el pedido pasa de p95 5,5 s a 22 ms.
- **Índices, FR-012**: se midió un índice parcial de productos con `EXPLAIN ANALYZE`. No mejoró ningún plan
  (`explain-con-indice-descartado.txt`) y se retiró. Los índices de la V1 cubren las consultas de mayor
  tráfico (`explain-antes.txt`).

## 2. Estrés, una instancia

| Usuarios | 100 | 200 | 300 | 400 | 500 | 600 |
|----------|-----|-----|-----|-----|-----|-----|
| p95, pool 10 | 84 ms ✅ | 5 142 ms ❌ | 5 910 ms ❌ | 5 594 ms ❌ | 5 231 ms ❌ | 5 498 ms ❌ |
| Errores, pool 10 | 0 % | 3,3 % | 6,7 % | 7,5 % | 6,3 % | 6,7 % |
| **p95, pool 25** | **18 ms ✅** | **42 ms ✅** | **339 ms ✅** | **667 ms ✅** | **556 ms ✅** | **739 ms ✅** |
| **Errores, pool 25** | **0 %** | **0 %** | **0 %** | **0,08 %** | **0,31 %** | **0,28 %** |

- **Con pool 10** el límite estaba entre 100 y 200 usuarios, y lo marcaba el pool
  (`*-1943-stress-cache-perfil-real-*`).
- **Con pool 25** no se alcanzó el límite hasta **600 usuarios concurrentes** (`*-2109-stress-cache-pool25-*`).
  - Por flujo sobre toda la corrida: catálogo p95 554 ms ✅, pedido p95 542 ms ✅ (0,45 % de errores), aciertos
    99,9 %.
  - El login, a 5 por segundo, sí incumple (p95 28 s): con 600 usuarios la CPU del equipo se reparte entre k6,
    PostgreSQL y PBKDF2.
  - Quedan unos 130 avisos de pool agotado en toda la corrida; los pedidos los absorben con 0,45 % de errores.

## 3. Dos instancias con Redis (historia 3, RNF-02, solo local)

**Coherencia: verificada.**

- **Freno de fuerza bruta compartido (SC-006)**: 5 fallos alternando 8080 y 8081 para la misma cuenta; el sexto
  intento da **429 en ambas instancias**. Las claves en Redis llevan hash (`mikuna:login:acct:<sha256>`), sin
  correo en claro.
- Pruebas automáticas con Redis real, 8 en verde:
  - contador compartido entre dos instancias;
  - `tryAcquire` atómico con 20 hilos;
  - serialización y descarte de la caché en Redis;
  - respaldo en memoria si Redis no responde.
- **Redis caído en caliente (SC-005)**: catálogo, filtros y login siguen en 200 desde la base de datos. Al volver
  Redis, la caché se repuebla sola (catálogo en 30 ms).

**Rendimiento con dos instancias:**

| Configuración | Catálogo p95 | Login p95 | Pedido p95 | Errores | Aciertos | Informe |
|---------------|--------------|-----------|------------|---------|----------|---------|
| Solo Redis como caché | 5 372 ms ❌ | 9 530 ms ❌ | 6 556 ms ❌ | 0,3–0,6 % | 88 % | `*-2009-load-cache-redis-2inst-*` |
| **Dos niveles (Caffeine + Redis)** | **44 ms ✅** | 7 171 ms ❌ | **37 ms ✅** | **0 %** | **99,2 %** | `*-2055-load-cache-2niveles-2inst-*` |

- **Problema encontrado**: con Redis como único nivel, la instantánea de restaurantes (**174 KB** de JSON) se
  descargaba y deserializaba en cada consulta. Producía unos 18 000 *Redis command timed out* por instancia.
- **Solución aplicada (research §2 bis)**: caché de **dos niveles**. Las lecturas salen de Caffeine en cada
  instancia; Redis es el nivel compartido. Los descartes se avisan a las demás instancias por pub/sub
  (`mikuna:cache:invalidate`), así que un cambio se ve al instante en todas (prueba
  `TwoLevelCacheRedisIntegrationTest`). Los errores del backend bajaron de unos 18 000 a 11 por instancia.
- **Límite de la medición**: dos JVM en la misma máquina no suman CPU. El login, CPU puro por PBKDF2, empeora con
  dos instancias en un solo equipo. Para acreditar RNF-02 (el doble de usuarios con más instancias) hacen falta
  máquinas separadas.

## 4. Observaciones para el despliegue

- **Hecho**: `/actuator/health/readiness` es pública y solo depende de la base de datos. El balanceador debe usar
  esa sonda; `/actuator/health` completo pasa a `DOWN` si Redis cae, aunque la aplicación siga sirviendo.
- **Hecho**: `DB_POOL_SIZE` vale 25 por defecto. Con *n* instancias, *n* × pool debe quedar por debajo
  del `max_connections` de la base menos un margen (el RDS gratuito tiene un límite menor que el local, que es de
  100).

## 5. Estado de los requisitos

| Requisito | Estado | Evidencia |
|-----------|--------|-----------|
| RNF-03 (≥ 80 % de aciertos) | **Verificado en local** | 99,2–99,95 % en todas las corridas con caché, también con 2 instancias |
| SC-003 (p95 del catálogo ≥ 50 % mejor) | **Verificado en local** | 6 641 → 26 ms (corridas A y B, −99,6 %) |
| SC-001 (200 usuarios, p95 < 2 s, < 1 % de errores) | **Verificado salvo el login** | Catálogo 12 ms y pedido 22 ms con 0 % de errores (E); el login queda en 2 015 ms por CPU de PBKDF2 |
| SC-004 (cambio visible en < 1 s) | **Verificado** | Pruebas de invalidación; entre instancias, pub/sub en < 1 s con Redis real |
| SC-005 (sin Redis sigue respondiendo) | **Verificado** | Prueba en caliente, pruebas automáticas y arranque con Redis caído |
| SC-006 (límites compartidos) | **Verificado** | 429 en ambas instancias (dos corridas); pruebas con Redis real |
| RNF-01 (1 000 usuarios) | **Parcial** | 600 usuarios sin superar el umbral global en un portátil (estrés con pool 25); el login es el cuello de botella; falta medirlo en producción |
| RNF-02 (más instancias, más capacidad) | **Parcial** | Coherencia verificada y caché lista para escalar; la ganancia de capacidad exige máquinas separadas |
| RNF-04 (sigue si cae una instancia) | **Parcial** | Instancias sin estado propio, estado compartido en Redis o BD y sonda *readiness*; falta una prueba de caída con balanceador |

**Siguiente cuello de botella conocido**: el login. PBKDF2 cuesta a propósito unos 0,7 s de CPU por intento. Se
escala con más CPU o más instancias en máquinas separadas, no debilitando el hash.
