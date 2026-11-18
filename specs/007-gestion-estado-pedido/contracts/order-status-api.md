# Contract: API de estado del pedido

Base: `http://localhost:8080`. JSON UTF-8; errores `ProblemDetail` como en
[checkout-api](../../006-confirmacion-pedido-pago/contracts/checkout-api.md). **Todas las rutas son protegidas.**
Sin token válido → 401; cuenta `DISABLED` o rol distinto → 403. Pedido ajeno o inexistente → 404.
`OrderResponse` es el de 006 con `status` ampliado y `payment.status` (`AUTHORIZED`, `CAPTURED`, `RELEASED`).

## Restaurante (rol `RESTAURANT`)

| Método y ruta | Efecto |
| --- | --- |
| `GET /api/restaurant/orders?status=PENDING` | Lista los pedidos del restaurante propio por estado (por defecto `PENDING`; admite `ACCEPTED`, `PREPARING`, `READY`), más antiguos primero. |
| `GET /api/restaurant/orders/{id}` | Detalle de un pedido propio. |
| `POST /api/restaurant/orders/{id}/accept` | PENDING → ACCEPTED y captura. |
| `POST /api/restaurant/orders/{id}/reject` | PENDING → REJECTED y liberación. Cuerpo opcional `{ "reason": "..." }`. |
| `POST /api/restaurant/orders/{id}/start-preparation` | ACCEPTED → PREPARING. |
| `POST /api/restaurant/orders/{id}/mark-ready` | PREPARING → READY. |

Elemento de la lista: `id`, `status`, `lines`, `delivery`, `totals`, `payment.status`, `createdAt`. Todas las
acciones responden 200 con el pedido actualizado (`RestaurantOrderResponse` para el restaurante,
`OrderResponse` de 006 para el cliente).

## Cliente (rol `CUSTOMER`)

`POST /api/customer/orders/{id}/cancel` — PENDING → CANCELLED y liberación. Cuerpo opcional
`{ "reason": "..." }` (≤ 200). 200 con el pedido actualizado.

## Errores específicos

| Estado | `code` | Cuándo |
| --- | --- | --- |
| 403 | `RESTAURANT_NOT_ENABLED` | El restaurante del token ya no está aprobado. |
| 404 | — | Pedido inexistente o de otro restaurante/cliente. |
| 409 | `INVALID_TRANSITION` | El estado actual no admite la acción (saltos, retrocesos, ya decidido, cancelar tras aceptar: el mensaje indica que no hay reembolsos). |
| 409 | `RESERVATION_EXPIRED` | La reserva venció al aceptar; el pedido quedó Cancelado sin cobro. |
| 503 | `PAYMENT_UNAVAILABLE` | La pasarela falló; el pedido no cambia y se puede reintentar. |
| 400 | — | `reason` de más de 200 caracteres o campos desconocidos. |
