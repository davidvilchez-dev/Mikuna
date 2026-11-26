# Data Model: 014

Sin migración de esquema: las columnas de enums son `varchar(16)` sin CHECK (V1) y los valores nuevos caben.

## Pedido (`pedidos`) — sin columnas nuevas

Transiciones (en negrita las nuevas, solo por el administrador con motivo):

| Desde | Hacia |
|-------|-------|
| PENDING | ACCEPTED, REJECTED, CANCELLED |
| ACCEPTED | PREPARING, **CANCELLED** |
| PREPARING | READY, **CANCELLED** |
| READY | ASSIGNED, **CANCELLED** |
| ASSIGNED | PICKED_UP, READY, **CANCELLED** |
| PICKED_UP | DELIVERED, **CANCELLED** |
| REJECTED, CANCELLED, DELIVERED | (finales) |

## Pago (`pagos.status`)

| Estado | Significado | Desde |
|--------|-------------|-------|
| AUTHORIZED | Tarjeta: reservado. Efectivo: pendiente de cobro | (inicial) |
| CAPTURED | Tarjeta: cobrado al aceptar. Efectivo: cobrado al entregar | AUTHORIZED |
| RELEASED | Sin cobro | AUTHORIZED; efectivo CAPTURED (anterior al 014) al cancelar |
| **REFUNDED** | Tarjeta cobrada y devuelta por completo | CAPTURED con tarjeta |

## Transacción de pago (`transacciones_pago`)

- `type` gana **REFUND**; `status` gana **REFUNDED**.
- Reembolso correcto: `REFUND`/`REFUNDED`, importe = total del pago, `gateway_ref` = intento de Stripe.
- Reembolso fallido: `REFUND`/`FAILED`, `result_code` = código de la pasarela o `gateway_unavailable`
  (registrado aparte, sobrevive al rollback).
- Cobro del efectivo al entregar: `CAPTURE`/`CAPTURED`, `result_code` = `cash`.
- Cancelación de efectivo: `RELEASE`/`RELEASED`, `result_code` = `cash`.

## Cambio de estado (`cambios_estado_pedido`) — sin cambios

`actor_role = ADMIN`, `actor_id` = administrador, `reason` = motivo (1–200).

## Entrega y disponibilidad — sin cambios de forma

- `entregas.courier_id` se conserva como registro; la entrega deja de estar activa porque su pedido ya no
  está ASSIGNED/PICKED_UP.
- `repartidores_disponibilidad.free_since` se reinicia (`markFree`).

## Invariantes

- Un pedido Cancelado con tarjeta cobrada tiene el pago REFUNDED (nunca CAPTURED).
- Un pedido Entregado nunca tiene el pago REFUNDED.
- A lo sumo una transacción `REFUND`/`REFUNDED` por pedido.
- Un pedido en efectivo no Entregado nunca queda con el pago CAPTURED por este incremento.
