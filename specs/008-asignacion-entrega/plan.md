# Implementation Plan: Disponibilidad, asignación y entrega

**Branch**: `008-asignacion-entrega` (sin rama Git nueva) | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/008-asignacion-entrega/spec.md`

## Summary

Implementa RF-21 a RF-25 con RNF-05 y la autorización de RF-04, sobre el pedido Listo para recoger de la spec 007:

- **Backend, módulo `delivery` (nuevo, depende de `order` y `account`):** `CourierAvailability` (disponibilidad y
  «libre desde»), `Delivery` (una por pedido, con a lo sumo un repartidor) y `DeliveryRejection` (quién rechazó
  qué). `DeliveryAssignmentService` elige candidato y asigna; `CourierDeliveryService` acepta/recoge, rechaza y
  entrega; `DeliveryDispatcher` (sin transacción) lanza la asignación tras confirmar cada cambio que libera un
  candidato o una entrega.
- **Backend, módulo `order` (ampliado):** `OrderStatus` suma `ASSIGNED`, `PICKED_UP`, `DELIVERED`; `ActorRole` suma
  `COURIER` y `ADMIN`. `Order.transitionTo` admite READY→ASSIGNED, ASSIGNED→PICKED_UP, ASSIGNED→READY (solo por
  rechazo del repartidor) y PICKED_UP→DELIVERED; cada cambio va a `cambios_estado_pedido` (FR-014). `markReady`
  publica un evento y `delivery` crea la entrega en la misma transacción; `order` nunca importa `delivery`.
- **Concurrencia (RNF-05):** bloqueo de la fila del pedido, luego del repartidor (`FOR UPDATE SKIP LOCKED`) y
  `UPDATE entregas SET courier_id = ? WHERE order_id = ? AND courier_id IS NULL` que debe afectar 1 fila.
- **Seguridad:** `/api/courier/**` exige rol `COURIER` (nueva regla en `SecurityConfig`); administración bajo
  `/api/admin/deliveries/**`.
- **Frontend:** `/repartidor/entregas` (disponibilidad, entrega actual, rechazar/recoger/entregar) y
  `/admin/entregas` (pendientes y asignación manual), con actualización periódica porque los avisos son spec 009.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**: las ya instaladas; ninguna nueva.

**Storage**: PostgreSQL local; tablas nuevas `repartidores_disponibilidad`, `entregas`, `rechazos_entrega` por
`ddl-auto=update`; `pedidos.status` ya admite 16 caracteres. Sin migraciones.

**Testing**: JUnit 5 + Mockito (elección de candidato, tabla de transiciones, reglas de rechazo/manual);
`@SpringBootTest` + MockMvc sobre `mikuna_test_db` (401/403/404/409, pertenencia, registro de cambios) y pruebas de
carrera con hilos concurrentes sobre el estado persistido; JaCoCo ≥ 90 % de líneas de negocio; Vitest para la
lógica de la interfaz. Sin Playwright.

**Target Platform**: igual que 002–007.

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: SC-002 y SC-004 (asignación < 5 s, síncrona tras el commit); SC-005 (< 1 min manual).
RNF-01 aplazado.

**Constraints**:
- Repartidor y administrador salen del token; estado, entrega y asignación nunca se aceptan del cliente.
- Entrega ajena o inexistente → 404 (no revela existencia), como en 006/007.
- El estado del pedido y la fila de la entrega cambian juntos o ninguno cambia.
- Un repartidor tiene como máximo una entrega activa (ASSIGNED o PICKED_UP).

**Scale/Scope**: 9 endpoints HTTP nuevos, 3 tablas nuevas, 2 páginas nuevas y un ajuste de estados en `/pedido/:id`.

## Constitution Check

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Un módulo nuevo justificado (`delivery`) con `model/repository/service/controller/dto`; `delivery` → `order`, `account`; `order` no depende de `delivery` (evento). Sin ciclos. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; reglas en servicios y en `Order.transitionTo`; el backend decide candidato y estado. | ✅ |
| III. Seguridad | Rol por ruta + cuenta habilitada (`AccountStatusFilter`); propiedad por token; pruebas 401/403/404. Sin integraciones externas nuevas. | ✅ |
| IV. Concurrencia | Actualización condicional en PostgreSQL + bloqueo de filas; unicidad preservada al reasignar (RF-23); no hay bloqueo en memoria. Flujo y rechazo→reasignación conforme a la constitución. | ✅ |
| V. Pruebas | JUnit/Mockito + integración y carreras con estado persistido; JaCoCo ≥ 90 %. | ✅ |
| VI. Trazabilidad | RF-21…RF-25, RF-04, RNF-05, RNF-07, RNF-11. RF-26/28 (ubicación) y RF-29 (avisos) fuera. | ✅ |
| VII. Incremental | Solo lo de la spec; decisiones en [research.md](./research.md). Políticas de elección y espera resueltas en la spec (constitución, «reglas pendientes»). | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Rechazo = retroceso explícito.** READY→ASSIGNED→READY es un retroceso de estado, pero está especificado
   (spec FR-008) y la constitución lo prevé («rechazar una asignación activa la reasignación»); solo lo origina
   la operación de rechazo.
2. **Sin barrido ni temporizador.** La asignación se dispara al quedar READY, al activarse un repartidor, al
   terminar/rechazar una entrega y tras un rechazo. Si el despacho tras el commit falla, la entrega queda
   pendiente y se recupera con el siguiente disparo o con asignación manual.
3. **Sin avisos.** El repartidor se entera consultando su pantalla (spec 009 queda fuera).

Resultado: **PASA** (re-evaluado tras la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/008-asignacion-entrega/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/delivery-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── delivery/                                   # módulo nuevo
│   ├── model/        CourierAvailability, Delivery, DeliveryRejection
│   ├── repository/   CourierAvailabilityRepository (candidato con SKIP LOCKED), DeliveryRepository
│   │                 (asignación condicional, pendientes), DeliveryRejectionRepository
│   ├── service/      DeliveryAssignmentService (auto, manual y liberación por repartidor inhabilitado),
│   │                 CourierDeliveryService (rechazar, recoger,
│   │                 entregar), CourierAvailabilityService, DeliveryDispatcher, OrderReadyListener
│   ├── controller/   CourierController (/api/courier), AdminDeliveryController (/api/admin/deliveries)
│   └── dto/          AvailabilityRequest/Response, CourierDeliveryResponse, PendingDeliveryResponse,
│                     AssignRequest (+ release del administrador)
├── order/
│   ├── model/        OrderStatus (+3), ActorRole (+COURIER, ADMIN), Order (+ transiciones), OrderReadyEvent
│   └── service/      OrderStatusService.markReady publica OrderReadyEvent; método público de cambio con
│                     registro reutilizable por delivery; cancel (007) también cubre ASSIGNED, PICKED_UP
│                     y DELIVERED con el mensaje de no reembolso
├── common/security/  SecurityConfig (+ /api/courier/** → COURIER)
└── common/exception/ DeliveryConflictException (409), CourierNotCandidateException (409),
                      CourierStillEnabledException (409)
backend/src/test/java/com/mikuna/backend/delivery/   # unit + integration + carreras

frontend/src/
├── App.tsx                                  # + /repartidor/entregas, /admin/entregas
├── features/delivery/CourierPage.tsx        # + courier-api.ts, courier.css
├── features/delivery/AdminDeliveriesPage.tsx
└── features/checkout/OrderPage.tsx          # + estados Asignado, Recogido, Entregado
```

**Structure Decision**: un módulo `delivery` (constitución I) porque la disponibilidad, las asignaciones y los
rechazos son una responsabilidad propia; el estado del pedido sigue siendo único y vive en `order`.

## Complexity Tracking

Sin violaciones que justificar.
