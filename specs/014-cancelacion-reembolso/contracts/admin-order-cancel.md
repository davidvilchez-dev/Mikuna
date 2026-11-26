# Contrato: cancelación administrativa y vistas afectadas (014)

## POST `/api/admin/orders/{id}/cancel` (rol ADMIN)

Request:

```json
{ "reason": "El restaurante cerró por un corte de luz" }
```

- `reason`: obligatorio, 1–200 caracteres tras recortar espacios.

Respuestas:

| Código | Cuándo | Cuerpo |
|--------|--------|--------|
| 200 | Cancelado (y reembolsado si correspondía) | `AdminOrderDetail` actualizado |
| 400 | Motivo vacío o demasiado largo | ProblemDetail con `errors.reason` |
| 401/403 | Sin sesión o sin rol ADMIN | ProblemDetail |
| 404 | Pedido inexistente | ProblemDetail |
| 409 | Estado no cancelable o el pedido cambió | ProblemDetail, `code: INVALID_TRANSITION` |
| 503 | La pasarela no reembolsó | ProblemDetail, `code: REFUND_FAILED`; el pedido no cambió |

## `AdminOrderDetail` (GET `/api/admin/orders/{id}`) — campo nuevo

```json
"transactions": [
  { "type": "CAPTURE", "status": "CAPTURED", "amount": 42.50, "code": null, "at": "2026-11-25T18:00:00Z" },
  { "type": "REFUND", "status": "REFUNDED", "amount": 42.50, "code": null, "at": "2026-11-25T18:20:00Z" }
]
```

`payment.status` puede ser `REFUNDED`.

## `OrderResponse` (GET `/api/customer/orders/{id}`) — campo nuevo

Solo cuando `status = CANCELLED`:

```json
"cancellation": { "by": "ADMIN", "reason": "El restaurante cerró por un corte de luz" }
```

`by`: `CUSTOMER`, `RESTAURANT`, `SYSTEM` o `ADMIN`. `payment.status` puede ser `REFUNDED`.

## Historial (`HistoryItem.payment.status`)

Puede ser `REFUNDED`.

## Avisos (tipo `CANCELLED`, actor ADMIN)

- Cliente: `Se canceló tu pedido #xxxxxxxx: <motivo>. Te devolvimos el pago.` (sin la última frase en
  efectivo).
- Restaurante: `Se canceló el pedido #xxxxxxxx: <motivo>`.
- Repartidor (si tenía la entrega): `Se canceló el pedido #xxxxxxxx: <motivo>`.
