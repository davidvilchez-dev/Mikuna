# Contrato HTTP: ubicación, seguimiento y avisos

Todos los endpoints son **protegidos** (token); ninguno es público. Errores en `application/problem+json`, como
en 006–008. El usuario siempre sale del token.

## RF-26 — Ubicación del repartidor (rol COURIER)

### `PUT /api/courier/location`

Cuerpo:

```json
{ "latitude": -13.1588, "longitude": -74.2236, "recordedAt": "2026-11-18T15:04:05Z" }
```

| Caso | Respuesta |
| --- | --- |
| Guardada (o ignorada por ser más antigua que la vigente) | `204` |
| Coordenadas fuera de rango o faltantes; `recordedAt` futuro (> 1 min) | `400` con detalle del campo |
| Sin entrega en curso (Asignada/Recogida) | `409` «No tienes una entrega en curso» |
| Sin sesión / rol distinto | `401` / `403` |

## RF-27 / RF-28 — Seguimiento (rol CUSTOMER)

### `GET /api/customer/orders/{id}/tracking`

```json
{
  "orderId": "…", "status": "PICKED_UP",
  "stages": [
    { "status": "PENDING",   "at": "2026-11-18T14:50:00Z" },
    { "status": "ACCEPTED",  "at": "2026-11-18T14:52:10Z" }
  ],
  "courierLocation": { "latitude": -13.1588, "longitude": -74.2236, "updatedAt": "2026-11-18T15:04:05Z" },
  "locationUnavailableReason": null
}
```

- `stages`: etapas cumplidas en orden cronológico (fecha y hora; no expone actores).
- `courierLocation` solo está presente si el pedido es del cliente del token y está `PICKED_UP` y hay ubicación;
  en otro caso es `null` y `locationUnavailableReason` es uno de `NOT_PICKED_UP`, `FINISHED`, `NO_LOCATION_YET`.
- Pedido ajeno o inexistente → `404`; sin sesión → `401`; otro rol → `403`.

## RF-29 — Avisos (roles CUSTOMER, RESTAURANT, COURIER)

| Método y ruta | Descripción | Respuestas |
| --- | --- | --- |
| `GET /api/notifications?unreadOnly=false&page=0&size=20` | Avisos propios, del más reciente al más antiguo | `200` página: `{ items: [{ id, orderId, type, message, createdAt, read }], page, size, totalItems, totalPages }` |
| `GET /api/notifications/unread-count` | Contador de no leídos | `200` `{ "unread": 3 }` |
| `POST /api/notifications/{id}/read` | Marca un aviso propio como leído (idempotente) | `204`; ajeno o inexistente `404` |
| `POST /api/notifications/read-all` | Marca todos los propios como leídos | `204` |

`size` máximo 50. Sin sesión → `401`; administrador → `403`.

## Reglas transversales

- Los avisos se generan por los cambios de las specs 006–008 sin modificar sus contratos.
- Los hitos de recogida y entrega se guardan al ejecutarse `pickup` y `deliver` (spec 008); no hay endpoint.
