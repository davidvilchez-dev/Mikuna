# Pruebas de carga (spec 015)

Pruebas k6 reproducibles sobre los tres flujos de mayor tráfico de Mikuna:

| Flujo | Qué hace |
|-------|----------|
| `catalogo` | Lista de restaurantes (con y sin filtro), filtros y carta de un restaurante (API pública) |
| `login` | Inicio de sesión de un cliente de la semilla |
| `pedido` | Vacía el carrito, agrega un plato, elige la dirección, consulta el checkout y confirma con **efectivo** (nunca llama a Stripe) |

Cada corrida tiene dos escenarios a la vez:

- **navegación**: los usuarios virtuales. Cada uno inicia sesión una sola vez y reutiliza su token, como un
  usuario real. Mezcla: catálogo 80 % y pedido 20 %, con 1 s de pausa.
- **login**: inicios de sesión a ritmo fijo (`LOGIN_RATE`, 5 por segundo por defecto; 1 por segundo en humo).

Umbrales de la propuesta por flujo: **p95 < 2 s** y **errores < 1 %**. Una corrida que no los cumple termina
con código distinto de cero.

> **Por qué el login va aparte.** La contraseña se valida con PBKDF2, que cuesta unos 0,7 s de CPU por login a
> propósito (frena los intentos de adivinarla). En un equipo de 12 núcleos eso da para unos 15 logins por
> segundo. Una primera versión hacía login en el 15 % de las iteraciones de cada usuario. Con 200 usuarios eso
> pedía mucho más que esa capacidad y la cola de logins dominaba la corrida (informe
> `*-cache-tras-arreglo-login-*`). Un usuario real inicia sesión una vez por visita.

> Los resultados en local miden este equipo, no producción: **no acreditan RNF-01** en el despliegue.

## Requisitos (una sola vez)

1. k6: `winget install k6 --source winget` (abrir una terminal nueva después).
2. Base dedicada, nunca la de desarrollo:
   ```powershell
   & 'C:\Program Files\PostgreSQL\16\bin\createdb.exe' -U postgres mikuna_load_db
   ```
3. Arrancar una vez el backend contra esa base para que Flyway cree el esquema, detenerlo y cargar la semilla:
   ```powershell
   cd backend
   $env:DB_URL = 'jdbc:postgresql://localhost:5432/mikuna_load_db'
   .\mvnw.cmd spring-boot:run          # esperar "Started", luego Ctrl+C
   & 'C:\Program Files\PostgreSQL\16\bin\psql.exe' -U postgres -d mikuna_load_db -f ..\load-tests\seed\seed.sql
   ```
   La semilla vacía `mikuna_load_db` y carga 200 restaurantes abiertos todo el día, 6 000 platos, 300 clientes
   con dirección y un administrador. Se niega a correr en cualquier otra base.

Cuentas de la semilla (contraseña `CargaMikuna2026!` para todas):

- Clientes: `carga1@mikuna.test` … `carga300@mikuna.test`.
- Administrador: `admin-carga@mikuna.test`.

## Correr

Backend contra `mikuna_load_db` (misma variable `DB_URL` de arriba). En otra terminal:

```powershell
cd load-tests
$env:GIT_SHA = (git rev-parse --short HEAD)

k6 run smoke.js                                  # humo: 2 usuarios, 30 s; debe pasar

# Antes y después de la caché (SC-003): misma prueba con la caché apagada y encendida
#   backend con $env:SPRING_CACHE_TYPE='none'  →  $env:CACHE_LABEL='sin-cache'; k6 run load.js
#   backend sin esa variable                   →  $env:CACHE_LABEL='cache';     k6 run load.js

k6 run stress.js                                 # escalones de 100 hasta 600 usuarios: busca el límite
```

Dos instancias con Redis compartido (historia 3, solo local; requiere Docker Desktop encendido):

```powershell
docker run --name mikuna-redis -p 6379:6379 -d redis:8-alpine
# Terminal A y B, desde backend/ y con el mismo DB_URL:
$env:SPRING_PROFILES_ACTIVE='redis'; $env:SERVER_PORT='8080'; .\mvnw.cmd spring-boot:run
$env:SPRING_PROFILES_ACTIVE='redis'; $env:SERVER_PORT='8081'; .\mvnw.cmd spring-boot:run
# Terminal C:
$env:BASE_URLS='http://localhost:8080,http://localhost:8081'; $env:CACHE_LABEL='cache-redis'; k6 run load.js
```

## Variables

| Variable | Por defecto | Uso |
|----------|-------------|-----|
| `BASE_URLS` | `http://localhost:8080` | Una o varias URL separadas por comas; las peticiones se reparten en ronda |
| `CACHE_LABEL` | `sin-etiqueta` | Etiqueta del informe: `cache`, `sin-cache`, `cache-redis`… |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | cuenta de la semilla | Para leer los aciertos de caché en `/actuator/metrics` |
| `LOAD_PASSWORD` | `CargaMikuna2026!` | Contraseña de los clientes de la semilla |
| `GIT_SHA` | `unknown` | Commit que aparece en el informe |
| `TARGET_VUS` | `200` | Solo `load.js`: usuarios del tramo sostenido |
| `LOGIN_RATE` | `5` | Inicios de sesión por segundo del escenario de login (`load.js` y `stress.js`) |

## Informes

Cada corrida escribe `reports/<AAAAMMDD-HHMM>-<escenario>-<CACHE_LABEL>-<n>inst.md` y `.json` con:

- commit, fecha, URL, datos, usuarios máximos y duración;
- p50, p95, p99 y errores por flujo;
- si se cumplieron los umbrales;
- la proporción de aciertos de la caché, sumada entre instancias.

`stress.js` añade una tabla por escalón con el primero que incumple. El resumen comparado vive en
`reports/RESUMEN.md`.
