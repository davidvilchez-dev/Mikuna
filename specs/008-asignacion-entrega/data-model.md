# Data Model: Disponibilidad, asignación y entrega (008)

Tablas nuevas por `ddl-auto=update`. Los enums se guardan como texto (`@Enumerated(STRING)`); las columnas de
enums nuevos sobre tablas existentes no llevan restricciones CHECK que `update` no pueda ampliar (`pedidos.status`
y `cambios_estado_pedido.*` ya son cadenas de 16 caracteres).

## Cambios en entidades existentes

| Entidad | Cambio |
| --- | --- |
| `OrderStatus` | + `ASSIGNED`, `PICKED_UP`, `DELIVERED` (≤ 16 caracteres). |
| `ActorRole` | + `COURIER`, `ADMIN` (junto a `RESTAURANT`, `CUSTOMER`, `SYSTEM`). |
| `Order.transitionTo` | + `READY→ASSIGNED`, `ASSIGNED→PICKED_UP`, `ASSIGNED→READY` (solo rechazo), `PICKED_UP→DELIVERED`. `DELIVERED` es final. |

## CourierAvailability (`repartidores_disponibilidad`)

| Campo | Tipo | Notas |
| --- | --- | --- |
| `courierId` | UUID, PK | Id de la cuenta con rol `COURIER`; no es FK declarada, se valida en el servicio. |
| `available` | boolean, no nulo | Disponibilidad indicada por el repartidor (FR-001). |
| `freeSince` | Instant, no nulo | Desde cuándo está libre: al activarse y al terminar una entrega (research §3). |
| `updatedAt` | Instant, no nulo | Última modificación. |

La ausencia de fila equivale a no disponible. Se crea al primer cambio de disponibilidad.

## Delivery (`entregas`)

| Campo | Tipo | Notas |
| --- | --- | --- |
| `id` | UUID, PK | Generado. |
| `orderId` | UUID, único, no nulo | Una entrega por pedido; se crea al quedar el pedido `READY`. |
| `courierId` | UUID, nulo | Repartidor activo; nulo = pendiente de asignación. Se conserva tras `DELIVERED` como constancia. |
| `readyAt` | Instant, no nulo | Cuándo quedó lista; ordena las pendientes (más antigua primero). |
| `assignedAt` | Instant, nulo | Última asignación. |

Índices: único en `order_id`; `(courier_id)`; `(ready_at)`.

**Invariante (RNF-05):** como máximo un `courierId`; solo cambia de nulo a un valor mediante
`UPDATE ... WHERE courier_id IS NULL` (1 fila afectada) y vuelve a nulo en el rechazo, siempre con el pedido
bloqueado. Una entrega activa = `courierId` no nulo y pedido en `ASSIGNED` o `PICKED_UP`.

## DeliveryRejection (`rechazos_entrega`)

| Campo | Tipo | Notas |
| --- | --- | --- |
| `id` | UUID, PK | Generado. |
| `deliveryId` | UUID, no nulo | Entrega rechazada. |
| `courierId` | UUID, no nulo | Quien la rechazó. |
| `rejectedAt` | Instant, no nulo | Fecha y hora. |

Único `(delivery_id, courier_id)`: un repartidor que rechazó no vuelve a ser candidato de esa entrega, ni
automática ni manualmente (FR-008, FR-010).

## Registro de cambios (spec 007, sin tabla nueva)

`cambios_estado_pedido` registra cada cambio con estado anterior, nuevo, fecha y responsable (FR-014):

| Operación | Cambio | Actor |
| --- | --- | --- |
| Asignación automática | `READY → ASSIGNED` | `SYSTEM`, sin id |
| Asignación manual | `READY → ASSIGNED` | `ADMIN`, id de la cuenta |
| Recogida (acepta y retira) | `ASSIGNED → PICKED_UP` | `COURIER` |
| Entrega al cliente | `PICKED_UP → DELIVERED` | `COURIER` |
| Rechazo del repartidor | `ASSIGNED → READY` (motivo opcional) | `COURIER` |
| Liberación (repartidor inhabilitado) | `ASSIGNED → READY`, sin `DeliveryRejection` | `ADMIN`, id de la cuenta |

## Transiciones

```text
READY ──asignar (sistema o admin)──▶ ASSIGNED ──pickup──▶ PICKED_UP ──complete──▶ DELIVERED
  ▲                                      │
  └────────────── reject (repartidor) ───┘   (la entrega queda sin repartidor y se reasigna)
```

## Reglas de validación

- Candidato: cuenta `ENABLED`, `available = true`, sin entrega activa y sin rechazo previo de esa entrega
  (FR-002, FR-010).
- `pickup` y `reject` solo desde `ASSIGNED` y por el `courierId` de la entrega; `complete` solo desde
  `PICKED_UP` y por el mismo repartidor; cualquier otro estado → 409.
- Desactivar la disponibilidad no toca una entrega ya asignada (US1.5).
