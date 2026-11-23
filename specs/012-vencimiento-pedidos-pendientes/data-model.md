# Data Model: Vencimiento automático de pedidos pendientes

## Pedido (`pedidos`) — ampliado

| Campo | Tipo | Regla |
| --- | --- | --- |
| `expires_at` | instante, nullable en BD | Se fija al crear: `created_at + plazo vigente`. Inmutable (`updatable = false`). Las filas anteriores se rellenan al arrancar con `created_at + plazo` (research §5). |

Índice nuevo: `ix_pedidos_estado_vence (status, expires_at)` para el barrido.

Sin estados ni transiciones nuevas (FR-006). Se usa la ya existente:

| Desde | Hasta | Responsable | Motivo | Efecto en el pago |
| --- | --- | --- | --- | --- |
| PENDING | CANCELLED | sistema (sin cuenta) | `sin respuesta del restaurante` | liberar la reserva (efectivo: sin pasarela) |

Condición, comprobada **con la fila bloqueada**: `status = PENDING` y `expires_at <= ahora`.

## Cambio de estado (`cambios_estado_pedido`) — sin cambios de esquema

El vencimiento se registra como cualquier cambio: `from = PENDING`, `to = CANCELLED`, `actor_role = SYSTEM`,
`actor_id = null`, `reason = "sin respuesta del restaurante"`. Ese motivo identifica el vencimiento en el
historial, la consulta administrativa (FR-016) y el mensaje de FR-014.

## Pago (`pagos`) — sin cambios de esquema

`AUTHORIZED → RELEASED` mediante la operación de liberación existente (spec 006). Si la pasarela ya había
vencido la reserva, la liberación la trata como cancelada sin error.

## Parámetros de la plataforma

| Propiedad | Por defecto | Uso |
| --- | --- | --- |
| `mikuna.orders.response-timeout` | `PT15M` | Plazo para responder; se fija en cada pedido al crearlo. |
| `mikuna.orders.expiry-sweep` | `PT15S` | Intervalo del barrido (≤ 1 min exigido por FR-005). |

## Evento `OrderStatusChangedEvent` — ampliado

Gana `reason` (texto, nullable) para que los avisos distingan el vencimiento (research §8).
