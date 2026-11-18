# Data Model: Gestión de estado del pedido (007)

## Pedido (`pedidos`, spec 006) — cambia

`status` (cadena ≤ 16): `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `PREPARING`, `READY`.
Los demás campos siguen inmutables. `REJECTED` y `CANCELLED` son finales; `READY` es final en este incremento
(la spec 008 lo continúa con la asignación).

### Transiciones (FR-007)

| Desde | Hacia | Actor | Efecto en el pago |
| --- | --- | --- | --- |
| PENDING | ACCEPTED | restaurante propietario | capturar el monto reservado |
| PENDING | REJECTED | restaurante propietario | liberar la reserva |
| PENDING | CANCELLED | cliente dueño | liberar la reserva |
| PENDING | CANCELLED | sistema (reserva vencida al aceptar) | reserva ya inexistente; pago `RELEASED` |
| ACCEPTED | PREPARING | restaurante propietario | ninguno |
| PREPARING | READY | restaurante propietario | ninguno |

Cualquier otra combinación → `INVALID_TRANSITION` (409), sin efectos.

## Cambio de estado (`cambios_estado_pedido`, nueva)

| Campo | Notas |
| --- | --- |
| id | UUID |
| order_id | pedido; índice `(order_id, changed_at)` |
| from_status, to_status | cadenas ≤ 16 |
| changed_at | instante del cambio (`Clock`) |
| actor_id | cuenta responsable; nulo si `SYSTEM` |
| actor_role | `RESTAURANT`, `CUSTOMER` o `SYSTEM` |
| reason | opcional, ≤ 200 caracteres (rechazo, cancelación, reserva vencida) |

Solo se inserta junto con un cambio efectivo; nunca se actualiza ni borra.

## Pago y transacción de pago (spec 006) — sin cambios de esquema

Invariante: `PENDING` ⇒ pago `AUTHORIZED`; `ACCEPTED`, `PREPARING` y `READY` ⇒ `CAPTURED`;
`REJECTED` y `CANCELLED` ⇒ `RELEASED`. Reserva vencida: transacción `RELEASE` con código `reservation_expired`.
