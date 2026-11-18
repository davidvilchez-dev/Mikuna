# Data Model: Ubicación, seguimiento y avisos

Tablas nuevas creadas por `ddl-auto=update`. Los ids son UUID. Referencias lógicas (sin FK) como en 006–008.

## CourierLocation — `ubicaciones_repartidor`

| Campo | Tipo | Regla |
| --- | --- | --- |
| courier_id | UUID, PK | Una fila por repartidor |
| order_id | UUID, not null | Pedido de la entrega en curso al enviar |
| latitude | double, not null | −90 a 90 |
| longitude | double, not null | −180 a 180 |
| recorded_at | timestamptz, not null | Marca de la ubicación; solo se reemplaza por una más reciente |

Se borra por `order_id` cuando el pedido pasa a `DELIVERED`, `CANCELLED` o `READY` (liberación). Sin historial.

## DeliveryMilestone — `hitos_entrega`

| Campo | Tipo | Regla |
| --- | --- | --- |
| id | UUID, PK | |
| order_id | UUID, not null | |
| type | `PICKUP` \| `DELIVERY` | |
| latitude, longitude | double, not null | Copiadas de la ubicación vigente |
| recorded_at | timestamptz, not null | Marca de la ubicación copiada |
| created_at | timestamptz, not null | Instante del cambio de estado |

Único `(order_id, type)`. Solo inserción. Si no hay ubicación vigente no se crea el hito.

## Notification — `avisos`

| Campo | Tipo | Regla |
| --- | --- | --- |
| id | UUID, PK | |
| recipient_id | UUID, not null | Cuenta destinataria |
| order_id | UUID, not null | |
| type | enum `NotificationType` | NEW_ORDER, ACCEPTED, REJECTED, CANCELLED, PREPARING, READY, ASSIGNED, PICKED_UP, DELIVERED |
| message | varchar(200), not null | Texto mostrado, en español |
| source_key | varchar(80), not null | Id del `OrderStatusChange` o `PLACED:{orderId}` |
| created_at | timestamptz, not null | |
| read_at | timestamptz, null | Nulo = no leído |

Único `(source_key, recipient_id)`. Índice `(recipient_id, created_at DESC)` y `(recipient_id, read_at)`. Se
conservan mientras exista el pedido; el usuario no los borra.

## Eventos (no persistentes)

- `OrderStatusChangedEvent(changeId, orderId, from, to, actorId, actorRole)`.
- `OrderPlacedEvent(orderId)`.

## Relaciones

`Notification.order_id` → Pedido (006). `CourierLocation.courier_id` → Cuenta con rol repartidor (002).
`DeliveryMilestone.order_id` → Pedido. El estado y sus etapas salen de `cambios_estado_pedido` (007), sin tabla nueva.
