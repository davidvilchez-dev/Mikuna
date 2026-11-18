---

description: "Tareas de implementación: aceptación, rechazo, preparación y cancelación del pedido (RF-18, RF-19, RF-20, RF-04)"
---

# Tasks: Aceptación, rechazo, preparación y cancelación del pedido

**Input**: Documentos de diseño en `specs/007-gestion-estado-pedido/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/order-status-api.md](./contracts/order-status-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra PostgreSQL local
(`mikuna_test_db`) con `StripeGateway` **simulado** (nunca Stripe real) y ≥ 90 % de líneas de lógica de negocio.
Vitest para la lógica del frontend. Sin Playwright ni otra herramienta E2E.

**Organization**: Tareas agrupadas por historia de usuario. US1 (aceptar/rechazar) es el MVP; US2 (cancelar) y
US3 (carrera) comparten el mecanismo de transición de US1; US4 (avance) y US5 (registro) se apoyan en él.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1–US5 según [spec.md](./spec.md)
- Rutas abreviadas: `BE/` = `backend/src/main/java/com/mikuna/backend/`; `BT/` =
  `backend/src/test/java/com/mikuna/backend/`; `FE/` = `frontend/src/`.
- Nunca abrir, copiar ni imprimir `backend/.env`.
- Las pruebas de integración extienden `OrderTestSupport` (`BT/support/`) como las de 006; revisar sus
  utilidades antes de crear otras.

---

## Phase 1: Setup

Sin tareas: no hay dependencias nuevas ni configuración (plan: «ninguna nueva»).

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: estados, transición validada, registro de cambios y errores comunes.

- [X] T001 Ampliar `BE/order/model/OrderStatus.java` con `PENDING, ACCEPTED, REJECTED, CANCELLED, PREPARING, READY` (cadena ≤ 16 caracteres en `pedidos.status`) y añadir `BE/order/model/ActorRole.java` (`RESTAURANT, CUSTOMER, SYSTEM`).
- [X] T002 Crear `BE/order/model/OrderStatusChange.java` (tabla `cambios_estado_pedido`, solo inserciones): `id` UUID, `order_id`, `from_status` y `to_status` (≤ 16), `changed_at`, `actor_id` (nulo si `SYSTEM`), `actor_role` y `reason` opcional **≤ 200 caracteres**; índice `(order_id, changed_at)`. Con `BE/order/repository/OrderStatusChangeRepository.java`.
- [X] T003 En `BE/order/model/Order.java` añadir (el campo `status` ya es actualizable) `transitionTo(OrderStatus target)` que aplique **únicamente** la tabla de `data-model.md` (PENDING→ACCEPTED/REJECTED/CANCELLED, ACCEPTED→PREPARING, PREPARING→READY) y lance `InvalidOrderTransitionException` en cualquier otra; `REJECTED`, `CANCELLED` y `READY` son finales.
- [X] T004 [P] Crear en `BE/common/exception/`: `InvalidOrderTransitionException` (409, `code=INVALID_TRANSITION`, mensaje configurable; el de cancelar tras aceptar menciona que ya fue cobrado y no hay reembolsos), `RestaurantNotEnabledException` (403, `code=RESTAURANT_NOT_ENABLED`) y `ReservationExpiredException` (solo para el gateway; el 409 `RESERVATION_EXPIRED` lo emite el controlador tras el commit, no el handler). Registrar los handlers de las otras dos en `GlobalExceptionHandler.java`; `OrderNotFoundException` (404) ya existe.
- [X] T005 En `BE/order/repository/OrderRepository.java` añadir `findByIdForUpdate(UUID)` (`@Lock(PESSIMISTIC_WRITE)`) y `findByRestaurantIdAndStatusOrderByCreatedAtAsc(UUID, OrderStatus)`.
- [X] T006 En `BE/payment/service/StripeGateway.java`, `capture` lanza `ReservationExpiredException` ante HTTP 400 con código `payment_intent_unexpected_state` o `charge_expired_for_capture`; cualquier otro fallo sigue siendo `PaymentUnavailableException`. En `BE/payment/service/PaymentService.java`, `capture(UUID)` devuelve `CaptureResult` (`CAPTURED` | `RESERVATION_EXPIRED`): ante la excepción marca el pago `RELEASED`, registra una transacción `RELEASE` con código `reservation_expired` y **devuelve** el resultado sin lanzar (research §3). Añadir `CaptureResult` en `BE/payment/service/`.
- [X] T007 [P] Pruebas unitarias en `BT/order/model/OrderTransitionTest.java` (las 6 transiciones válidas y todas las inválidas, finales sin salida) y en `BT/payment/service/PaymentServiceCaptureExpiredTest.java` (reserva vencida → `RESERVATION_EXPIRED` y pago `RELEASED`; fallo transitorio → excepción 503 y pago intacto). Adaptar las pruebas existentes de `PaymentService.capture` al nuevo retorno.
- [X] T008 Crear `BE/order/service/OrderStatusService.java` con el esqueleto común, `@Transactional`: cargar con `findByIdForUpdate`, comprobar propiedad (404 si ajeno), `order.transitionTo(...)`, guardar un `OrderStatusChange` (`Clock`, actor, motivo) y devolver la entidad `Order` actualizada (cada controlador la mapea a su DTO). Orden de bloqueo fijo pedido → pago. Las operaciones concretas se añaden en cada historia.

**Checkpoint**: el modelo de transición, el registro y los errores están listos.

---

## Phase 3: User Story 1 - El restaurante acepta o rechaza (Priority: P1) 🎯 MVP

**Goal**: el restaurante ve sus pendientes y los acepta (cobro) o rechaza (liberación) (RF-18).

**Independent Test**: con dos pedidos pendientes, aceptar uno (ACCEPTED, pago CAPTURED) y rechazar otro
(REJECTED, RELEASED).

- [X] T009 [US1] En `BE/order/service/OrderStatusService.java` añadir `accept(UUID accountId, UUID orderId)` y `reject(UUID accountId, UUID orderId, String reason)`: resolver el restaurante con `RestaurantRepository.findByOwnerId` (sin restaurante o pedido de otro → 404; restaurante no `APPROVED` → `RestaurantNotEnabledException`); aceptar llama a `PaymentService.capture` y, si devuelve `RESERVATION_EXPIRED`, cancela el pedido con actor `SYSTEM` y motivo «reserva vencida» y **devuelve** `AcceptOutcome.RESERVATION_EXPIRED` sin lanzar (lanzar revertiría la cancelación); el controlador (T011) lo traduce a 409 `RESERVATION_EXPIRED` tras el commit; rechazar llama a `PaymentService.release`. Un `PaymentUnavailableException` revierte todo y el pedido sigue PENDING.
- [X] T010 [P] [US1] Crear `BE/order/dto/DecisionRequest.java` (`reason` opcional, `@Size(max = 200)`, campos desconocidos → 400) y `BE/order/dto/RestaurantOrderResponse.java` (según el contrato: `id`, `status`, `lines`, `delivery`, `totals`, `payment.status`, `createdAt`). Los listados y las acciones del restaurante devuelven `RestaurantOrderResponse`; el `cancel` del cliente devuelve el `OrderResponse` de 006.
- [X] T011 [US1] Crear `BE/order/controller/RestaurantOrderController.java` en `/api/restaurant/orders`: `GET ?status=PENDING` (por defecto `PENDING`; solo `PENDING`, `ACCEPTED`, `PREPARING`, `READY`, otro → 400), `GET /{id}`, `POST /{id}/accept`, `POST /{id}/reject`. Cuenta y restaurante salen del token; la ruta ya exige rol `RESTAURANT` (`SecurityConfig`).
- [X] T012 [P] [US1] Pruebas unitarias con Mockito en `BT/order/service/OrderStatusServiceTest.java` (agrupa T012, T018 y T023; aceptar cobra una vez, rechazar libera una vez, reserva vencida → Cancelado/SYSTEM, fallo 503 no cambia estado, pedido ajeno → 404, restaurante no aprobado → 403).
- [X] T013 [US1] Pruebas de integración en `BT/order/RestaurantOrderDecisionIntegrationTest.java` (401 sin token, 403 con rol cliente o cuenta deshabilitada, lista solo los propios, aceptar → ACCEPTED/CAPTURED con `StripeGateway.capture` invocado una vez, rechazar → REJECTED/RELEASED sin captura, pedido de otro restaurante → 404 sin cambios, decidir de nuevo → 409 `INVALID_TRANSITION` sin segunda llamada a Stripe, `reason` de 201 caracteres → 400, reserva vencida → 409 `RESERVATION_EXPIRED` y, consultando la BD, pedido CANCELLED, pago RELEASED y un `cambios_estado_pedido` con rol `SYSTEM`; rechazar con pasarela caída → 503 y pedido sigue PENDING).
- [X] T014 [P] [US1] Frontend: `FE/features/restaurant/orders-api.ts` (fetch con token: listar, aceptar, rechazar, avanzar), `FE/features/restaurant/orders-logic.ts` (etiquetas en español por estado y acción siguiente permitida: PENDING→aceptar/rechazar, ACCEPTED→«En preparación», PREPARING→«Listo para recoger») y `FE/features/restaurant/OrdersPage.tsx` (reutiliza `restaurant.css`; no se creó `orders.css`) con lista de pendientes y botones aceptar/rechazar con motivo opcional; mostrar el mensaje de 503; ante 409 recargar el pedido y mostrar su estado actual en lugar de un error. Ruta `/mi-restaurante/pedidos` en `FE/App.tsx` y enlace desde `FE/features/restaurant/RestaurantPage.tsx`.
- [X] T015 [P] [US1] Prueba Vitest `FE/features/restaurant/orders-logic.test.ts`; añadir `src/features/restaurant/orders-logic.ts` a `test.coverage.include` en `frontend/vite.config.ts` (mantener `thresholds.lines: 90`).

**Checkpoint**: aceptar y rechazar funcionan con cobro y liberación coherentes.

---

## Phase 4: User Story 2 - El cliente cancela su pedido pendiente (Priority: P1)

**Goal**: cancelar un pedido propio Pendiente libera la reserva; después de aceptado, se rechaza (RF-20).

**Independent Test**: cancelar un Pendiente propio (CANCELLED, RELEASED); intentar con uno Aceptado (409).

- [X] T016 [US2] En `BE/order/service/OrderStatusService.java` añadir `cancel(UUID customerId, UUID orderId, String reason)`: pedido de otro cliente → 404 (`findByIdForUpdate` + comparar `customerId`); desde estado distinto de PENDING lanzar `InvalidOrderTransitionException` con el mensaje de «ya aceptado y cobrado, sin reembolso» si está ACCEPTED/PREPARING/READY, o genérico si ya REJECTED/CANCELLED; en PENDING llamar a `PaymentService.release` y registrar el cambio con actor `CUSTOMER`.
- [X] T017 [US2] En `BE/order/controller/OrderController.java` añadir `POST /{id}/cancel` con `DecisionRequest` opcional; devuelve el `OrderResponse` actualizado.
- [X] T018 [P] [US2] Pruebas unitarias en `BT/order/service/OrderStatusServiceTest.java` (cubierto junto con T012; cancelar libera una vez, mensajes por estado, pedido ajeno).
- [X] T019 [US2] Pruebas de integración en `BT/order/OrderCancellationIntegrationTest.java` (401/403, cancelar Pendiente propio → CANCELLED/RELEASED sin captura, ya Aceptado/En preparación/Listo → 409 sin cambios, ya Rechazado/Cancelado → 409 sin segunda liberación, pedido de otro cliente → 404, fallo de pasarela → 503 y pedido sigue PENDING).
- [X] T020 [P] [US2] Frontend: en `FE/features/checkout/OrderPage.tsx` y `checkout-api.ts` añadir el botón «Cancelar pedido» solo con estado PENDING y mostrar los estados nuevos con sus etiquetas; mostrar el mensaje de 409 («ya fue aceptado y cobrado, no hay reembolsos») y recargar el pedido para mostrar su estado actual.

**Checkpoint**: el cliente cancela solo lo cancelable y la reserva se libera.

---

## Phase 5: User Story 3 - Decisiones simultáneas (Priority: P1)

**Goal**: un único resultado coherente ante acciones concurrentes sobre un pedido (FR-010, FR-015).

**Independent Test**: lanzar aceptar y cancelar a la vez sobre el mismo Pendiente, muchas veces; siempre exactamente
uno prevalece.

- [X] T021 [US3] Pruebas de integración concurrentes en `BT/order/OrderDecisionConcurrencyIntegrationTest.java` (hilos con `CountDownLatch`, patrón de `OrderIdempotencyConcurrencyIntegrationTest`): (a) aceptar vs. cancelar × 30 repeticiones: resultado ACCEPTED+CAPTURED **o** CANCELLED+RELEASED, nunca mezclado; el perdedor recibe 409; `capture` + `cancel` de Stripe suman exactamente una llamada por pedido; (b) dos aceptaciones simultáneas → un solo cobro; (c) dos rechazos → una sola liberación; (d) un solo `OrderStatusChange` por transición efectiva. Si la prueba revela interbloqueo u orden de bloqueo distinto, corregir `OrderStatusService` (pedido antes que pago).

**Checkpoint**: la coherencia entre estado y pago se demuestra bajo carrera.

---

## Phase 6: User Story 4 - El restaurante avanza la preparación (Priority: P2)

**Goal**: ACCEPTED → PREPARING → READY sin saltos ni retrocesos; READY queda disponible para la spec 008 (RF-19).

**Independent Test**: avanzar un Aceptado a En preparación y a Listo; los saltos y retrocesos dan 409.

- [X] T022 [US4] En `BE/order/service/OrderStatusService.java` añadir `startPreparation` y `markReady` (mismo control de propiedad y restaurante habilitado que T009; sin llamada al pago) y en `BE/order/controller/RestaurantOrderController.java` `POST /{id}/start-preparation` y `POST /{id}/mark-ready`.
- [X] T023 [P] [US4] Pruebas unitarias en `BT/order/service/OrderStatusServiceTest.java` (cubierto junto con T012; secuencia válida, saltar a READY desde ACCEPTED, retroceder, avanzar PENDING/REJECTED/CANCELLED, ajeno → 404).
- [X] T024 [US4] Pruebas de integración en `BT/order/OrderProgressIntegrationTest.java` (401/403, secuencia completa con pago `CAPTURED` intacto y sin llamadas adicionales a Stripe, saltos y retrocesos → 409 sin cambios, restaurante ajeno → 404, `GET ?status=READY` lista el pedido listo para la asignación).
- [X] T025 [P] [US4] Frontend: en `FE/features/restaurant/OrdersPage.tsx` añadir las pestañas Aceptados/En preparación/Listos con el botón de avance único permitido por `orders-logic.ts`; ampliar `orders-logic.test.ts` con la secuencia.

**Checkpoint**: el restaurante completa el ciclo hasta Listo para recoger.

---

## Phase 7: User Story 5 - Registro de cada cambio de estado (Priority: P2)

**Goal**: cada cambio efectivo queda registrado con fecha y responsable; los rechazados no (FR-012).

**Independent Test**: recorrer un pedido por varios estados y comprobar un registro por cambio.

- [X] T026 [US5] Pruebas de integración en `BT/order/OrderStatusChangeIntegrationTest.java`: recorrido PENDING→ACCEPTED→PREPARING→READY deja 3 registros con estado anterior/nuevo, `changed_at`, `actor_id` y `actor_role` `RESTAURANT`; rechazo y cancelación registran con rol `RESTAURANT`/`CUSTOMER` y el motivo; reserva vencida registra `SYSTEM`, `actor_id` nulo y motivo «reserva vencida»; los intentos denegados (403/404/409) no crean registros.

**Checkpoint**: trazabilidad completa de los cambios de estado.

---

## Phase 8: Polish & Cross-Cutting

- [X] T027 [P] Verificar que ninguna respuesta ni log incluye claves de Stripe ni datos de tarjeta en los flujos nuevos (revisar `OrderStatusService` y `GlobalExceptionHandler`).
- [X] T028 Ejecutar `mvnw verify` en `backend/` y confirmar JaCoCo ≥ 90 % de líneas de lógica de negocio, sin excluir transiciones ni validaciones; ejecutar `pnpm test --coverage` en `frontend/`.
- [ ] T029 Seguir [quickstart.md](./quickstart.md) en local con Stripe en pruebas y anotar el resultado en `specs/007-gestion-estado-pedido/validation.md` distinguiendo lo verificado de lo pendiente (webhooks, conciliación, RNF-06/08 de despliegue).

---

## Dependencies & Execution Order

- **Foundational (T001–T008)** bloquea todo. Dentro: T001 → T002/T003 → T005; T004, T006 y T007 en paralelo tras T001/T003; T008 al final.
- **US1 (T009–T015)** primero: crea el controlador de restaurante y el patrón de pruebas.
- **US2 (T016–T020)** depende de T008; independiente de US1 salvo por archivos compartidos (`OrderStatusService`).
- **US3 (T021)** depende de US1 y US2 (necesita aceptar y cancelar).
- **US4 (T022–T025)** depende de US1 (controlador y servicio); **US5 (T026)** depende de US1, US2 y US4.
- **Polish** al final.

## Parallel Opportunities

- Foundational: T004, T006 y T007 a la vez.
- US1: T010, T012, T014 y T015 a la vez; US2: T018 y T020.
- Frontend (T014, T015, T020, T025) puede avanzar en paralelo con el backend contra el contrato.

## Implementation Strategy

1. **MVP**: Foundational + US1 (aceptar/rechazar con cobro y liberación).
2. Añadir US2 y US3 (cancelación y carrera) antes de dar el flujo por confiable: son el núcleo de la
   integridad del dinero.
3. Cerrar con US4 y US5 y la fase de pulido.
