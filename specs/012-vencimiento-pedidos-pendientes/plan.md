# Implementation Plan: Vencimiento automático de pedidos pendientes

**Branch**: `012-vencimiento-pedidos-pendientes` (sin rama Git nueva) | **Date**: 2026-11-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/012-vencimiento-pedidos-pendientes/spec.md`

## Summary

Cierra el hueco que la spec 007 dejó fuera: un pedido Pendiente sin respuesta vence a los 15 minutos
(configurable) y el sistema lo cancela liberando la reserva.

- **Backend, módulo `order`:** `Order` gana `expiresAt` (fijado al crear). Servicio nuevo
  `PendingOrderExpiryService` con `expire(orderId)` en transacción propia (bloquea el pedido, recomprueba,
  libera y cambia a Cancelado por `SYSTEM` con motivo «sin respuesta del restaurante»). Componente
  `PendingOrderExpirySweeper` con `@Scheduled` cada 15 s que procesa los vencidos de uno en uno, y un relleno
  de `expires_at` al arrancar para los pedidos anteriores (research §1, §5).
- **Aceptar y rechazar** comprueban el vencimiento con la fila bloqueada y responden 409 `ORDER_EXPIRED`
  (research §3, §4). La cancelación del cliente no cambia.
- **Respuestas** de cliente y restaurante ganan `expiresAt` y `secondsToExpire` (research §7).
- **Avisos:** `OrderStatusChangedEvent` gana `reason`; textos específicos para el vencimiento (research §8).
- **Frontend:** cuenta regresiva en `/mi-restaurante/pedidos` (con «próximo a vencer») y en `/pedido/:id`;
  lógica pura en `orders-logic.ts` con pruebas Vitest.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**: las ya instaladas; ninguna nueva (`@Scheduled` es parte de Spring).

**Storage**: PostgreSQL; columna nueva `pedidos.expires_at` e índice `(status, expires_at)` por
`ddl-auto=update`; relleno con un `UPDATE` nativo al arrancar. Sin migraciones.

**Testing**: JUnit 5 + Mockito (servicio de vencimiento, decisión con pedido vencido, textos de aviso);
`@SpringBootTest` + MockMvc sobre `mikuna_test_db` con `StripeGateway` simulado (barrido, 409
`ORDER_EXPIRED`, carreras repetidas, relleno); Vitest para la cuenta regresiva. JaCoCo ≥ 90 % de líneas de
negocio. Sin Stripe real.

**Target Platform**: igual que 002–011.

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: FR-005 / SC-001: cancelación ≤ 1 min tras vencer (barrido cada 15 s). El barrido
consulta por índice y procesa como máximo 100 pedidos por pasada.

**Constraints**:
- El vencimiento solo cambia un pedido con la fila bloqueada y el estado recomprobado (constitución IV).
- Orden de bloqueo pedido → pago, igual que la spec 007.
- Sin estados ni transiciones nuevas (FR-006); la regla del cliente no cambia (FR-015).
- El momento de vencimiento sale del servidor; la interfaz no decide si un pedido venció.

**Scale/Scope**: 0 endpoints nuevos, 2 campos nuevos en respuestas, 1 código de error nuevo, 1 columna,
1 tarea programada, 2 vistas ajustadas.

## Constitution Check

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Se amplía `order`; `order` → `payment` (ya existente). `notification` sigue escuchando eventos de `order`. Sin módulos ni ciclos nuevos. | ✅ |
| II. Responsabilidades | La tarea programada solo orquesta; la regla vive en `PendingOrderExpiryService` y en `Order`; los controladores solo traducen a HTTP. | ✅ |
| III. Seguridad | Sin endpoints nuevos; las acciones existentes conservan rol, propiedad por token y 404 para lo ajeno. La tarea no expone nada. | ✅ |
| IV. Concurrencia | Bloqueo de fila del pedido + recomprobación de estado y plazo, luego bloqueo del pago; pruebas de carrera con estado persistido (vencer ‖ aceptar/rechazar/cancelar, dos barridos). Transición Pendiente → Cancelado ya permitida («Cancelado … antes de la aceptación»; precedente de la reserva vencida, spec 007). | ✅ |
| V. Pruebas | Unitarias + integración sobre `mikuna_test_db` + Vitest; JaCoCo ≥ 90 %. | ✅ |
| VI. Trazabilidad | RF-16 (liberar al cancelar antes de aceptar), RF-17, RF-18, RF-20, RF-29 (avisos), RF-30/RF-33 (motivo visible); RNF-07 sin cambios; RNF-11. RNF-04 (varias instancias) cubierto en lógica por el bloqueo, sin evidencia de despliegue. | ✅ |
| VII. Incremental | Solo lo de la spec; cancelación tras aceptar, por administrador y reembolsos fuera. | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Pasarela dentro de la transacción.** Como en 007, la liberación llama a Stripe con la fila bloqueada. Si
   la pasarela no responde, la transacción se revierte y el pedido sigue Pendiente; el siguiente barrido lo
   reintenta (edge case «consistente y reintentable»). Un pedido así puede superar el minuto de FR-005 mientras
   dure la caída de la pasarela.
2. **Precisión del reloj.** El barrido usa el `Clock` del servidor; con varias instancias se asume reloj
   sincronizado (NTP), igual que el resto de marcas de tiempo.

Resultado: **PASA** (re-evaluado tras la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/012-vencimiento-pedidos-pendientes/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/order-expiry-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── BackendApplication.java              # + @EnableScheduling
├── order/
│   ├── model/        Order (+ expiresAt, isExpired), OrderStatusChangedEvent (+ reason)
│   ├── repository/   OrderRepository (+ ids vencidos, relleno nativo),
│   │                 OrderStatusChangeRepository (+ último cambio con motivo)
│   ├── service/      OrderExpiryPolicy (plazo, motivo), PendingOrderExpiryService (nuevo),
│   │                 PendingOrderExpirySweeper (nuevo), OrderStatusService (+ vencido al decidir),
│   │                 CheckoutTransaction (fija expiresAt)
│   ├── controller/   RestaurantOrderController (409 ORDER_EXPIRED)
│   └── dto/          OrderResponse, RestaurantOrderResponse (+ expiresAt, secondsToExpire)
├── notification/service/NotificationRecipients.java   # textos de vencimiento
└── common/exception/OrderConflictException.java      # + ORDER_EXPIRED
backend/src/main/resources/application.properties     # + mikuna.orders.*
backend/src/test/...                                  # unit + integración

frontend/src/features/
├── restaurant/orders-logic.ts (+ test)  # cuenta regresiva y «próximo a vencer»
├── restaurant/orders-api.ts, OrdersPage.tsx
└── checkout/checkout-api.ts, OrderPage.tsx
```

**Structure Decision**: sin módulos nuevos (constitución I); el vencimiento es una decisión sobre el pedido y
vive en `order`, junto a `OrderStatusService`, del que reutiliza `changeStatus`.

## Complexity Tracking

Sin violaciones que justificar.
