# Contrato HTTP: historiales y calificaciones

Todos los endpoints son **protegidos** (token); ninguno es público. Errores en `application/problem+json`, como
en 006–009. El usuario siempre sale del token.

## Parámetros comunes de los tres historiales

| Parámetro | Tipo | Descripción |
| --- | --- | --- |
| `page` | entero ≥ 0 | Página, desde 0 (por defecto 0). |
| `size` | entero 1–100 | Tamaño (por defecto 20). |
| `status` | texto, opcional | Uno de `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `PREPARING`, `READY`, `ASSIGNED`, `PICKED_UP`, `DELIVERED`. |
| `from`, `to` | `yyyy-MM-dd`, opcionales | Rango inclusivo sobre la fecha de creación del pedido. |

Orden fijo: del más reciente al más antiguo. `status` inválido, `from` > `to`, fecha mal formada o `size`
fuera de rango → `400`. Página fuera de rango → `200` con `items: []`. Sin sesión → `401`; rol distinto → `403`.

## RF-30 — Historial del cliente (rol CUSTOMER)

### `GET /api/customer/orders/history`

```json
{
  "items": [
    {
      "orderId": "…", "status": "DELIVERED", "createdAt": "2026-11-18T15:04:05Z",
      "restaurant": { "id": "…", "name": "Pollería Don Pepe" },
      "lines": [ { "productId": "…", "name": "Pollo a la brasa", "unitPrice": 32.00, "quantity": 1, "lineTotal": 32.00 } ],
      "totals": { "subtotal": 32.00, "deliveryFee": 5.00, "commission": 1.60, "total": 38.60 },
      "payment": { "method": "CARD", "status": "CAPTURED" },
      "rating": 5, "canRate": false
    }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

Incluye todos los estados, también `REJECTED` y `CANCELLED`. Solo pedidos del cliente del token.

## RF-30 — Historial del restaurante (rol RESTAURANT)

### `GET /api/restaurant/orders/history`

Mismos campos, con `customerName` en lugar de `restaurant` y sin `canRate`. Solo pedidos del restaurante propio.
Sin dirección ni contacto del cliente. Se resuelve el establecimiento del token **sin exigir que esté habilitado
para recibir pedidos**: un restaurante inhabilitado conserva su historial. Cuenta sin establecimiento registrado →
`403`; cuenta bloqueada → no inicia sesión (`401`, RF-04).

## RF-30 — Historial del repartidor (rol COURIER)

### `GET /api/courier/deliveries/history`

Campos: `orderId`, `status`, `createdAt`, `restaurant`, `customerName`, `deliveryAddress`
(`street`, `district`, `reference`), `lines` (solo `name` y `quantity`), `deliveryFee` y `rating`. Sin `payment`,
`totals`, `commission`, precios ni teléfono. Solo entregas con el repartidor del token a cargo y pedido `ASSIGNED`,
`PICKED_UP` o `DELIVERED`, aunque el repartidor ya no esté habilitado para recibir entregas. El filtro `status` solo
admite esos tres valores (otro estado → `400`).

## RF-31 — Calificar un pedido entregado (rol CUSTOMER)

### `POST /api/customer/orders/{id}/rating`

Cuerpo: `{ "value": 4 }`

| Caso | Respuesta |
| --- | --- |
| Registrada | `201` con `{ "orderId": "…", "value": 4, "createdAt": "…" }` |
| `value` ausente, no entero o fuera de 1–5 | `400` con detalle del campo; no se registra nada |
| Pedido inexistente o de otro cliente | `404` (sin revelar si existe) |
| Pedido que no está `DELIVERED` | `409` código `ORDER_NOT_DELIVERED` |
| Pedido ya calificado (incluye envíos simultáneos) | `409` código `RATING_ALREADY_EXISTS`; la original no cambia |
| Sin sesión / rol distinto | `401` / `403` |

No existen endpoints para editar o eliminar una calificación (FR-014).
