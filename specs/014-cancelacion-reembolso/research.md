# Research: Cancelación administrativa con reembolso y cobro del efectivo al entregar (014)

No quedaron puntos "NEEDS CLARIFICATION" en el contexto técnico; estas son las decisiones de diseño.

## §1 Reembolso en Stripe

- **Decision**: `POST /v1/refunds` con `payment_intent=<intentId>` sin `amount` (reembolso total), desde
  `StripeGateway.refund(intentId, idempotencyKey)`, con la clave de idempotencia `refund-<orderId>`. Estado
  `succeeded` o `pending` en la respuesta = aceptado; `failed` o `canceled` = rechazo. Un 400 con
  `charge_already_refunded` se trata como hecho (la respuesta anterior se perdió).
- **Rationale**: misma integración REST que autorizar, capturar y cancelar (spec 006), sin SDK nuevo. La
  clave de idempotencia y el reconocimiento de `charge_already_refunded` cubren el reintento tras una
  respuesta perdida (FR-006). `pending` cuenta como aceptado porque la acreditación bancaria no la controla
  la plataforma (supuesto de la spec) y no hay webhooks (constitución).
- **Alternatives considered**: reembolso parcial por importe (fuera de alcance); esperar a `succeeded` con
  sondeo o webhook (aplazado por la constitución).

## §2 Transacción y orden de bloqueo

- **Decision**: una sola transacción en `AdminOrderCancellationService.cancel`: bloquear el pedido
  (`findByIdForUpdate`), validar el estado, bloquear el pago (`findByOrderIdForUpdate`), reembolsar o
  liberar, cambiar el estado, y si había repartidor activo bloquear su disponibilidad y marcarlo libre.
  Orden fijo pedido → pago → repartidor, el mismo que aceptar (pedido → pago) y completar una entrega
  (pedido → repartidor).
- **Rationale**: la llamada a la pasarela ocurre con el pedido bloqueado, igual que la captura al aceptar.
  Si la pasarela falla, la excepción revierte todo y el pedido conserva su estado (FR-005). Si el reembolso
  se hizo y el commit falla, el reintento usa la misma clave de idempotencia y no reembolsa dos veces.
  Cancelar y avanzar el pedido (restaurante o repartidor) bloquean la misma fila primero: solo uno
  prevalece (FR-010).
- **Alternatives considered**: reembolsar después del commit (deja un Cancelado sin reembolso si la
  pasarela falla, que es justo lo que FR-005 prohíbe).

## §3 Registro del intento fallido

- **Decision**: el reembolso fallido se registra con `PaymentRecorder` (transacción `REQUIRES_NEW`, ya
  existente) como `REFUND`/`FAILED` con su código; después se lanza `RefundFailedException` (503, código
  `REFUND_FAILED`) y la transacción principal se revierte.
- **Rationale**: mismo patrón que la captura fallida (spec 007): la constancia sobrevive al rollback.

## §4 Estados del pago

- **Decision**: `PaymentStatus` gana `REFUNDED`, alcanzable solo desde `CAPTURED` con tarjeta.
  `TransactionType` gana `REFUND`; `TransactionStatus` gana `REFUNDED`. Para el efectivo, `CAPTURED → RELEASED`
  se permite solo con `isCash()` (efectivo cobrado al aceptar antes de este incremento y luego cancelado:
  edge case de la spec).
- **Rationale**: las columnas de enums son `varchar(16)` sin CHECK (V1): no se necesita migración de
  esquema. `REFUNDED` cabe en 16 caracteres.
- **Alternatives considered**: una tabla de reembolsos aparte (innecesaria: la transacción de pago ya
  guarda importe, resultado y referencia).

## §5 Transiciones del pedido

- **Decision**: `Order.TRANSITIONS` añade `CANCELLED` a `ACCEPTED`, `PREPARING`, `READY`, `ASSIGNED` y
  `PICKED_UP`. La restricción "solo el administrador" la aplica el servicio (ruta `/api/admin/**` con rol
  ADMIN); los demás servicios ya fijan su estado destino (restaurante: avanzar; cliente: `requirePending`;
  repartidor: recoger, entregar o rechazar), así que no ganan la nueva transición.
- **Rationale**: `transitionTo` sigue siendo el único punto de cambio de estado (constitución IV).

## §6 Entrega y repartidor

- **Decision**: la entrega conserva su `courier_id` como registro. El repartidor queda libre porque la
  condición de candidato y la lista de entregas activas ya filtran por pedido `ASSIGNED`/`PICKED_UP`; además
  se llama a `markFree` (reinicia "libre desde") y, tras el commit, a `DeliveryDispatcher.courierBecameFree`
  para ofrecerle una pendiente. Una entrega Lista sin repartidor sale sola de las pendientes porque
  `findPending` exige el pedido en `READY`.
- **Rationale**: no borrar el repartidor permite que el aviso de cancelación le llegue (los destinatarios
  se leen de la entrega después del commit) y que el administrador vea quién la tenía.
- **Alternatives considered**: `Delivery.release()` (deja `courier_id` nulo y el repartidor no recibiría el
  aviso; habría que añadir el repartidor al evento).

## §7 Efectivo cobrado al entregar

- **Decision**: `PaymentService.capture` deja de capturar el efectivo al aceptar (devuelve `CAPTURED` como
  resultado de la operación sin tocar el pago). `CourierDeliveryService.complete` llama a
  `PaymentService.collectCash(orderId)`, que pasa el pago en efectivo `AUTHORIZED → CAPTURED` y registra la
  transacción `CAPTURE`/`CAPTURED` con código `cash`. Si ya estaba `CAPTURED` (efectivo anterior al
  incremento) no hace nada (FR-018). Con tarjeta no hace nada.
- **Rationale**: el momento real del cobro es la entrega (US3); el cambio vive dentro de la transacción
  que marca el pedido Entregado.

## §8 Avisos

- **Decision**: `NotificationRecipients.plan` distingue la cancelación por `ActorRole.ADMIN`: cliente
  "Se canceló tu pedido #x: <motivo>" más "Te devolvimos el pago." si se pagó con tarjeta; restaurante y
  repartidor "Se canceló el pedido #x: <motivo>". `Participants` gana `cardPayment` (del pedido). El
  administrador no recibe aviso (es quien origina). Se reutiliza el tipo `CANCELLED`.
- **Rationale**: con tarjeta, una cancelación administrativa solo se registra si el reembolso se hizo, así
  que "tarjeta + ADMIN" implica reembolso.

## §9 Vista del cliente y del administrador

- **Decision**: `OrderResponse` gana `cancellation {by, reason}` cuando el pedido está Cancelado (último
  cambio de estado). `AdminOrderDetail` gana `transactions` (tipo, estado, importe, código, momento). La
  etiqueta `REFUNDED` = "Reembolsado". En efectivo: "pagarás al recibirlo" mientras está `AUTHORIZED`,
  "Pagado en efectivo" con `CAPTURED`, "Sin cobro" con `RELEASED`.
- **Rationale**: el historial ya muestra el estado del pago; basta con la etiqueta nueva.

## §10 Contrato de la acción

- **Decision**: `POST /api/admin/orders/{id}/cancel` con `{ "reason": "..." }` (1–200 caracteres tras
  recortar espacios). Responde 200 con el `AdminOrderDetail` actualizado. Errores: 400 motivo inválido,
  404 pedido inexistente, 409 `INVALID_TRANSITION` (estado no cancelable o ya cambió), 503
  `REFUND_FAILED` (pasarela).
- **Rationale**: misma forma que las acciones existentes (`/cancel` del cliente, ProblemDetail con `code`).
