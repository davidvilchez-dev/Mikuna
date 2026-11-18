# Data Model: Historiales y calificaciones

Una tabla nueva (`calificaciones`). El historial no persiste nada: lee `pedidos`, `lineas_pedido`, `pagos`,
`entregas` y `cuentas` de las specs anteriores.

## Calificación (`Rating`, tabla `calificaciones`)

| Campo | Tipo | Reglas |
| --- | --- | --- |
| `id` | UUID | Generado. |
| `order_id` | UUID | Obligatorio, **único** (`uk_calificaciones_pedido`): una calificación por pedido. No actualizable. |
| `customer_id` | UUID | Obligatorio; cliente dueño del pedido, tomado del token. No actualizable. |
| `value` | smallint | Obligatorio, entero de 1 a 5 (`check`). No actualizable. |
| `created_at` | timestamp | Obligatorio; fecha y hora del registro. No actualizable. |

- **Inmutable**: sin actualización ni borrado (FR-014).
- **Reglas de creación**: el pedido existe y es del cliente; está en `DELIVERED`; el valor es 1–5; aún no hay fila
  con ese `order_id`.
- **Índice**: la unicidad sobre `order_id` también sirve para unir la calificación al historial.
- Sin FK dura hacia `pedidos` (convención de 006–009: se relaciona por identificador).

## Índices añadidos a tablas existentes

| Tabla | Índice | Para |
| --- | --- | --- |
| `pedidos` | `ix_pedidos_restaurante_fecha (restaurant_id, created_at)` | Historial del restaurante. |

`ix_pedidos_cliente_fecha` y `ix_entregas_repartidor` ya existen.

## Vistas de lectura (no persistentes)

**HistoryItem** (una fila de cualquiera de los tres historiales):

| Campo | Cliente | Restaurante | Repartidor |
| --- | --- | --- | --- |
| `orderId`, `status`, `createdAt` | ✔ | ✔ | ✔ |
| `restaurant` (id, nombre guardado) | ✔ | — | ✔ |
| `customerName` | — | ✔ | ✔ |
| `deliveryAddress` (calle, distrito, referencia; sin teléfono) | — | — | ✔ |
| `lines` (nombre, precio unitario, cantidad, total de línea, tal como al pedido) | ✔ | ✔ | — |
| `lines` (solo nombre y cantidad) | — | — | ✔ |
| `totals` (subtotal, envío, comisión, total) | ✔ | ✔ | — |
| `deliveryFee` (costo de envío) | — | — | ✔ |
| `payment` (método, estado) | ✔ | ✔ | — |
| `rating` (valor o nulo) | ✔ | ✔ | ✔ |
| `canRate` (`DELIVERED` y sin calificación) | ✔ | — | — |

**HistoryPage**: `items`, `page` (desde 0), `size`, `totalItems`, `totalPages`.

El restaurante no ve dirección ni teléfono del cliente; el repartidor ve solo la dirección de entrega, sin teléfono
ni otros datos de contacto; el repartidor no ve comisión, subtotal, total, precios ni pago (FR-016, FR-017).

## Relaciones

```text
Order 1 ── 0..1 Rating        (por order_id; solo si Order.status = DELIVERED)
Order 1 ── 0..1 Delivery      (courier_id = repartidor que la realizó)
Order 1 ── 1 Payment          (estado del pago)
```
