---
description: "Tareas del incremento 012: vencimiento automático de pedidos pendientes"
---

# Tasks: Vencimiento automático de pedidos pendientes

**Input**: Design documents from `specs/012-vencimiento-pedidos-pendientes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/order-expiry-api.md, quickstart.md

**Tests**: Incluidas. La constitución (principio V, RNF-11) exige pruebas unitarias y de integración con
JaCoCo ≥ 90 % de líneas de negocio, y la constitución IV exige probar las carreras con estado persistido.

**Organization**: Tareas agrupadas por historia de usuario.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ir en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia a la que pertenece (US1–US4)
- Rutas: `backend/src/main/java/com/mikuna/backend/` = `BE/`, `backend/src/test/java/com/mikuna/backend/` =
  `BT/`, `frontend/src/features/` = `FE/`

## Phase 1: Setup

- [X] T001 Añadir a `backend/src/main/resources/application.properties` las propiedades
  `mikuna.orders.response-timeout=${MIKUNA_ORDER_RESPONSE_TIMEOUT:PT15M}` y
  `mikuna.orders.expiry-sweep=${MIKUNA_ORDER_EXPIRY_SWEEP:PT15S}` y
  `mikuna.orders.expiry-initial-delay=PT5S` con un comentario (spec 012), y en
  `backend/src/test/resources/application.properties` `mikuna.orders.response-timeout=PT15M`,
  `mikuna.orders.expiry-sweep=PT1H` y `mikuna.orders.expiry-initial-delay=PT1H` (en pruebas el barrido nunca
  corre solo: se invoca a mano, research §1, §6).
- [X] T002 Activar la programación de tareas con `@EnableScheduling` en `BE/BackendApplication.java`.

## Phase 2: Foundational (bloquea todas las historias)

- [X] T003 Crear `BE/order/service/OrderExpiryPolicy.java` (`@Component`): lee
  `mikuna.orders.response-timeout` como `Duration`; expone `timeout()`, `expiresAt(Instant createdAt)` y la
  constante `REASON = "sin respuesta del restaurante"`; `secondsToExpire(Order, Instant now)` devuelve `null`
  si el pedido no está `PENDING` o no tiene `expiresAt`, y si no `max(0, segundos hasta expiresAt)` (nunca
  negativo, contrato).
- [X] T004 Ampliar `BE/order/model/Order.java`: columna `expires_at` (`Instant expiresAt`, nullable en BD,
  `updatable = false`, «Se fija al crear: created_at + plazo vigente. Inmutable»), método
  `setExpiresAt(Instant)` que solo asigna si es nulo, `getExpiresAt()`, `isExpiredAt(Instant now)`
  (`status == PENDING && expiresAt != null && !now.isBefore(expiresAt)`) e índice
  `ix_pedidos_estado_vence (status, expires_at)` en `@Table(indexes)`.
- [X] T005 Fijar el vencimiento al crear el pedido en `BE/order/service/CheckoutTransaction.java`
  (`order.setExpiresAt(policy.expiresAt(createdAt))` justo tras `new Order(...)`), inyectando
  `OrderExpiryPolicy`; ajustar el constructor en `BT/order/service/CheckoutTransactionTest.java` si lo crea a mano.
- [X] T006 [P] Añadir `reason` (String, nullable) a `BE/order/model/OrderStatusChangedEvent.java` con un
  constructor de compatibilidad de 6 argumentos (`reason = null`), y publicarlo con el motivo normalizado en
  `OrderStatusService.changeStatus` (`BE/order/service/OrderStatusService.java`).
- [X] T007 [P] Añadir `ORDER_EXPIRED = "ORDER_EXPIRED"` y el mensaje
  `EXPIRED_MESSAGE = "El pedido venció porque no se respondió a tiempo"` en
  `BE/common/exception/OrderConflictException.java`.
- [X] T008 Añadir `expiresAt` (Instant, nullable) y `secondsToExpire` (Long, nullable) a
  `BE/order/dto/OrderResponse.java` y `BE/order/dto/RestaurantOrderResponse.java` (nueva sobrecarga
  `of(order, paymentStatus, secondsToExpire)`; la de 2 argumentos pasa `null`) y rellenarlos en todos los
  puntos que los construyen (`OrderController`, `RestaurantOrderController`, `CheckoutService`/`CheckoutTransaction`
  donde se arme `OrderResponse`) usando `OrderExpiryPolicy.secondsToExpire(order, Instant.now(clock))`.

**Checkpoint**: el pedido nace con `expiresAt` y las respuestas lo exponen.

## Phase 3: User Story 1 - Un pedido sin respuesta se cancela solo (P1) 🎯 MVP

**Goal**: los Pendientes vencidos pasan a Cancelado por el sistema con la reserva liberada; aceptar o
rechazar un vencido responde 409 `ORDER_EXPIRED`.

**Independent Test**: pedido Pendiente con `expires_at` en el pasado → barrido → Cancelado, `SYSTEM`, motivo
«sin respuesta del restaurante», pago `RELEASED`.

### Tests for User Story 1

- [X] T009 [P] [US1] Pruebas unitarias en `BT/order/service/PendingOrderExpiryServiceTest.java` (Mockito):
  vence con tarjeta (llama `paymentService.release` y `changeStatus(order, CANCELLED, null, SYSTEM, REASON)`),
  no hace nada si el pedido no existe, no está `PENDING` o aún no venció; si `release` lanza, no cambia estado.
- [X] T010 [P] [US1] Pruebas unitarias en `BT/order/service/OrderStatusServiceTest.java`: `accept` y `reject`
  sobre un Pendiente vencido liberan, cancelan por `SYSTEM` con `REASON` y devuelven `expired = true` sin cobrar;
  sobre un Cancelado cuyo último cambio tiene `REASON` lanzan `OrderConflictException(ORDER_EXPIRED)`.
- [X] T011 [P] [US1] Prueba de integración `BT/order/PendingOrderExpiryIntegrationTest.java` (`@SpringBootTest`,
  MockMvc, `StripeGateway` simulado, mismo patrón de alta de datos que `OrderCancellationIntegrationTest`):
  tarjeta vencida → Cancelado + pago `RELEASED` + `gateway.cancel` una vez; efectivo vencido → Cancelado sin
  llamadas a la pasarela; aceptado/rechazado/cancelado antes → sin cambios ni segunda liberación;
  `POST /api/restaurant/orders/{id}/accept` y `/reject` sobre vencido no barrido → 409 `ORDER_EXPIRED` y queda
  Cancelado/liberado; sobre ya vencido → 409 `ORDER_EXPIRED`; las respuestas de pedido Pendiente traen
  `expiresAt` y `secondsToExpire`.

### Implementation for User Story 1

- [X] T012 [P] [US1] Añadir a `BE/order/repository/OrderRepository.java`
  `List<UUID> findExpiredPendingIds(OrderStatus status, Instant now, Pageable page)` (JPQL: `select o.id ... where
  o.status = :status and o.expiresAt <= :now order by o.expiresAt`).
- [X] T013 [P] [US1] Añadir a `BE/order/repository/OrderStatusChangeRepository.java`
  `Optional<OrderStatusChange> findFirstByOrderIdOrderByChangedAtDesc(UUID orderId)`.
- [X] T014 [US1] Crear `BE/order/service/PendingOrderExpiryService.java`: `expire(UUID orderId)` con
  `@Transactional(propagation = REQUIRES_NEW)` que bloquea con `findByIdForUpdate`, recomprueba
  `order.isExpiredAt(now)`, y llama a `applyExpiry(order)`; `applyExpiry` (paquete, reutilizable dentro de una
  transacción existente) hace `paymentService.release(orderId)` y
  `statusService.changeStatus(order, CANCELLED, null, ActorRole.SYSTEM, OrderExpiryPolicy.REASON)`. Devuelve si
  venció.
- [X] T015 [US1] En `BE/order/service/OrderStatusService.java`: `Result` gana `expired`; `accept` y `reject`,
  tras bloquear, si `order.isExpiredAt(now)` aplican el vencimiento (liberar + Cancelado `SYSTEM` con `REASON`)
  y devuelven `expired = true`; si el pedido está `CANCELLED` y su último cambio tiene `REASON`, lanzan
  `OrderConflictException(ORDER_EXPIRED, EXPIRED_MESSAGE)`. `cancel` del cliente no cambia (FR-015).
- [X] T016 [US1] En `BE/order/controller/RestaurantOrderController.java`: si `result.expired()`, lanzar
  `OrderConflictException(ORDER_EXPIRED, EXPIRED_MESSAGE)` después de la transacción (como la reserva vencida).
  Comprobar que `GlobalExceptionHandler` ya traduce `OrderConflictException` a 409 con `code`.
- [X] T017 [US1] Crear `BE/order/service/PendingOrderExpirySweeper.java` (`@Component`):
  `@Scheduled(initialDelayString = "${mikuna.orders.expiry-initial-delay}", fixedDelayString =
  "${mikuna.orders.expiry-sweep}")` `sweep()` que pide
  hasta 100 ids vencidos y llama a `expiryService.expire(id)` en un `try/catch` por pedido (registra el fallo
  con `warn` y sigue con el siguiente).
- [X] T018 [US1] Textos de aviso en `BE/notification/service/NotificationRecipients.java`: en `CANCELLED` con
  `event.reason()` igual a «sin respuesta del restaurante», cliente «Se canceló tu pedido #XXXX: el
  restaurante no respondió a tiempo» y restaurante «Se canceló el pedido #XXXX porque no se respondió a
  tiempo»; con su prueba en `BT/notification/service/NotificationRecipientsTest.java`.

**Checkpoint**: MVP funcional; los vencidos se cancelan y nadie puede aceptarlos.

## Phase 4: User Story 2 - Decisiones simultáneas coherentes (P1)

**Goal**: vencimiento ‖ aceptar/rechazar/cancelar, y dos barridos a la vez, terminan en un único desenlace.

**Independent Test**: repetir ≥ 50 veces las carreras y comprobar estado, pago, cambios y avisos.

- [X] T019 [US2] Prueba de concurrencia `BT/order/PendingOrderExpiryConcurrencyIntegrationTest.java` (patrón de
  `OrderDecisionConcurrencyIntegrationTest`), ≥ 50 repeticiones por caso, todas las acciones lanzadas a la vez:
  (a) pedido ya vencido, `expire` ‖ `accept`: gane quien gane queda Cancelado por `SYSTEM` y `RELEASED`, nunca
  `CAPTURED`, y `accept` responde 409 `ORDER_EXPIRED`; (b) pedido aún vigente, `expire` ‖ `accept`: `expire` no
  hace nada y queda Aceptado y `CAPTURED`; (c) pedido vencido, `expire` ‖ cancelación del cliente: un único
  Cancelado y una única liberación; (d) dos `expire`. Comprobar siempre: nunca `CAPTURED` y `RELEASED` a la
  vez, un solo cambio de estado desde `PENDING` y, en (d), un solo aviso por destinatario.
- [X] T020 [US2] Revisar que `PendingOrderExpiryService` y `OrderStatusService` bloquean pedido → pago en ese
  orden (sin interbloqueos) y documentarlo en el Javadoc de `PendingOrderExpiryService`.

## Phase 5: User Story 3 - Cliente y restaurante ven el tiempo restante (P2)

**Goal**: cuenta regresiva en ambas vistas, «próximo a vencer» en el último 20 %, sin negativos.

**Independent Test**: Vitest de la lógica + abrir ambas vistas con un pedido Pendiente.

- [X] T021 [P] [US3] Lógica pura en `FE/restaurant/orders-logic.ts`: `expiryDeadline(secondsToExpire, receivedAt)`,
  `remainingMs(deadline, now)` (≥ 0), `isNearExpiry(remaining, createdAt, expiresAt)` (≤ 20 % del plazo total),
  `formatRemaining(ms)` («m:ss») y `PENDING_EXPIRY_NOTICE` para el texto del cliente; pruebas en
  `FE/restaurant/orders-logic.test.ts`.
- [X] T022 [P] [US3] Tipos: `expiresAt: string | null` y `secondsToExpire: number | null` en
  `FE/restaurant/orders-api.ts` (`RestaurantOrder`) y `FE/checkout/checkout-api.ts` (`Order`).
- [X] T023 [US3] `FE/restaurant/OrdersPage.tsx`: en cada pedido Pendiente mostrar «Responde en m:ss» con un
  reloj de 1 s, clase de aviso cuando `isNearExpiry`, ocultar Aceptar/Rechazar al llegar a 0, mostrar
  «Vencido» y forzar una recarga de la lista; tratar el 409 `ORDER_EXPIRED` mostrando su mensaje y recargando.
  Estilos en el CSS de la página.
- [X] T024 [US3] `FE/checkout/OrderPage.tsx`: con el pedido Pendiente mostrar «Si el restaurante no responde,
  se cancelará en m:ss» con la cuenta; al llegar a 0 recargar el pedido para reflejar Cancelado.

## Phase 6: User Story 4 - Sobrevive a reinicios (P2)

**Goal**: los pedidos vencidos durante una caída (y los anteriores al incremento) se cancelan al arrancar.

**Independent Test**: filas con `expires_at` pasado o nulo + arranque → canceladas en el primer barrido.

- [X] T025 [US4] Añadir a `BE/order/repository/OrderRepository.java` el relleno nativo
  `update pedidos set expires_at = created_at + make_interval(secs => :seconds) where expires_at is null`
  (`@Modifying`) y crear `BE/order/service/OrderExpiryBackfill.java` que lo ejecuta en
  `ApplicationReadyEvent` dentro de una transacción y registra cuántas filas rellenó.
- [X] T026 [US4] Prueba de integración en `BT/order/PendingOrderExpiryIntegrationTest.java`: un pedido Pendiente
  con `expires_at` nulo y `created_at` antiguo → tras el relleno y un `sweep()` queda Cancelado; un Pendiente
  reciente con `expires_at` nulo recibe `created_at + 15 min` y no se cancela.

## Phase 7: Polish & Cross-Cutting

- [X] T027 [P] Documentar `MIKUNA_ORDER_RESPONSE_TIMEOUT` y `MIKUNA_ORDER_EXPIRY_SWEEP` en
  `backend/.env.example`.
- [X] T028 Ejecutar `./mvnw verify` en `backend/` (suite completa + JaCoCo ≥ 90 %) y `npm test` y
  `npm run build` en `frontend/`; corregir fallos.
- [X] T029 Recorrer [quickstart.md](./quickstart.md) y anotar en este archivo lo que quede sin validar a mano.

## Dependencies & Execution Order

- Setup (T001–T002) → Foundational (T003–T008) → US1 (T009–T018) → US2 (T019–T020).
- US3 (T021–T024) depende solo de Foundational (T008); puede ir en paralelo con US1.
- US4 (T025–T026) depende de US1 (barrido y servicio).
- Polish al final.

Dentro de US1: T012/T013 → T014 → T015/T017 → T016; las pruebas T009–T011 se escriben a la par y deben
pasar al cerrar la fase.

## Parallel Example: User Story 1

```text
T009 PendingOrderExpiryServiceTest   ‖  T012 OrderRepository  ‖  T013 OrderStatusChangeRepository
T010 OrderStatusServiceTest          ‖  T018 NotificationRecipients
```

## Parallel Example: User Story 3

```text
T021 orders-logic.ts (+ test)  ‖  T022 tipos de API
```

## Implementation Strategy

1. **MVP** = Setup + Foundational + US1: ningún pedido queda Pendiente para siempre.
2. US2 refuerza la garantía de dinero con pruebas de carrera.
3. US3 añade la experiencia (cuenta regresiva); US4 cubre reinicios y datos anteriores.

## Notas de implementación (2026-11-22)

- Validado automáticamente: backend `./mvnw verify` con 740 pruebas y 0 fallos, cobertura de líneas JaCoCo
  94,3 % (las clases nuevas al 100 %); frontend `npm test` con 132 pruebas y 0 fallos, y `npm run build` correcto.
- `npm run lint` marca un único error previo, ajeno a este incremento, en
  `frontend/src/features/catalog/RestaurantSearchPage.tsx:38` (`react-hooks/set-state-in-effect`).
- **Sin validar a mano** (pasos 1–5 de [quickstart.md](./quickstart.md)): la cuenta regresiva en el navegador, la
  cancelación real con Stripe de pruebas y el arranque tras una caída. Las pruebas de integración cubren la
  lógica con `StripeGateway` simulado, pero estos pasos siguen pendientes de una prueba manual.
