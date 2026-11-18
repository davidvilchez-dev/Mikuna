---

description: "Tareas de implementación: disponibilidad, asignación y entrega (RF-21, RF-22, RF-23, RF-24, RF-25, RNF-05, RF-04)"
---

# Tasks: Disponibilidad, asignación y entrega

**Input**: Documentos de diseño en `specs/008-asignacion-entrega/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/delivery-api.md](./contracts/delivery-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra PostgreSQL local
(`mikuna_test_db`), pruebas de carrera con hilos sobre el estado persistido (RNF-05) y ≥ 90 % de líneas de
lógica de negocio. Vitest para la lógica del frontend. Sin Playwright ni otra herramienta E2E; sin Stripe
(este incremento no toca el pago).

**Organization**: Tareas agrupadas por historia. US1 (disponibilidad y asignación automática) es el MVP; US2
(aceptar/recoger/entregar) completa el flujo; US3 (rechazo y reasignación) y US4 (asignación manual) se apoyan en
el servicio de asignación de US1; US5 (carreras, RNF-05) valida la garantía de todas ellas.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1–US5 según [spec.md](./spec.md)
- Rutas abreviadas: `BE/` = `backend/src/main/java/com/mikuna/backend/`; `BT/` =
  `backend/src/test/java/com/mikuna/backend/`; `FE/` = `frontend/src/`.
- Nunca abrir, copiar ni imprimir `backend/.env`.
- Las pruebas de integración extienden `IntegrationTestSupport` / `OrderStatusTestSupport` (`BT/support/`) y usan
  `TestAccounts`, `TestOrders` y `TestRestaurants`; revisar sus utilidades antes de crear otras.

---

## Phase 1: Setup

Sin tareas: no hay dependencias nuevas ni configuración (plan: «ninguna nueva»).

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: estados nuevos, modelo de datos del reparto, permisos y errores comunes.

- [X] T001 Ampliar `BE/order/model/OrderStatus.java` con `ASSIGNED, PICKED_UP, DELIVERED` (cadena ≤ 16 caracteres en `pedidos.status`) y `BE/order/model/ActorRole.java` con `COURIER, ADMIN`.
- [X] T002 En `BE/order/model/Order.java` ampliar la tabla de `transitionTo` **únicamente** con READY→ASSIGNED, ASSIGNED→PICKED_UP, ASSIGNED→READY (solo para el rechazo del repartidor) y PICKED_UP→DELIVERED (final); cualquier otra transición lanza `InvalidOrderTransitionException`. `DELIVERED` es final.
- [X] T003 [P] Crear `BE/delivery/model/CourierAvailability.java` (tabla `repartidores_disponibilidad`): `courierId` UUID PK, `available` boolean no nulo, `freeSince` Instant no nulo, `updatedAt` Instant no nulo; ausencia de fila = no disponible. Con `BE/delivery/repository/CourierAvailabilityRepository.java` (sin consultas todavía).
- [X] T004 [P] Crear `BE/delivery/model/Delivery.java` (tabla `entregas`): `id` UUID generado, `orderId` UUID **único y no nulo**, `courierId` UUID **nulo** (nulo = pendiente), `readyAt` Instant no nulo, `assignedAt` Instant nulo; índices `(courier_id)` y `(ready_at)`. Con `BE/delivery/repository/DeliveryRepository.java` (`findByOrderId`).
- [X] T005 [P] Crear `BE/delivery/model/DeliveryRejection.java` (tabla `rechazos_entrega`): `id` UUID, `deliveryId` no nulo, `courierId` no nulo, `rejectedAt` no nulo; **único `(delivery_id, courier_id)`**. Con `BE/delivery/repository/DeliveryRejectionRepository.java`.
- [X] T006 [P] Crear en `BE/common/exception/` `DeliveryConflictException` (409, `code=DELIVERY_CONFLICT`) y `CourierNotCandidateException` (409, `code=COURIER_NOT_CANDIDATE`) y registrar sus handlers en `GlobalExceptionHandler.java`; `InvalidOrderTransitionException` (409) y `OrderNotFoundException` (404) ya existen.
- [X] T007 En `BE/common/security/SecurityConfig.java` añadir `.requestMatchers("/api/courier/**").hasRole("COURIER")` junto a las reglas de restaurante, cliente y administrador (`/api/admin/**` ya existe).
- [X] T008 En `BE/order/service/OrderStatusService.java` extraer un método público reutilizable `changeStatus(Order order, OrderStatus target, UUID actorId, ActorRole role, String reason)` (hoy `change` es privado) que aplique `transitionTo`, guarde el pedido y el `OrderStatusChange`; `delivery` lo usará con el pedido ya bloqueado. Añadir `BE/order/model/OrderReadyEvent.java` (`orderId`) y publicarlo con `ApplicationEventPublisher` en `markReady`.
- [X] T009 Verificar que `order` no importa nada de `delivery` (sin ciclos, constitución I): `delivery` depende de `order` y `account`, y `order` solo publica `OrderReadyEvent`.
- [X] T010 [P] Pruebas unitarias en `BT/order/model/OrderTransitionTest.java` (ampliar): las cuatro transiciones nuevas válidas, saltos (READY→PICKED_UP, ASSIGNED→DELIVERED, READY→DELIVERED) y retrocesos (PICKED_UP→ASSIGNED, DELIVERED→cualquiera) inválidos.
- [X] T010a En `BE/order/service/OrderStatusService.java` extender con `ASSIGNED`, `PICKED_UP` y `DELIVERED` la lista de estados de `cancel` que lanzan `InvalidOrderTransitionException.NO_REFUND` (hoy solo ACCEPTED, PREPARING y READY) y ampliar `BT/order/OrderCancellationIntegrationTest.java` con esos tres casos (409 con mensaje de no reembolso, sin cambios).

**Checkpoint**: estados, tablas, permisos y errores listos.

---

## Phase 3: User Story 1 - Disponibilidad y asignación automática (Priority: P1) 🎯 MVP

**Goal**: el repartidor indica su disponibilidad y el pedido Listo para recoger se asigna solo al candidato que
lleva más tiempo libre; sin candidatos queda pendiente y se asigna al aparecer uno (RF-21, RF-22).

**Independent Test**: con A disponible, B no disponible y C inhabilitado, dejar un pedido READY → `ASSIGNED` a A.

- [X] T011 [US1] En `BE/delivery/repository/CourierAvailabilityRepository.java` añadir la consulta nativa del candidato (research §2): `available = true`, cuenta `ENABLED` (join a `cuentas`), sin entrega activa (pedido `ASSIGNED` o `PICKED_UP` con su `courier_id`), sin fila en `rechazos_entrega` para la entrega dada, `ORDER BY free_since ASC LIMIT 1 FOR UPDATE OF ca SKIP LOCKED`; y la variante «candidato concreto» con bloqueo de fila para la asignación manual y la entrega pendiente más antigua (`entregas` con `courier_id IS NULL` y pedido `READY`, `ready_at ASC`).
- [X] T012 [US1] En `BE/delivery/repository/DeliveryRepository.java` añadir `@Modifying @Query("update Delivery d set d.courierId = :c, d.assignedAt = :t where d.orderId = :o and d.courierId is null")` que devuelve las filas afectadas, y la consulta de pendientes (pedido `READY` y `courier_id IS NULL`, más antiguas primero).
- [X] T013 [US1] Crear `BE/delivery/service/OrderReadyListener.java`: `@EventListener` de `OrderReadyEvent` (misma transacción) que crea la `Delivery` con `readyAt = Instant.now(clock)`; y `@TransactionalEventListener(AFTER_COMMIT)` que solo delega en `DeliveryDispatcher.dispatch(orderId)` (sin lógica transaccional propia).
- [X] T014 [US1] Crear `BE/delivery/service/DeliveryAssignmentService.java` con `assignAutomatically(UUID orderId)`, `@Transactional(propagation = Propagation.REQUIRES_NEW)` (se invoca tras el commit de `markReady`; research §4): `OrderRepository.findByIdForUpdate`; si el pedido no está `READY` o la entrega ya tiene repartidor, no hace nada; elegir candidato (T011); `DeliveryRepository` condicional (T012) que debe afectar **1 fila**; `OrderStatusService.changeStatus(..., ASSIGNED, null, ActorRole.SYSTEM, null)`. Sin candidato → no cambia nada (queda pendiente, FR-004). Orden de bloqueo fijo: pedido → repartidor.
- [X] T015 [US1] En el mismo servicio añadir `assignOldestPendingTo(UUID courierId)` (`@Transactional(propagation = Propagation.REQUIRES_NEW)`): localizar la pendiente más antigua sin bloquear, bloquear su pedido con `findByIdForUpdate`, revalidar READY y sin repartidor, bloquear la fila del repartidor y revalidar que es candidato para esa entrega (disponible, habilitado, sin entrega activa, sin rechazo), asignar como en T014; devolver `boolean`. Si el repartidor no es candidato, no hace nada.
- [X] T016 [US1] Crear `BE/delivery/service/DeliveryDispatcher.java` (**sin** `@Transactional`): `dispatch(orderId)` → `assignAutomatically`; `courierBecameFree(courierId)` → `assignOldestPendingTo`; captura cualquier excepción, la registra sin datos sensibles y deja la entrega pendiente (plan, punto 2).
- [X] T017 [US1] Crear `BE/delivery/service/CourierAvailabilityService.java` (`get`, `set(courierId, available)`): comprobar cuenta `COURIER` habilitada; `set` con `@Transactional` crea o actualiza la fila con `freeSince = now` solo al pasar a disponible desde no disponible (research §3) y `updatedAt`; **tras el commit** llamar a `DeliveryDispatcher.courierBecameFree` si quedó disponible. Desactivar no toca entregas asignadas (US1.5).
- [X] T018 [P] [US1] Crear `BE/delivery/dto/AvailabilityRequest.java` (`available` boolean obligatorio, campos desconocidos → 400) y `AvailabilityResponse.java` (`available`, `freeSince`).
- [X] T019 [US1] Crear `BE/delivery/controller/CourierController.java` en `/api/courier` con `GET` y `PUT /availability` según [contracts](./contracts/delivery-api.md); el id del repartidor sale del token (`AccountPrincipal`).
- [X] T020 [P] [US1] Pruebas unitarias con Mockito en `BT/delivery/service/DeliveryAssignmentServiceTest.java` y `CourierAvailabilityServiceTest.java` (elige al de mayor tiempo libre, excluye no disponible/inhabilitado/con entrega activa/con rechazo, sin candidato no cambia nada, pedido no READY no se asigna, `freeSince` solo al activar, desactivar no toca la asignada).
- [X] T021 [US1] Pruebas de integración en `BT/delivery/AutoAssignmentIntegrationTest.java` (401 sin token, 403 con rol distinto de repartidor o cuenta deshabilitada; con A disponible, B no y C inhabilitado, `markReady` → pedido `ASSIGNED` a A y registro `READY→ASSIGNED` con actor `SYSTEM`; sin candidatos queda pendiente y al activar B se le asigna la más antigua; B con entrega activa no recibe otra; asignación en menos de 5 s). Comprobar además que, tras `markReady`, el estado `ASSIGNED` y su registro quedan persistidos y visibles desde otra conexión (REQUIRES_NEW).
- [X] T022 [P] [US1] Frontend: `FE/features/delivery/courier-api.ts` (disponibilidad y entregas, con token), `FE/features/delivery/courier-logic.ts` (etiquetas en español por estado y acción permitida) y la parte de disponibilidad de `FE/features/delivery/CourierPage.tsx` (interruptor, estado actual, actualización periódica cada ~10 s); ruta `/repartidor/entregas` en `FE/App.tsx` y enlace para el rol repartidor donde ya se muestran los enlaces por rol.
- [X] T023 [P] [US1] Prueba Vitest `FE/features/delivery/courier-logic.test.ts`; añadir `src/features/delivery/courier-logic.ts` a `test.coverage.include` en `frontend/vite.config.ts` (mantener `thresholds.lines: 90`).

**Checkpoint**: un pedido listo se asigna solo al candidato correcto.

---

## Phase 4: User Story 2 - Aceptar, recoger y entregar (Priority: P1)

**Goal**: el repartidor ve su entrega y la lleva ASSIGNED → PICKED_UP → DELIVERED (RF-25).

**Independent Test**: con una entrega `ASSIGNED`, `pickup` y `complete`; los saltos y ajenos se rechazan.

- [X] T024 [US2] Crear `BE/delivery/service/CourierDeliveryService.java` con `listActive(courierId)` (solo propias, `ASSIGNED` o `PICKED_UP`), `pickup(courierId, orderId)` y `complete(courierId, orderId)` (`@Transactional`): `findByIdForUpdate`, la entrega debe tener `courierId` igual al del token (si no → `OrderNotFoundException` 404), `changeStatus` ASSIGNED→PICKED_UP / PICKED_UP→DELIVERED con actor `COURIER`; en `complete` fijar `freeSince = now` si el repartidor sigue disponible y, **tras el commit**, `DeliveryDispatcher.courierBecameFree`. Estados inválidos y reenvíos → 409 `INVALID_TRANSITION`.
- [X] T025 [P] [US2] Crear `BE/delivery/dto/CourierDeliveryResponse.java` (`orderId`, `status`, `restaurant.name`, `delivery` con `street/district/reference/phone/label`, `lines` con `productName/quantity`, `assignedAt`) mapeado desde `Order` y `Delivery`.
- [X] T026 [US2] En `BE/delivery/controller/CourierController.java` añadir `GET /deliveries`, `POST /deliveries/{orderId}/pickup` y `POST /deliveries/{orderId}/complete` (200 con `CourierDeliveryResponse`).
- [X] T027 [P] [US2] Pruebas unitarias en `BT/delivery/service/CourierDeliveryServiceTest.java` (secuencia válida, saltar a `complete` desde ASSIGNED, repetir `pickup`, entrega ajena → 404, `freeSince` y despacho al completar).
- [X] T028 [US2] Pruebas de integración en `BT/delivery/CourierDeliveryIntegrationTest.java` (401/403; la lista muestra solo las propias; `pickup` → `PICKED_UP`, `complete` → `DELIVERED` con registros y actor `COURIER`; saltos, retrocesos y reenvíos → 409 sin cambios ni registros duplicados; entrega de otro repartidor → 404 en `pickup`, `complete` y `reject`). Cada 404 y 409 devuelve un mensaje claro sin detalles internos (FR-016).
- [X] T029 [P] [US2] Frontend: completar `FE/features/delivery/CourierPage.tsx` con la entrega actual (restaurante, dirección, productos) y los botones «Recogí el pedido» y «Entregué el pedido» según `courier-logic.ts`; mostrar mensajes de 404/409.
- [X] T030 [P] [US2] En `FE/features/checkout/OrderPage.tsx` y `checkout-api.ts` mostrar con su etiqueta en español los estados `ASSIGNED`, `PICKED_UP` y `DELIVERED` (sin avisos ni ubicación); ampliar el tipo `OrderStatus` compartido con los tres estados y hacer que `restaurantActions` de `FE/features/restaurant/orders-logic.ts` devuelva lista vacía para ellos (ampliar `orders-logic.test.ts`); ampliar `checkout-validation.test.ts` solo si hay lógica nueva.

**Checkpoint**: el flujo de entrega llega hasta Entregado.

---

## Phase 5: User Story 3 - Rechazo y reasignación (Priority: P2)

**Goal**: el repartidor rechaza; la entrega se reasigna a otro sin volver al mismo (RF-23).

**Independent Test**: rechazar con A y B disponible → `ASSIGNED` a B; A nunca la recibe de nuevo.

- [X] T031 [US3] En `BE/delivery/service/CourierDeliveryService.java` añadir `reject(courierId, orderId, reason)` (`@Transactional`): bloquear el pedido, comprobar que es `ASSIGNED` y de ese repartidor (404 si ajeno), insertar `DeliveryRejection` (`rejectedAt`), poner `courier_id = NULL` en la entrega, `changeStatus` ASSIGNED→READY con actor `COURIER` y motivo **≤ 200 caracteres** (opcional); **tras el commit** llamar a `DeliveryDispatcher.dispatch(orderId)`. No toca la disponibilidad ni `freeSince` del que rechaza (research §3). Solo en `ASSIGNED` (`PICKED_UP`/`DELIVERED` → 409).
- [X] T032 [P] [US3] Crear `BE/delivery/dto/RejectDeliveryRequest.java` (`reason` opcional, `@Size(max = 200)`, campos desconocidos → 400) y en `CourierController.java` añadir `POST /deliveries/{orderId}/reject` (200 con la entrega, `status` `READY`).
- [X] T033 [P] [US3] Pruebas unitarias en `BT/delivery/service/CourierDeliveryServiceTest.java` (ampliar): rechazo registra el rechazo y vuelve a READY sin repartidor, solo desde ASSIGNED, el rechazo no cambia el pedido a REJECTED/CANCELLED, el que rechazó queda excluido del candidato.
- [X] T034 [US3] Pruebas de integración en `BT/delivery/DeliveryRejectionIntegrationTest.java` (A rechaza y B disponible → `ASSIGNED` a B, registros `READY→ASSIGNED→READY→ASSIGNED`; A no recibe otra vez esa entrega aunque B también rechace; sin otro candidato queda pendiente con `courier_id` nulo; rechazar `PICKED_UP` → 409; el pedido nunca pasa a `REJECTED`/`CANCELLED`). Comprobar que la reasignación ocurre en menos de 5 s (SC-004).
- [X] T035 [P] [US3] Frontend: añadir el botón «Rechazar entrega» (con motivo opcional) en `FE/features/delivery/CourierPage.tsx`, visible solo con estado `ASSIGNED`, y actualizar `courier-logic.ts`/`courier-logic.test.ts`.

**Checkpoint**: una entrega rechazada no queda detenida ni vuelve al mismo repartidor.

---

## Phase 6: User Story 4 - Asignación manual del administrador (Priority: P2)

**Goal**: el administrador asigna una entrega pendiente a un repartidor candidato (RF-24).

**Independent Test**: con una entrega pendiente, asignarla a un candidato → `ASSIGNED`, responsable `ADMIN`.

- [X] T036 [US4] En `BE/delivery/service/DeliveryAssignmentService.java` añadir `listPending()` (pendientes con sus candidatos calculados con las reglas de T011) y `assignManually(adminId, orderId, courierId)` (`@Transactional`): bloquear el pedido, exigir `READY` y entrega sin repartidor (si no → 409 `INVALID_TRANSITION`/`DELIVERY_CONFLICT`), bloquear la fila del repartidor y exigir candidato (cuenta `ENABLED`, disponible, sin entrega activa, sin rechazo; si no → `CourierNotCandidateException`), asignación condicional (T012) con 1 fila y `changeStatus` READY→ASSIGNED con actor `ADMIN` y su id. Estado distinto de READY → `INVALID_TRANSITION`; actualización condicional con 0 filas (defensa) → `DELIVERY_CONFLICT`.
- [X] T036a [US4] En `BE/delivery/service/DeliveryAssignmentService.java` añadir `releaseFromDisabledCourier(adminId, orderId)` (`@Transactional`): bloquear el pedido y exigir `ASSIGNED` (si no → `INVALID_TRANSITION`); cargar la cuenta del repartidor y exigir `DISABLED` (si está habilitado → 409 `COURIER_STILL_ENABLED`, añadir `CourierStillEnabledException` y su handler en `BE/common/exception/`); poner `courier_id = NULL` y `changeStatus` ASSIGNED→READY con actor `ADMIN` y su id, sin crear `DeliveryRejection`; **tras el commit** llamar a `DeliveryDispatcher.dispatch(orderId)`. En la lista de pendientes (T036) incluir las `ASSIGNED` con repartidor inhabilitado y `courierDisabled = true`.
- [X] T037 [P] [US4] Crear `BE/delivery/dto/AssignRequest.java` (`courierId` UUID obligatorio, campos desconocidos → 400) y `PendingDeliveryResponse.java` (`courierDisabled` boolean; `orderId`, `restaurantName`, `delivery.district`, `readyAt`, `candidates` con `courierId`, `name`, `freeSince`; tras asignar, `status` y `courierId`).
- [X] T038 [US4] Crear `BE/delivery/controller/AdminDeliveryController.java` en `/api/admin/deliveries`: `GET /pending`, `POST /{orderId}/assign` y `POST /{orderId}/release` (200 con `PendingDeliveryResponse`); rol `ADMIN` por `SecurityConfig`.
- [X] T039 [P] [US4] Pruebas unitarias en `BT/delivery/service/DeliveryAssignmentServiceTest.java` (ampliar): asignación manual válida, rechazo de inhabilitado/no disponible/con entrega activa/que ya rechazó/inexistente, entrega ya asignada, responsable `ADMIN`.
- [X] T040 [US4] Pruebas de integración en `BT/delivery/AdminDeliveryIntegrationTest.java` (401 sin token, 403 con cliente/restaurante/repartidor; lista de pendientes con candidatos; asignar → `ASSIGNED` con registro `ADMIN`; asignar una ya `ASSIGNED` → 409 sin cambiar; elegir un no candidato → 409 `COURIER_NOT_CANDIDATE`; `courierId` inválido → 400). Añadir `release`: libera con repartidor inhabilitado (READY sin repartidor, registro `ADMIN`, sin rechazo, reasignación posterior), 409 `COURIER_STILL_ENABLED` con repartidor habilitado, `INVALID_TRANSITION` si no está ASSIGNED, 403 sin rol de administrador; mensajes claros en 404/409 (FR-016).
- [X] T041 [P] [US4] Frontend: `FE/features/delivery/AdminDeliveriesPage.tsx` con `admin-deliveries-api.ts` (lista de pendientes con candidatos y botón «Asignar», actualización periódica) y ruta `/admin/entregas` en `FE/App.tsx` junto a `/admin/solicitudes`; lógica de etiquetas en `courier-logic.ts` si aplica. Incluir el botón «Liberar» para las entregas con `courierDisabled`.

**Checkpoint**: ningún pedido listo queda sin salida cuando falla la automática.

---

## Phase 7: User Story 5 - Una sola asignación bajo carrera (Priority: P1)

**Goal**: demostrar RNF-05 sobre el estado persistido (FR-011).

**Independent Test**: lanzar solicitudes simultáneas muchas veces; cada entrega termina con exactamente un `courier_id`.

- [X] T042 [US5] Pruebas de integración concurrentes en `BT/delivery/DeliveryAssignmentConcurrencyIntegrationTest.java` (hilos con `CountDownLatch`, patrón de `OrderDecisionConcurrencyIntegrationTest`), cada una repetida ≥ 30 veces: (a) N asignaciones automáticas simultáneas de la misma entrega → un único `courier_id` y un solo cambio `READY→ASSIGNED`; (b) asignación automática vs. manual de la misma entrega → solo una prospera, la otra 409 o sin efecto; (c) rechazo vs. reasignación simultáneos → un único repartidor activo y nunca el que rechazó; (d) dos pedidos y un único repartidor disponible → solo uno recibe entrega y el otro queda pendiente; (e) dos repartidores se activan a la vez con una sola pendiente → solo uno la recibe; (f) reenvío simultáneo de `pickup` → un solo cambio registrado.
- [X] T043 [US5] Comprobar en esas pruebas contra la base: `count(entregas con courier_id no nulo) == 1` por pedido, ningún repartidor con más de una entrega activa y ningún `DeliveryRejection` duplicado.

**Checkpoint**: RNF-05 verificado con evidencia persistida.

---

## Phase 8: Polish & Cross-Cutting

- [X] T044 [P] Verificar que ninguna respuesta ni log expone datos del cliente a repartidores ajenos ni claves/secretos (revisar `CourierDeliveryResponse`, `DeliveryDispatcher` y `GlobalExceptionHandler`); un repartidor solo ve el contacto de las entregas propias.
- [X] T045 Ejecutar `mvnw verify` en `backend/` y confirmar JaCoCo ≥ 90 % de líneas de lógica de negocio, sin excluir transiciones ni reglas de asignación; ejecutar `pnpm test --coverage` en `frontend/`.
- [X] T046 Seguir [quickstart.md](./quickstart.md) en local y anotar el resultado en `specs/008-asignacion-entrega/validation.md` distinguiendo lo verificado de lo pendiente (avisos spec 009, ubicación, historial, vistas globales de administración, RNF-01/10 de carga).

---

## Dependencies & Execution Order

- **Foundational (T001–T010, T010a)** bloquea todo. T001 → T002; T010a tras T001; T003, T004, T005, T006 en paralelo tras T001; T007 independiente; T008 tras T001/T002; T009 al cerrar; T010 tras T002.
- **US1 (T011–T023)** primero: crea el servicio de asignación, el despachador y `CourierController`. T011/T012 → T014/T015 → T016 → T017/T019.
- **US2 (T024–T030)** depende de T008 y T016; **US3 (T031–T035)** depende de US2 (mismo servicio) y T014; **US4 (T036–T041)** depende de T014 (reutiliza las reglas de candidato).
- **US5 (T042–T043)** depende de US1, US3 y US4 (usa asignación automática, manual y rechazo).
- **Polish** al final.

## Parallel Opportunities

- Foundational: T003, T004, T005 y T006 a la vez; T010 aparte.
- US1: T018, T020, T022 y T023 a la vez; US2: T025, T027, T029 y T030; US3: T032, T033 y T035; US4: T037, T039 y T041.
- Frontend (T022, T023, T029, T030, T035, T041) puede avanzar en paralelo con el backend contra el contrato.

## Implementation Strategy

1. **MVP**: Foundational + US1 (disponibilidad y asignación automática).
2. Añadir US2 (flujo hasta Entregado) para tener el reparto completo, luego US3 y US4 (rechazo y manual).
3. Cerrar con US5 (carreras) **antes** de dar la asignación por confiable: es la garantía de RNF-05; y con la fase
   de pulido.

## Notas de implementación

- La elección de asignación a un repartidor concreto quedó en `DeliveryAssignmentService.assignToCourier(orderId, courierId)` (`REQUIRES_NEW`, una entrega por transacción) y el bucle sobre las pendientes en `DeliveryDispatcher.courierBecameFree`, en lugar de un único `assignOldestPendingTo` (T015/T016): así ninguna transacción retiene el bloqueo de varios pedidos y no hay riesgo de interbloqueo.
- La asignación manual y la liberación (T036, T036a) viven en `DeliveryAdminService`, para no crear un ciclo entre `DeliveryAssignmentService` y `DeliveryDispatcher`.
- Las acciones posteriores al commit usan `AfterCommit` (sincronización de transacción) en lugar de un `@TransactionalEventListener` en cada servicio; solo `OrderReadyListener` usa el evento de `order`.
- T046: recorrido por API verificado por el asistente contra un backend real sobre `mikuna_test_db`; las páginas `/repartidor/entregas` y `/admin/entregas` las probó el responsable del proyecto a mano y confirmó el resultado (ver [validation.md](./validation.md)).
