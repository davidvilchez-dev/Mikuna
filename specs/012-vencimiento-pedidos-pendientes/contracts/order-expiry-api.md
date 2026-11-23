# Contract: vencimiento de pedidos pendientes

Sin endpoints nuevos. Cambios en respuestas y errores existentes.

## Respuestas de pedido

`OrderResponse` (cliente: `GET /api/customer/orders/{id}`, `POST .../cancel`, confirmación) y
`RestaurantOrderResponse` (restaurante: `GET /api/restaurant/orders`, `GET .../{id}` y acciones) ganan:

| Campo | Tipo | Significado |
| --- | --- | --- |
| `expiresAt` | instante ISO-8601 o `null` | Momento de vencimiento fijado al crear el pedido. |
| `secondsToExpire` | entero ≥ 0 o `null` | Segundos restantes calculados por el servidor al responder; `null` si el pedido no está `PENDING`. Nunca negativo. |

La interfaz calcula su fecha límite local como `momento de recepción + secondsToExpire` (research §7).

## Errores nuevos

| HTTP | `code` | Cuándo | Mensaje |
| --- | --- | --- | --- |
| 409 | `ORDER_EXPIRED` | `POST /api/restaurant/orders/{id}/accept` o `/reject` sobre un pedido cuyo plazo venció (si el barrido aún no lo procesó, la propia petición lo cancela y libera antes de responder). | «El pedido venció porque no se respondió a tiempo» |

El resto de errores de la spec 007 no cambia. La cancelación del cliente (`POST /api/customer/orders/{id}/cancel`)
no cambia (FR-015).

## Avisos (spec 009)

Tipo `CANCELLED`, sin tipo nuevo:

| Destinatario | Texto |
| --- | --- |
| Cliente | «Se canceló tu pedido #XXXX: el restaurante no respondió a tiempo» |
| Restaurante | «Se canceló el pedido #XXXX porque no se respondió a tiempo» |
