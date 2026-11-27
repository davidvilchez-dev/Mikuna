# Quickstart: validar el incremento 015

Todo corre en local por HTTP. No se configuran HTTPS, TLS, certificados ni AWS.

## 0. Sin cambios para el día a día

`.\mvnw.cmd spring-boot:run` (sin perfil) debe arrancar igual que antes, sin Redis ni Docker. Las consultas
públicas usan Caffeine. Comprobación: la aplicación arranca, `/actuator/health` da `UP` y el catálogo
responde.

## 1. Pruebas automatizadas

```powershell
cd backend
.\mvnw.cmd verify                  # suite completa con Caffeine; las pruebas de Redis se omiten
# Opcional, con Redis local (Docker Desktop encendido):
docker run --name mikuna-redis -p 6379:6379 -d redis:8-alpine
$env:MIKUNA_TEST_REDIS = 'true'; .\mvnw.cmd verify
```

| Escenario | Prueba | Esperado |
|-----------|--------|----------|
| Acierto (US1-1) | catálogo consultado dos veces | la 2.ª no consulta la BD; respuesta idéntica |
| Estado en vivo (US1-2/3) | reloj fijo a las 17:59 y luego a las 18:01 | `OPEN` y luego `OUTSIDE_HOURS` sin descartar la caché; pausado al instante |
| Invalidación (US2) | cada operación de research §3 | la siguiente consulta refleja el cambio |
| Carrera (caso límite) | lectura lenta concurrente con un cambio | tras el descarte diferido no queda el dato viejo |
| Redis caído (FR-006) | perfil `redis` con un puerto sin servicio | catálogo `200` desde la BD, falla registrada |
| Contadores compartidos (US3-1/2) | dos limitadores sobre el mismo Redis | el bloqueo llega con el mismo total de intentos |
| Respaldo en memoria (FR-010a) | contador Redis sin conexión | el límite sigue aplicándose en memoria |
| Barrido doble (US3-4) | dos barridos concurrentes | una cancelación, un aviso, una liberación |
| Métricas (contrato) | `/actuator/metrics` sin token, con cliente, con admin | 401, 403, 200 |

## 2. Datos de volumen

```powershell
& 'C:\Program Files\PostgreSQL\16\bin\createdb.exe' -U postgres mikuna_load_db
# Arrancar una vez el backend contra mikuna_load_db para que Flyway cree el esquema; luego:
& 'C:\Program Files\PostgreSQL\16\bin\psql.exe' -U postgres -d mikuna_load_db -f load-tests\seed\seed.sql
```

Esperado: 200 restaurantes visibles, 6 000 productos y 300 clientes con dirección. Con `-d mikuna_db`, la
semilla aborta.

## 3. Pruebas de carga (k6)

Instalar una vez: `winget install k6 --source winget`. Backend contra `mikuna_load_db`
(`DB_URL=jdbc:postgresql://localhost:5432/mikuna_load_db`).

```powershell
cd load-tests
k6 run smoke.js                                             # humo: debe pasar
# Antes/después de la caché (SC-003): misma corrida con el backend arrancado con SPRING_CACHE_TYPE=none
# (caché apagada) y luego sin esa variable
$env:CACHE_LABEL='sin-cache'; k6 run load.js
$env:CACHE_LABEL='cache';     k6 run load.js
k6 run stress.js                                            # límite real (SC-001, RNF-01 parcial)
```

Esperado:

- `load.js` con caché cumple p95 < 2 s y errores < 1 % con 200 usuarios (SC-001).
- Aciertos ≥ 80 % (SC-002).
- p95 de `catalogo` al menos 50 % menor que sin caché (SC-003).
- Los informes quedan en `load-tests/reports/`.

## 4. Dos instancias (US3, solo local)

```powershell
docker run --name mikuna-redis -p 6379:6379 -d redis:8-alpine   # si no está corriendo
# Terminal A y B (mismo DB_URL), perfil redis:
$env:SPRING_PROFILES_ACTIVE='redis'; $env:SERVER_PORT='8080'; .\mvnw.cmd spring-boot:run
$env:SPRING_PROFILES_ACTIVE='redis'; $env:SERVER_PORT='8081'; .\mvnw.cmd spring-boot:run
cd load-tests; $env:BASE_URLS='http://localhost:8080,http://localhost:8081'; k6 run load.js
```

Comprobaciones manuales:

- 5 logins fallidos alternando puertos → el 6.º da `429` en cualquiera de los dos.
- Cambiar un precio por 8080 → se ve en 8081 en la siguiente consulta.

## 5. Redis caído en caliente (SC-005)

Con el perfil `redis` corriendo, ejecutar `docker stop mikuna-redis`:

- el catálogo sigue respondiendo `200`;
- `/actuator/health` muestra `redis: DOWN`;
- el login sigue funcionando.

Después, `docker start mikuna-redis`: la caché vuelve a llenarse.
