# Data Model: Rendimiento, caché y pruebas de carga (015)

El esquema de negocio no cambia. La única migración (`V2__indices_rendimiento.sql`) añade índices
(research §7). Lo nuevo son entradas de caché y contadores, que viven en el almacén (Caffeine o Redis).

## Entradas de caché

### Instantánea de restaurantes visibles — caché `public-restaurants`

| Campo | Origen | Nota |
|-------|--------|------|
| `id`, `ownerId` | restaurante | `ownerId` solo se usa para agrupar sucursales por marca; nunca se devuelve |
| `name`, `category`, `description`, `zone` | restaurante | datos públicos ya expuestos hoy |
| `logoUrl`, `coverUrl` | restaurante | |
| `fallbackCoverUrl` | primer producto activo y disponible | se resuelve con una sola consulta al armar la instantánea |
| `latitude`, `longitude` | restaurante | para la distancia; no se devuelven |
| `submittedAt` | restaurante | desempate de la sucursal principal |
| `available` | restaurante | pausado o no |
| `schedule[]` | horario: `day`, `opensAt`, `closesAt` | el estado de atención se calcula por petición (FR-002) |

- **Clave**: una sola (`all`). **Valor**: `RestaurantSnapshotList(List<RestaurantSnapshot>)`, un envoltorio
  tipado para serializar sin información de tipos polimórfica.
- **Contiene solo** restaurantes aprobados con cuenta habilitada y operación habilitada (misma regla que
  `findVisible`).
- **Vida**: 10 min de respaldo. **Se descarta** ante cualquier operación de la tabla de research §3.
- **No contiene**: dirección, teléfono, estado de aprobación, motivo de rechazo, datos de cuenta ni el estado
  de atención calculado.

### Productos disponibles de un restaurante — caché `public-catalog`

- **Clave**: id del restaurante. **Valor**: `PublicProductList(List<PublicProductResponse>)`, con los
  productos activos y disponibles ordenados por categoría y nombre. Campos ya públicos: `id`, `name`,
  `description`, `category`, `price`, `imageUrl`.
- **Vida**: 10 min de respaldo. **Se descarta** ante crear, editar, cambiar disponibilidad, retirar o copiar
  el menú de ese restaurante.
- **Lectura**: el catálogo primero comprueba la visibilidad en la instantánea. Un restaurante que no está en
  ella da 404 aunque su lista siga guardada.

## Dos niveles con el perfil `redis`

| Nivel | Dónde | Clave | Vida |
|-------|-------|-------|------|
| 1 | Caffeine en cada instancia | texto de la clave (`all` o id del restaurante) | `MIKUNA_CACHE_LOCAL_TTL` (1 min, respaldo) |
| 2 | Redis | `mikuna:cache:<caché>::<clave>` (JSON tipado) | `MIKUNA_CACHE_TTL` (10 min) |

Aviso de descarte: el canal `mikuna:cache:invalidate` recibe `<caché>|<clave>` (o `<caché>|*` al vaciar). Cada
instancia borra ese elemento de su nivel 1.

## Contadores compartidos (perfil `redis`)

| Clave | Contenido | Límite / ventana (sin cambios) |
|-------|-----------|--------------------------------|
| `mikuna:login:acct:<sha256(correo normalizado)>` | conjunto ordenado de marcas de fallos | `MIKUNA_LOGIN_MAX_FAILURES_PER_ACCOUNT` / `MIKUNA_LOGIN_WINDOW` |
| `mikuna:login:ip:<sha256(ip)>` | ídem | `MIKUNA_LOGIN_MAX_FAILURES_PER_IP` / `MIKUNA_LOGIN_WINDOW` |
| `mikuna:assistant:<accountId>` | conjunto ordenado de marcas de mensajes | `MIKUNA_ASSISTANT_RATE_LIMIT` / 60 s |

- Cada clave expira sola al terminar la ventana, sin purgas manuales.
- Un acceso correcto borra la clave de la cuenta (igual que hoy).
- Si Redis falla, los mismos contadores viven en la memoria de la instancia (FR-010a).

## Informe de carga (archivo, no base de datos)

`load-tests/reports/<AAAAMMDD-HHMM>-<escenario>-<cache|sin-cache>-<n>inst.{md,json}`, con estos datos:

- commit;
- escenario;
- usuarios máximos;
- duración;
- `BASE_URLS` (cantidad de instancias);
- perfil de caché;
- conjunto de datos (semilla y volúmenes);
- por flujo: p50, p95, p99 y tasa de errores;
- umbrales cumplidos o no;
- proporción de aciertos de caché (research §9).

## Datos de volumen (`mikuna_load_db`)

| Entidad | Cantidad | Nota |
|---------|----------|------|
| Restaurantes aprobados | 200 | 5 zonas (los distritos cubiertos) y 8 categorías; horario de lunes a domingo, de 00:00 a 23:59, para que estén abiertos |
| Productos | 30 por restaurante | activos y disponibles, en 5 secciones de carta |
| Clientes | 300 | `carga<n>@mikuna.test`, contraseña `CargaMikuna2026!`, con una dirección en Ayacucho |
| Administrador | 1 | `admin-carga@mikuna.test`, para leer las métricas |

Todos los correos usan el dominio reservado `.test`. La semilla solo corre si la base se llama
`mikuna_load_db`: vacía esa base y vuelve a cargarla.
