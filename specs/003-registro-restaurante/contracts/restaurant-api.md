# Contract: API de restaurantes

Base: `http://localhost:8080`. JSON UTF-8; errores `ProblemDetail` como en
[auth-api](../../002-registro-acceso/contracts/auth-api.md), con `errors` en las validaciones.
Todas las rutas son **protegidas** (`Authorization: Bearer <jwt>`). Sin token válido o con cuenta
inexistente → 401; cuenta `DISABLED` → 403; otro rol → 403. Campos desconocidos (`status`,
`ownerId`, `approved`…) → 400.

## Titular (rol `RESTAURANT`): `/api/restaurant/me`

`RestaurantRequest`: `{ "name", "category", "description"?, "address", "zone", "phone" }`
(reglas en [data-model](../data-model.md)).

`RestaurantResponse`:

```json
{ "id": "uuid", "name": "...", "category": "...", "description": "...", "address": "...",
  "zone": "...", "phone": "...", "status": "PENDING|APPROVED|REJECTED",
  "submittedAt": "ISO-8601", "rejectionReason": null, "resolvedAt": null,
  "available": false,
  "schedule": [ { "day": "MONDAY", "opensAt": "09:00", "closesAt": "21:00" },
                { "day": "SUNDAY", "opensAt": null, "closesAt": null } ],
  "acceptingOrders": false }
```

| Método y ruta | Acción | Resultados |
| --- | --- | --- |
| `POST /api/restaurant/me` | Envía la solicitud (RF-05) | 201 `RestaurantResponse` (`PENDING`); 400 datos inválidos; 409 ya existe una solicitud |
| `GET /api/restaurant/me` | Consulta el propio | 200; 404 aún no hay solicitud |
| `PUT /api/restaurant/me` | Edita datos generales (RF-05/RF-07) | 200 (el estado no cambia); 400; 404; 409 conflicto de edición |
| `POST /api/restaurant/me/resubmit` | Reenvía tras un rechazo (cuerpo vacío) | 200 → `PENDING`; 403 si no está `REJECTED`; 404; 409 estado cambiado |
| `PUT /api/restaurant/me/schedule` | Reemplaza el horario semanal (RF-07) | 200; 400 horas inválidas; 403 si no está `APPROVED`; 404 |
| `PUT /api/restaurant/me/availability` | `{ "available": true }` (RF-07) | 200; 400; 403 si no está `APPROVED`; 404 |

`PUT schedule`: cuerpo `{ "days": [ { "day", "opensAt", "closesAt" } ] }` con los siete días sin
repetir. Abierto exige ambas horas `HH:mm` con `closesAt > opensAt`; cerrado, ambas `null`.

## Administrador (rol `ADMIN`): `/api/admin/restaurants`

`AdminRestaurantResponse` = `RestaurantResponse` + `{ "ownerName", "ownerEmail" }`.

| Método y ruta | Acción | Resultados |
| --- | --- | --- |
| `GET /api/admin/restaurants?status=PENDING` | Lista por estado (por defecto `PENDING`), de la más antigua a la más reciente por `submittedAt` (RF-06) | 200 `[AdminRestaurantResponse]` (vacía si no hay); 400 estado inválido |
| `POST /api/admin/restaurants/{id}/approve` | Aprueba (cuerpo vacío) | 200 `APPROVED` con `resolvedBy` y `resolvedAt`; 404; 409 si ya no está pendiente |
| `POST /api/admin/restaurants/{id}/reject` | `{ "reason": "..." }` (obligatorio, ≤ 300) | 200 `REJECTED`; 400 sin motivo; 404; 409 si ya no está pendiente |

Dos resoluciones simultáneas de la misma solicitud: una responde 200 y la otra 409.

## Matriz de acceso (RNF-07)

| Situación | Resultado |
| --- | --- |
| Sin token, token manipulado o vencido | 401 |
| Cuenta `DISABLED` | 403 |
| Cliente, repartidor o administrador en `/api/restaurant/**` | 403 |
| Cliente, repartidor o restaurante en `/api/admin/**` | 403 |
| Restaurante `PENDING` o `REJECTED` cambia horario o disponibilidad | 403 |
| Restaurante intenta acceder al de otra cuenta | No es posible: `/me` no recibe ids y no devuelve datos ajenos |
