# Contrato: configuración, métricas y pruebas de carga (015)

Las rutas de la API pública **no cambian**: mismos endpoints, parámetros y forma de respuesta en
`/api/public/restaurants`, `/api/public/restaurants/filters` y `/api/public/restaurants/{id}/catalog`
(spec 004). Este contrato cubre lo nuevo para quien opera la plataforma.

## Configuración (variables de entorno)

| Variable | Por defecto | Efecto |
|----------|-------------|--------|
| `SPRING_PROFILES_ACTIVE` | (vacío) | `redis` activa la caché en Redis, los contadores compartidos y el indicador de salud de Redis. Sin él: Caffeine en memoria y contadores en memoria, como hoy. |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / vacío | Conexión a Redis (solo con el perfil `redis`). Cambiar a un servicio gestionado = cambiar estos valores. |
| `MIKUNA_CACHE_TTL` | `PT10M` | Vida máxima de respaldo de las entradas públicas (en Redis, nivel 2). |
| `MIKUNA_CACHE_LOCAL_TTL` | `PT1M` | Solo perfil `redis`: vida del nivel local; respaldo si se pierde un aviso de descarte. |
| `SPRING_CACHE_TYPE` | (según perfil) | `none` apaga la caché; solo para la corrida "sin caché" de comparación (SC-003). |
| `DB_POOL_SIZE` / `DB_POOL_MIN_IDLE` | `25` / `2` | Conexiones por instancia (research §7; 25 sale de las pruebas de carga). |

En este incremento no se añaden variables de HTTPS, TLS, certificados ni AWS.

## Métricas

`GET /actuator/metrics/cache.gets?tag=cache:public-restaurants&tag=result:hit`

- **Protegido**: requiere un token de rol `ADMIN`. Sin token → 401; con otro rol → 403.
- `/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness` son públicos. *Readiness* solo depende de la base de datos: un balanceador no saca de servicio una instancia porque Redis cayó.
- `/actuator/health` sigue mostrando el estado completo. Con el perfil `redis`, incluye el componente `redis`. Si Redis
  está caído, la salud informa `redis: DOWN`, pero la aplicación sigue sirviendo (FR-006).

Respuesta (formato estándar de Actuator):

```json
{ "name": "cache.gets", "measurements": [{ "statistic": "COUNT", "value": 1234.0 }],
  "availableTags": [{ "tag": "result", "values": ["hit", "miss"] }] }
```

Proporción de aciertos = hit / (hit + miss), por caché, entre el inicio y el fin de la corrida.

## Pruebas de carga (`load-tests/`)

| Comando | Escenario | Usuarios | Umbrales |
|---------|-----------|----------|----------|
| `k6 run smoke.js` | humo | 2 por 30 s | p95 < 2 s, errores < 1 % |
| `k6 run load.js` | carga | rampa hasta 200, 5 min sostenido | p95 < 2 s, errores < 1 % por flujo (SC-001) |
| `k6 run stress.js` | estrés | de 100 en 100 hasta 600 | registra el primer escalón que incumple |

Variables de k6:

| Variable | Significado |
|----------|-------------|
| `BASE_URLS` | Lista separada por comas; las peticiones se reparten en ronda entre las URL (dos instancias sin balanceador). |
| `CACHE_LABEL` | `cache` o `sin-cache`; solo para el informe. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Lectura de métricas; si faltan, el informe omite la proporción de aciertos. |
| `LOAD_PASSWORD` | Contraseña de los clientes de la semilla. |

Escenarios simultáneos en cada corrida:

- `navegacion`: usuarios virtuales que inician sesión una vez y reutilizan el token (catálogo 80 %, pedido 20 %).
- `login`: inicios de sesión a ritmo fijo (`LOGIN_RATE`, 5 por segundo por defecto).

Flujos etiquetados (`flow`):

- `catalogo`: `GET /api/public/restaurants`, con y sin filtros; `GET /api/public/restaurants/filters`;
  `GET /api/public/restaurants/{id}/catalog`.
- `login`: `POST /api/auth/login` con un cliente de la semilla.
- `pedido`, con efectivo y sin pasarela:
  1. `DELETE /api/customer/cart`;
  2. `POST /api/customer/cart/lines`;
  3. `PUT /api/customer/cart/address`;
  4. `GET /api/customer/checkout`;
  5. `POST /api/customer/orders`, con `Idempotency-Key` único, `paymentMethod: CASH` y el `expectedTotal`
     devuelto por el checkout.

La corrida termina con código distinto de cero si incumple un umbral, y escribe el informe descrito en
[data-model.md](../data-model.md#informe-de-carga-archivo-no-base-de-datos).
