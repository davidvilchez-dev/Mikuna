# Implementation Plan: Aceptación, rechazo, preparación y cancelación del pedido

**Branch**: `007-gestion-estado-pedido` (sin rama Git nueva) | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/007-gestion-estado-pedido/spec.md`

## Summary

Implementa RF-18, RF-19 y RF-20 con la autorización de RF-04, sobre el pedido y el pago de la spec 006:

- **Backend, módulo `order` (ampliado):** `OrderStatus` pasa de `PENDING` a seis estados; `Order.status` deja de
  ser inmutable y solo cambia mediante métodos de transición que validan la tabla de FR-007. Nuevo
  `OrderStatusChange` (FR-012) y `OrderStatusService`, que bloquea la fila del pedido, valida, invoca el
  pago y registra el cambio en una sola transacción. Controlador nuevo `RestaurantOrderController`
  (`/api/restaurant/orders`) y la acción `cancel` en `OrderController` (`/api/customer/orders/{id}/cancel`).
- **Backend, módulo `payment`:** se reutilizan `capture` y `release` (006). `capture` devuelve un resultado en
  lugar de lanzar cuando la reserva venció (research §3); `StripeGateway` distingue ese caso de una caída.
- **Concurrencia:** bloqueo pesimista de la fila `pedidos` **antes** que la de `pagos` (orden fijo, sin
  interbloqueos); quien llega segundo ve un estado distinto y recibe 409 (research §1).
- **Frontend:** página `/mi-restaurante/pedidos` (pendientes, aceptar/rechazar y avance de estado) y botón
  «Cancelar pedido» en `/pedido/:id` mientras esté Pendiente.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**: las ya instaladas; ninguna nueva.

**Storage**: PostgreSQL local; tabla nueva `cambios_estado_pedido` por `ddl-auto=update`; la columna
`pedidos.status` ya existe (cadena de hasta 16 caracteres). Sin migraciones.

**Testing**: JUnit 5 + Mockito (tabla de transiciones, decisión y pago, reserva vencida); `@SpringBootTest` +
MockMvc sobre `mikuna_test_db` con `StripeGateway` simulado (401/403/404/409, pertenencia, carrera
aceptar/cancelar repetida, doble envío, registro de cambios); JaCoCo ≥ 90 % de líneas de negocio; Vitest para la
lógica de la interfaz. Sin llamadas a Stripe real ni Playwright.

**Target Platform**: igual que 002–006.

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: SC-001 (< 30 s para decidir). RNF-01 aplazado.

**Constraints**:
- El restaurante y el cliente salen del token; el estado, el monto y el pago nunca se aceptan del cliente.
- Pedido ajeno → 404 (no revela existencia), igual que en 006.
- El estado del pedido y el del pago cambian juntos o ninguno cambia (FR-011).
- Sin endpoints de pago; el cobro y la liberación solo ocurren por una transición de pedido.

**Scale/Scope**: 6 endpoints HTTP nuevos, 1 tabla nueva, 1 página nueva y un ajuste en `/pedido/:id`.

## Constitution Check

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Sin módulos nuevos: se amplía `order`; `order` → `payment` y `restaurant` (ya existentes), sin ciclos. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; transiciones en `Order`/`OrderStatusService`; Stripe tras `StripeGateway`. | ✅ |
| III. Seguridad | `/api/restaurant/**` y `/api/customer/**` ya exigen rol y cuenta habilitada; propiedad por token (restaurante por `ownerId`, cliente por `customerId`). Sin datos de tarjeta. | ✅ |
| IV. Concurrencia | Bloqueo de fila del pedido + estado esperado, luego bloqueo del pago; pruebas de carrera con estado persistido. Flujo permitido solo el de FR-007. | ✅ |
| V. Pruebas | JUnit/Mockito + integración con `StripeGateway` simulado; JaCoCo ≥ 90 %. | ✅ |
| VI. Trazabilidad | RF-18, RF-19, RF-20, RF-04, RF-16/17 (captura y liberación), RNF-07, RNF-11. RF-22 (asignación) queda fuera. | ✅ |
| VII. Incremental | Solo lo de la spec; decisiones en [research.md](./research.md). | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Pago dentro de la transacción.** La llamada a Stripe ocurre con las filas bloqueadas; es breve (tiempos
   acotados desde 006) y evita un estado intermedio. Una caída entre el cobro en Stripe y el commit puede dejar
   cobrado en la pasarela un pedido Pendiente; el reintento con la misma clave de idempotencia lo corrige,
   pero sin webhooks ni conciliación (aplazados) no se declara resuelto.
2. **Vencimiento de reservas.** Se maneja solo al intentar aceptar (FR-011a); no hay barrido automático.
3. **Responsable «sistema».** La cancelación por reserva vencida no tiene cuenta responsable: se registra con
   rol `SYSTEM` y sin identificador.

Resultado: **PASA** (re-evaluado tras la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/007-gestion-estado-pedido/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/order-status-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── order/
│   ├── model/        OrderStatus (+5 estados), Order (+ transiciones), OrderStatusChange, ActorRole
│   ├── repository/   OrderRepository (+ findByIdForUpdate, pendientes por restaurante),
│   │                 OrderStatusChangeRepository
│   ├── service/      OrderStatusService (aceptar, rechazar, cancelar, avanzar; registro de cambios)
│   ├── controller/   RestaurantOrderController (nuevo); OrderController (+ cancel)
│   └── dto/          RestaurantOrderResponse, DecisionRequest (motivo opcional)
├── payment/service/  PaymentService.capture → resultado; StripeGateway distingue reserva vencida
└── common/exception/ InvalidOrderTransitionException (409), RestaurantNotEnabledException (403),
                      ReservationExpiredException; handlers en GlobalExceptionHandler
backend/src/test/java/com/mikuna/backend/order/   # unit + integration

frontend/src/
├── App.tsx                            # + /mi-restaurante/pedidos
├── features/restaurant/OrdersPage.tsx # + orders-api.ts, orders.css
└── features/checkout/OrderPage.tsx    # + botón «Cancelar pedido» y estados nuevos
```

**Structure Decision**: sin módulos nuevos (constitución I); el restaurante opera pedidos desde `order`,
como el cliente, y reutiliza la resolución `Restaurant.ownerId` ya existente.

## Complexity Tracking

Sin violaciones que justificar.
