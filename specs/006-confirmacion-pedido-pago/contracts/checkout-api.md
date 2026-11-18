# Contract: API de confirmación del pedido y pago

Base: `http://localhost:8080`. JSON UTF-8; errores `ProblemDetail` como en
[auth-api](../../002-registro-acceso/contracts/auth-api.md) y [cart-api](../../005-carrito-direcciones-importes/contracts/cart-api.md).
Campos desconocidos en el JSON → 400. **Todas las rutas son protegidas** (rol `CUSTOMER`):
`Authorization: Bearer <jwt>`. Sin token válido → 401; cuenta `DISABLED` u otro rol → 403. Un pedido ajeno
responde 404. Ninguna ruta es pública.

## Resumen de confirmación

`GET /api/customer/checkout` → 200 `CheckoutResponse`:

```json
{ "cart": { "...": "CartResponse de 005" },
  "ready": true,
  "blockers": [],
  "paymentMethods": [ { "code": "CARD", "label": "Tarjeta de crédito o débito" } ],
  "publishableKey": "pk_test_..." }
```

- `ready = false` y `blockers` (`CART_EMPTY`, `NO_ADDRESS`, `RESTAURANT_UNAVAILABLE`,
  `LINES_UNAVAILABLE`) cuando no se puede confirmar. El resumen es informativo: el servidor vuelve a
  validar al confirmar.
- `publishableKey` es la clave `pk_test_` de `backend/.env`; nunca se devuelve la clave secreta.

## Confirmar el pedido

`POST /api/customer/orders` con el encabezado obligatorio `Idempotency-Key: <uuid>` (1–64 caracteres):

```json
{ "paymentMethod": "CARD", "paymentMethodId": "pm_...", "expectedTotal": 43.85 }
```

- `paymentMethodId`: id de Stripe creado por Stripe.js; nunca números de tarjeta.
- `expectedTotal`: total que vio el cliente; **solo se compara**, el monto reservado es siempre el
  recalculado en el servidor. El cliente no envía precios, restaurante ni dirección (sale del carrito).

`OrderResponse`:

```json
{ "id": "uuid", "status": "PENDING", "restaurant": { "id": "uuid", "name": "..." },
  "lines": [ { "productId": "uuid", "name": "...", "unitPrice": 18.50, "quantity": 2, "lineTotal": 37.00 } ],
  "delivery": { "label": "Casa", "street": "...", "district": "...", "reference": "...", "contactPhone": "..." },
  "totals": { "subtotal": 37.00, "deliveryFee": 5.00, "commission": 1.85, "total": 43.85 },
  "payment": { "method": "CARD", "status": "AUTHORIZED" },
  "createdAt": "ISO-8601" }
```

| Resultado | Cuándo |
| --- | --- |
| 201 `OrderResponse` | Pedido registrado y monto reservado. |
| 200 `OrderResponse` | La clave ya se procesó: devuelve el mismo pedido, sin nueva reserva. |
| 400 | Cuerpo inválido, `Idempotency-Key` ausente o inválida, método no ofrecido. |
| 402 `code=PAYMENT_DECLINED` | Autorización rechazada o tarjeta que exige verificación adicional (`code=PAYMENT_ACTION_REQUIRED`). Sin pedido; carrito intacto. |
| 409 `code=CART_EMPTY` / `NO_ADDRESS` | Falta carrito con líneas o dirección seleccionada. |
| 409 `code=NOT_ORDERABLE` | Restaurante no habilitado/abierto, producto no disponible o retirado (el mensaje lista los productos), cantidad inválida o dirección fuera de zona. Sin pedido ni reserva. |
| 409 `code=AMOUNTS_CHANGED` | `expectedTotal` difiere del total recalculado; incluye el `CheckoutResponse` vigente en `checkout`. Sin pedido ni reserva. |
| 503 `code=PAYMENT_UNAVAILABLE` | La pasarela no respondió o falló. Sin pedido; sin reserva activa; se puede reintentar. |

Mensajes en español, sin datos de tarjeta ni detalles internos de Stripe.

Idempotencia: las confirmaciones del mismo cliente se serializan por su carrito. Una clave ya usada
devuelve el pedido existente (200) antes de cualquier otra validación. Si se reutiliza una clave con otro
`paymentMethodId` o `expectedTotal` distinto, responde 409 `AMOUNTS_CHANGED` en lugar de devolver el
pedido anterior sin avisar.

## Consultar un pedido propio

`GET /api/customer/orders/{id}` → 200 `OrderResponse` (con `payment.status`: `AUTHORIZED`, `CAPTURED` o
`RELEASED`); 404 si no existe o es de otro cliente. El listado y el historial pertenecen a la spec 010.

## Operaciones internas de pago (sin HTTP)

Para la spec 007; no son rutas ni acciones del cliente.

```text
PaymentService.capture(UUID orderId)   // AUTHORIZED -> CAPTURED, cobra el monto total reservado
PaymentService.release(UUID orderId)   // AUTHORIZED -> RELEASED, libera sin cobrar
```

Cada una: bloquea el pago del pedido, rechaza si no está `AUTHORIZED` (`PaymentStateException`, 409
cuando llegue a HTTP), registra la transacción (`CAPTURE`/`RELEASE`) y, si la pasarela falla, registra
`FAILED` sin cambiar el estado, de modo que puede reintentarse. No hay reembolso.

## Pasarela (Stripe, solo pruebas)

Llamadas salientes por HTTPS desde `StripeGateway`, con la clave `sk_test_`:

| Operación | Llamada |
| --- | --- |
| Autorizar | `POST /v1/payment_intents` (`capture_method=manual`, `confirm=true`, `currency` y `amount` en céntimos) |
| Capturar | `POST /v1/payment_intents/{id}/capture` |
| Liberar / compensar | `POST /v1/payment_intents/{id}/cancel` |

Todas con `Idempotency-Key`. Tiempos de espera acotados; un fallo o tiempo agotado se trata como error de
pasarela. Webhooks: aplazados.
