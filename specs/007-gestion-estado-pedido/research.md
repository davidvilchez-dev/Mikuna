# Research: Gestión de estado del pedido (007)

Decisiones puntuales (constitución VII). Tecnologías ya elegidas.

## 1. Serialización de aceptar vs. cancelar

- **Decisión**: `OrderRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`) en una transacción única; se
  valida el estado esperado con la fila bloqueada, luego se llama a `PaymentService` (que bloquea `pagos`) y
  se escribe el cambio. Orden de bloqueo fijo: pedido → pago.
- **Razón**: mismo mecanismo ya usado para `pagos` (006 §8); garantiza un único ganador y que el perdedor vea
  el estado nuevo (409). Un `@Version` optimista no evita que Stripe se invoque dos veces.
- **Alternativas**: actualización condicional `UPDATE ... WHERE status='PENDING'` (no cubre el pago);
  bloqueo en memoria (prohibido como única protección, constitución IV).

## 2. Pago junto con el estado

- **Decisión**: capturar/liberar dentro de la misma transacción. Si Stripe falla (`PaymentUnavailable`), la
  excepción revierte el cambio de estado y el pedido sigue Pendiente (503, reintentable). El fallo ya se
  registra en transacción aparte (`PaymentRecorder`, 006).
- **Razón**: FR-011 exige que estado y pago no diverjan. Las claves de idempotencia de 006 (`capture-<id>`,
  `release-<id>`) hacen seguro el reintento.

## 3. Reserva vencida

- **Decisión**: `StripeGateway.capture` lanza `ReservationExpiredException` ante HTTP 400 con códigos
  `payment_intent_unexpected_state` o `charge_expired_for_capture` (reserva cancelada o vencida).
  `PaymentService.capture` la captura, marca el pago `RELEASED` (código `reservation_expired`) y **devuelve**
  `CaptureResult.RESERVATION_EXPIRED` sin lanzar, para no marcar la transacción como solo-rollback.
  `OrderStatusService.accept` cancela el pedido con actor `SYSTEM` y motivo «reserva vencida» y **devuelve**
  `AcceptOutcome.RESERVATION_EXPIRED` (no lanza, para que la transacción confirme la cancelación y el pago
  `RELEASED`). El controlador, ya fuera de la transacción, responde 409 `RESERVATION_EXPIRED`.
- **Razón**: aclaración 2026-11-17 (Cancelado). Un fallo transitorio sigue siendo 503 y no cambia el pedido.

## 4. Registro de cambios

- **Decisión**: tabla `cambios_estado_pedido` (solo inserciones) escrita en la misma transacción que el
  cambio. Responsable: `actor_id` + `actor_role` (`RESTAURANT`, `CUSTOMER`, `SYSTEM`). No se registra ningún
  intento rechazado.
- **Razón**: FR-012; la consulta del historial es de la spec 010.

## 5. Restaurante propietario

- **Decisión**: `RestaurantRepository.findByOwnerId(token.id)` y comparación con `order.restaurantId`; ajeno o
  inexistente → 404. Restaurante no `APPROVED` → 403 `RESTAURANT_NOT_ENABLED`. El estado de la cuenta ya lo
  filtra `AccountStatusFilter`. No se exige horario abierto: decidir sobre un pedido ya recibido no lo requiere.
- **Razón**: FR-008/FR-009; pedido ajeno sin revelar existencia (misma convención que 006).

## 6. Idempotencia

- **Decisión**: sin cabecera de clave. Repetir una acción ya aplicada encuentra un estado distinto y responde
  409 `INVALID_TRANSITION` sin tocar el pago ni registrar nada; si el efecto falló (503) se reintenta con las
  mismas claves de Stripe.
- **Razón**: las transiciones son únicas por estado de origen; una clave sería redundante.
