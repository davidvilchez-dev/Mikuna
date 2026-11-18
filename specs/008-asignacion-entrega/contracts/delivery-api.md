# Contract: API de disponibilidad, asignación y entrega

Base: `http://localhost:8080`. JSON UTF-8; errores `ProblemDetail` como en
[order-status-api](../../007-gestion-estado-pedido/contracts/order-status-api.md). **Todas las rutas son
protegidas.** Sin token válido → 401; cuenta `DISABLED` o rol distinto → 403. Entrega ajena o inexistente → 404.
El repartidor y el administrador salen siempre del token.

## Repartidor (rol `COURIER`)

| Método y ruta | Efecto |
| --- | --- |
| `GET /api/courier/availability` | `{ "available": bool, "freeSince": instant\|null }`. |
| `PUT /api/courier/availability` | Cuerpo `{ "available": true\|false }`. Al activar, se intenta asignar la entrega pendiente más antigua. 200 con el estado resultante. |
| `GET /api/courier/deliveries` | Entregas activas propias (`ASSIGNED` o `PICKED_UP`); lista de 0 o 1 elemento. |
| `POST /api/courier/deliveries/{orderId}/reject` | ASSIGNED → READY y reasignación a otro candidato. Cuerpo opcional `{ "reason": "..." }` (≤ 200). |
| `POST /api/courier/deliveries/{orderId}/pickup` | Acepta y registra la recogida: ASSIGNED → PICKED_UP. |
| `POST /api/courier/deliveries/{orderId}/complete` | PICKED_UP → DELIVERED; el repartidor queda libre y se intenta asignarle la siguiente pendiente. |

Elemento de entrega (`CourierDeliveryResponse`): `orderId`, `status`, `restaurant` (`name`),
`delivery` (`street`, `district`, `reference`, `phone`, `label`), `lines` (`productName`, `quantity`),
`assignedAt`. Las acciones responden 200 con la entrega actualizada (tras `reject`, `status` es `READY` y ya no
aparece en la lista propia).

## Administrador (rol `ADMIN`)

| Método y ruta | Efecto |
| --- | --- |
| `GET /api/admin/deliveries/pending` | Entregas pendientes de asignación (pedido `READY` sin repartidor), más antiguas primero: `orderId`, `restaurantName`, `delivery.district`, `readyAt`, `candidates` (`courierId`, `name`, `freeSince`) calculados con las reglas de candidato para esa entrega. Incluye también las entregas `ASSIGNED` cuyo repartidor está inhabilitado, con `courierDisabled: true`. |
| `POST /api/admin/deliveries/{orderId}/release` | ASSIGNED → READY cuando el repartidor asignado tiene la cuenta inhabilitada; responsable `ADMIN`, sin registrar rechazo; luego se intenta reasignar. 200 con `PendingDeliveryResponse`. |
| `POST /api/admin/deliveries/{orderId}/assign` | Cuerpo `{ "courierId": "<uuid>" }`. READY → ASSIGNED a un repartidor candidato; responsable `ADMIN`. 200 con `PendingDeliveryResponse` actualizado (`status` `ASSIGNED`, `courierId`). |

## Errores específicos

| Estado | `code` | Cuándo |
| --- | --- | --- |
| 404 | — | Entrega inexistente o de otro repartidor. |
| 409 | `INVALID_TRANSITION` | El estado actual no admite la acción (reenvío, salto, retroceso, entrega ya asignada: estado distinto de READY). |
| 409 | `DELIVERY_CONFLICT` | La actualización condicional no afectó filas (defensa; con el pedido bloqueado normalmente se ve `INVALID_TRANSITION`); sin efecto. |
| 409 | `COURIER_STILL_ENABLED` | `release` sobre una entrega cuyo repartidor sigue habilitado. |
| 409 | `COURIER_NOT_CANDIDATE` | El repartidor elegido está inhabilitado, no disponible, con entrega activa, ya rechazó esa entrega o no existe como repartidor. |
| 400 | — | `courierId` ausente o no UUID, `reason` > 200 caracteres o campos desconocidos. |

## Estados visibles al cliente

`GET /api/customer/orders/{id}` (006) devuelve además `ASSIGNED`, `PICKED_UP` y `DELIVERED` en `status`.
Sin otros cambios; los avisos y la ubicación quedan fuera de alcance.
