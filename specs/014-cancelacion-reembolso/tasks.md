# Tasks: Cancelación administrativa con reembolso y cobro del efectivo al entregar (014)

**Input**: Design documents from `specs/014-cancelacion-reembolso/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/admin-order-cancel.md, quickstart.md

**Tests**: incluidos; la constitución (principio V) exige pruebas de integración de las transacciones y
carreras introducidas.

**Organization**: tareas agrupadas por historia de usuario; rutas relativas a la raíz del repositorio.
`B/` = `backend/src/main/java/com/mikuna/backend/`, `BT/` = `backend/src/test/java/com/mikuna/backend/`,
`F/` = `frontend/src/features/`.

## Phase 1: Setup

Sin tareas: no hay dependencias, configuración ni migraciones nuevas (research §4).

---

## Phase 2: Foundational (bloquea todas las historias)

- [X] T001 [P] Añadir `REFUNDED` a `B/payment/model/PaymentStatus.java`, `REFUND` a `B/payment/model/TransactionType.java` y `REFUNDED` a `B/payment/model/TransactionStatus.java` (valores ≤ 16 caracteres, columnas `varchar(16)` sin CHECK)
- [X] T002 [P] En `B/payment/model/Payment.java`: añadir `refund(Instant)` (solo `CAPTURED` → `REFUNDED` y solo si `!isCash()`, si no `PaymentStateException`) y `voidCash(Instant)` (solo efectivo, `AUTHORIZED` o `CAPTURED` → `RELEASED`); actualizar el Javadoc ("No existe estado de reembolso" ya no aplica)
- [X] T003 [P] En `B/order/model/Order.java`: añadir `OrderStatus.CANCELLED` a los destinos de `ACCEPTED`, `PREPARING`, `READY`, `ASSIGNED` y `PICKED_UP` en `TRANSITIONS` y actualizar el Javadoc de `transitionTo` (solo el administrador las usa, spec 014)
- [X] T004 [P] Crear `B/common/exception/RefundFailedException.java` (`CODE = "REFUND_FAILED"`, mensaje "No se pudo reembolsar el pago. El pedido no cambió; inténtalo de nuevo.") y su manejador 503 con `code` en `B/common/exception/GlobalExceptionHandler.java`
- [X] T005 [P] Pruebas unitarias de `Payment.refund`/`voidCash` (orígenes válidos e inválidos) en `BT/payment/model/PaymentTest.java`

**Checkpoint**: el dominio admite el reembolso y las nuevas transiciones.

---

## Phase 3: User Story 1 + 2 - Cancelación con reembolso y sin efectos ante fallo de la pasarela (P1) 🎯 MVP

**Goal**: el administrador cancela un pedido aceptado no entregado con motivo; con tarjeta cobrada se
reembolsa el total una sola vez; si la pasarela falla nada cambia y queda constancia.

**Independent Test**: `AdminOrderCancelIntegrationTest` cancela pedidos en cada estado permitido, comprueba
Cancelado + Reembolsado + transacción + cambio de estado con ADMIN; con el mock de la pasarela fallando,
comprueba que el pedido y el pago no cambian y existe la transacción `REFUND`/`FAILED`.

### Tests

- [X] T006 [P] [US1] Pruebas de `StripeGateway.refund` con el servidor simulado en `BT/payment/service/StripeGatewayTest.java`: `succeeded` y `pending` = hecho; `failed`/`canceled` → `RefundFailedException`; 400 `charge_already_refunded` = hecho; 5xx o red → `PaymentUnavailableException`; envía `payment_intent` y la cabecera `Idempotency-Key`
- [X] T007 [P] [US1] Pruebas de `PaymentService.refund` en `BT/payment/service/PaymentServiceTest.java`: tarjeta `CAPTURED` → llama a la pasarela con `refund-<orderId>`, pago `REFUNDED` y transacción `REFUND`/`REFUNDED`; fallo → `PaymentRecorder` registra `REFUND`/`FAILED` y lanza `RefundFailedException`; efectivo → `voidCash` sin pasarela con transacción `RELEASE`/`RELEASED` código `cash`; tarjeta `AUTHORIZED` → `PaymentStateException`
- [X] T008 [US1] Pruebas de integración en `BT/admin/AdminOrderCancelIntegrationTest.java` (extiende `OrderStatusTestSupport`/`DeliveryTestSupport` según haga falta): cancelación con tarjeta desde ACCEPTED, PREPARING, READY, ASSIGNED y PICKED_UP → 200, pedido `CANCELLED`, pago `REFUNDED`, transacción `REFUND`/`REFUNDED` con el total, cambio de estado con `actor_role = ADMIN` y el motivo; motivo vacío o de más de 200 caracteres → 400 `errors.reason` sin cambios; PENDING, REJECTED, CANCELLED, DELIVERED → 409 `INVALID_TRANSITION` sin cambios; rol no ADMIN → 403; pedido inexistente → 404
- [X] T009 [US1] [US2] En el mismo archivo: con `stripeGateway.refund` lanzando `PaymentUnavailableException` o `RefundFailedException` → 503 `REFUND_FAILED`, pedido sin cambiar de estado, pago `CAPTURED`, transacción `REFUND`/`FAILED`; reintento posterior con la pasarela correcta → Cancelado con exactamente una transacción `REFUND`/`REFUNDED` y la pasarela llamada siempre con la misma clave `refund-<orderId>`
- [X] T010 [US2] Prueba de carreras en `BT/admin/AdminOrderCancelConcurrencyIntegrationTest.java`: 2 cancelaciones simultáneas del mismo pedido y cancelación simultánea con `complete` del repartidor (pedido PICKED_UP), repetidas 20 veces; nunca más de un `REFUND`/`REFUNDED`, nunca `DELIVERED` con pago `REFUNDED`, un único cambio de estado hacia el estado final

### Implementation

- [X] T011 [P] [US1] Añadir `refund(String intentId, String idempotencyKey)` a `B/payment/service/StripeGateway.java`: `POST /refunds` con `payment_intent` (sin `amount`, reembolso total); respuesta `succeeded`/`pending` = hecho, `failed`/`canceled` = `RefundFailedException`; 400 con `charge_already_refunded` = hecho; otros errores = `PaymentUnavailableException` mediante `unavailable("reembolsar", e)`
- [X] T012 [US1] Añadir a `B/payment/service/PaymentService.java` el método `public boolean refundOrVoid(UUID orderId)` (`@Transactional`, se une a la transacción del llamador): bloquea el pago con `findByOrderIdForUpdate`; tarjeta `CAPTURED` → `gateway.refund(gatewayRef, "refund-" + orderId)`, `payment.refund(now)` y transacción `REFUND`/`REFUNDED` (devuelve `true`); ante `PaymentUnavailableException`/`RefundFailedException` registra `REFUND`/`FAILED` con `recorder.record` (código `gateway_unavailable` o `refund_failed`) y lanza `RefundFailedException`; efectivo → `voidCash` y transacción `RELEASE`/`RELEASED` código `cash` (devuelve `false`); cualquier otro estado → `PaymentStateException`
- [X] T013 [P] [US1] Crear `B/admin/dto/CancelOrderRequest.java` (`record CancelOrderRequest(@NotBlank @Size(max = 200) String reason)`, mensajes "Indica el motivo de la cancelación" y "El motivo admite hasta 200 caracteres")
- [X] T014 [US1] Crear `B/admin/service/AdminOrderCancellationService.java` con `@Transactional AdminOrderDetail cancel(UUID adminId, UUID orderId, String reason)`: `orders.findByIdForUpdate` (404 si no existe); estado fuera de {ACCEPTED, PREPARING, READY, ASSIGNED, PICKED_UP} → `InvalidOrderTransitionException`; `paymentService.refundOrVoid(orderId)`; `orderStatus.changeStatus(order, CANCELLED, adminId, ActorRole.ADMIN, reason.strip())`; devuelve `adminOrderService.orderDetail(orderId)`
- [X] T015 [US1] Añadir `@PostMapping("/{id}/cancel")` en `B/admin/controller/AdminOrderController.java` que recibe `@AuthenticationPrincipal AccountPrincipal` y `@Valid @RequestBody CancelOrderRequest` y delega en el servicio; actualizar el Javadoc ("Solo lectura" ya no aplica)

**Checkpoint**: MVP — cancelación y reembolso funcionando y seguros ante fallos y carreras.

---

## Phase 4: User Story 3 - El efectivo se cobra al entregar (P1)

**Goal**: el efectivo queda `AUTHORIZED` (pendiente de cobro) hasta Entregado y pasa a `CAPTURED` al
entregarse; cancelado antes, `RELEASED`.

**Independent Test**: `CashCollectionIntegrationTest` lleva un pedido en efectivo de Pendiente a Entregado
comprobando el pago en cada paso.

- [X] T016 [P] [US3] Pruebas en `BT/payment/CashCollectionIntegrationTest.java`: aceptar en efectivo → pago `AUTHORIZED` y sin transacción `CAPTURE`; entregar → `CAPTURED` con transacción `CAPTURE`/`CAPTURED` código `cash` por el total; pago en efectivo ya `CAPTURED` (anterior al 014) al entregar → sin cambios ni transacción nueva; cancelación administrativa de efectivo aceptado → `RELEASED`, sin llamar a la pasarela; rechazo, cancelación del cliente y vencimiento siguen sin cobro
- [X] T017 [US3] En `B/payment/service/PaymentService.java`: `settle` con efectivo y `CAPTURE` no cambia el pago ni registra transacción y devuelve `CaptureResult.CAPTURED`; añadir `collectCash(UUID orderId)` (bloquea el pago; efectivo `AUTHORIZED` → `capture(now)` + transacción `CAPTURE`/`CAPTURED` código `cash`; efectivo `CAPTURED` o tarjeta → no hace nada)
- [X] T018 [US3] En `B/delivery/service/CourierDeliveryService.java#complete`, tras cambiar a `DELIVERED`, llamar a `paymentService.collectCash(orderId)` dentro de la misma transacción (orden de bloqueo pedido → pago → repartidor)
- [X] T019 [US3] Ajustar las pruebas existentes que esperan el efectivo `CAPTURED` al aceptar (buscar `isCash`/`CASH` en `BT/order` y `BT/payment`) a la nueva regla

---

## Phase 5: User Story 4 - Repartidor liberado y todos avisados (P2)

**Goal**: la entrega deja de estar activa, el repartidor queda libre y cliente, restaurante y repartidor
reciben un aviso con el motivo.

**Independent Test**: cancelar un pedido Asignado y comprobar entregas activas del repartidor, candidatura y
avisos.

- [X] T020 [P] [US4] Pruebas unitarias de `NotificationRecipients.plan` en `BT/notification/service/NotificationRecipientsTest.java` (crear o ampliar): `CANCELLED` por `ADMIN` con tarjeta → cliente "Se canceló tu pedido #x: <motivo>. Te devolvimos el pago.", restaurante y repartidor "Se canceló el pedido #x: <motivo>"; en efectivo sin la frase del reembolso; sin repartidor → 2 avisos; el administrador nunca recibe aviso; las cancelaciones del cliente y por vencimiento no cambian
- [X] T021 [US4] En `B/notification/service/NotificationRecipients.java`: `Participants` gana `boolean cardPayment` (de `order.getPaymentMethod() == PaymentMethod.CARD`); en `plan`, caso `CANCELLED` con `event.role() == ActorRole.ADMIN`, redactar los mensajes de T020 (el truncado a `Notification.MESSAGE_MAX` ya existe); actualizar los constructores de `Participants` en pruebas existentes
- [X] T022 [US4] En `B/admin/service/AdminOrderCancellationService.java`: si el pedido estaba ASSIGNED o PICKED_UP, leer el repartidor de la entrega, `availability.findByIdForUpdate(courierId)` → `markFree(now)` y, tras el commit, `AfterCommit.run(() -> dispatcher.courierBecameFree(courierId))`; la entrega conserva `courier_id` (research §6)
- [X] T023 [US4] Pruebas en `BT/admin/AdminOrderCancelIntegrationTest.java`: pedido ASSIGNED cancelado → no figura en `GET /api/courier/deliveries` del repartidor, el repartidor vuelve a ser candidato (otra entrega READY se le asigna automáticamente), avisos `CANCELLED` exactamente uno para cliente, restaurante y repartidor y ninguno para el admin; pedido READY sin repartidor cancelado → no aparece en las pendientes del admin de entregas

---

## Phase 6: User Story 5 - Cliente y administrador ven el reembolso (P2)

**Goal**: el cliente ve Cancelado por la plataforma, el motivo y Reembolsado; el admin ve la acción de
cancelar y las transacciones.

**Independent Test**: tras cancelar, `GET /api/customer/orders/{id}` trae `cancellation` y `REFUNDED`, y el
detalle admin trae `transactions`; en el frontend, las vistas lo muestran.

### Backend

- [X] T024 [P] [US5] En `B/order/dto/OrderResponse.java`: añadir `Cancellation cancellation` (`record Cancellation(String by, String reason)`, solo con estado `CANCELLED`, `@JsonInclude(NON_NULL)`); los llamadores (`CheckoutTransaction.find`, `OrderStatusService`, `OrderController`) obtienen el último cambio con `OrderStatusChangeRepository.findFirstByOrderIdOrderByChangedAtDesc`
- [X] T025 [P] [US5] En `B/admin/dto/AdminOrderDetail.java` añadir `List<Transaction> transactions` (`record Transaction(String type, String status, BigDecimal amount, String code, Instant at)`, orden cronológico) y poblarlo en `B/admin/service/AdminOrderService.java#orderDetail` desde `PaymentTransactionRepository` (transacciones con `order_id` del pedido)
- [X] T026 [US5] Pruebas: `cancellation` y `REFUNDED` en la respuesta del cliente y en el historial (`BT/admin/AdminOrderCancelIntegrationTest.java`), `transactions` en `BT/admin/AdminOrderDetailIntegrationTest.java`

### Frontend

- [X] T027 [P] [US5] En `F/checkout/checkout-api.ts`: `PaymentStatus` gana `'REFUNDED'` (`paymentStatusLabels.REFUNDED = 'Reembolsado (te devolvimos el total)'`); `paymentStatusLabel` en efectivo: `AUTHORIZED` → "Efectivo: pagarás al recibir el pedido", `CAPTURED` → "Pagado en efectivo", `RELEASED` → "Efectivo: pedido sin cobro"; `Order` gana `cancellation?: { by: string; reason: string | null }`; pruebas en `F/checkout/checkout-api.test.ts`
- [X] T028 [US5] En `F/checkout/OrderPage.tsx`: con pedido Cancelado y `cancellation.by === 'ADMIN'` mostrar "Cancelado por la plataforma" y el motivo; el estado del pago ya usa `paymentStatusLabel`
- [X] T029 [P] [US5] En `F/admin/admin-logic.ts`: `canAdminCancel(status)` (true solo para ACCEPTED, PREPARING, READY, ASSIGNED, PICKED_UP), `validateCancelReason(text)` (obligatorio, ≤ 200 tras recortar), etiqueta `REFUNDED` en `paymentLabel` y etiquetas de transacción (`AUTHORIZATION` "Reserva", `CAPTURE` "Cobro", `RELEASE` "Liberación", `REFUND` "Reembolso"); pruebas en `F/admin/admin-logic.test.ts`
- [X] T030 [US5] En `F/admin/admin-api.ts`: `cancelAdminOrder(token, id, reason)` (`POST /api/admin/orders/{id}/cancel`) y tipo `transactions` en el detalle
- [X] T031 [US5] En `F/admin/AdminOrdersPage.tsx`: en el detalle del pedido, botón "Cancelar pedido" solo si `canAdminCancel`; diálogo con campo de motivo obligatorio (contador hasta 200), confirmar/volver; al confirmar llama a `cancelAdminOrder`, muestra `notifySuccess` ("Pedido cancelado" + "y reembolsado" si el pago queda `REFUNDED`) y refresca el detalle; ante 503 `REFUND_FAILED` o 409 muestra el mensaje del servidor y deja el pedido como está; añadir la sección "Transacciones" (tipo, estado, importe, código, fecha)

---

## Phase 7: Polish & Cross-Cutting

- [X] T032 Ejecutar `./mvnw test` en `backend/` y `pnpm test`, `pnpm lint`, `pnpm exec tsc -b` en `frontend/`; corregir fallos
- [ ] T033 Recorrer `specs/014-cancelacion-reembolso/quickstart.md` (los pasos con la pasarela real se dejan al usuario si no hay claves de prueba)
- [X] T034 [P] Revisar que `specs/014-cancelacion-reembolso/checklists/requirements.md` siga completo tras la implementación

---

## Dependencies & Execution Order

- Phase 2 (T001–T005) bloquea todo.
- US1+US2 (T006–T015) es el MVP; US3 (T016–T019) solo depende de Phase 2 y de T012 (`voidCash` en el
  mismo servicio).
- US4 (T020–T023) depende de T014 (servicio de cancelación).
- US5 backend (T024–T026) depende de T014; frontend (T027–T031) depende de los contratos, no del backend
  terminado.
- Polish al final.

## Parallel Example

```text
Phase 2: T001, T002, T003, T004, T005 en paralelo (archivos distintos).
US1: T006, T007, T011, T013 en paralelo; luego T012 → T014 → T015 → T008, T009, T010.
US5 frontend: T027 y T029 en paralelo; luego T030 → T031, T028.
```

## Implementation Strategy

1. Phase 2 → US1+US2 → validar con T008–T010 (MVP: el dinero vuelve y nada queda incoherente).
2. US3 (efectivo) → US4 (repartidor y avisos) → US5 (vistas).
3. Polish y quickstart.
