# Implementation Plan: Cancelación administrativa con reembolso y cobro del efectivo al entregar

**Branch**: sin rama nueva (`main`) | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-cancelacion-reembolso/spec.md`

## Summary

El administrador podrá cancelar, con motivo obligatorio, un pedido Aceptado, En preparación, Listo,
Asignado o Recogido. Con tarjeta ya cobrada se reembolsa el total en Stripe (modo pruebas) dentro de la
misma transacción que bloquea el pedido; si la pasarela falla no cambia nada y queda constancia del
intento. El repartidor queda libre y cliente, restaurante y repartidor reciben el aviso con el motivo. El
efectivo deja de figurar cobrado al aceptar y pasa a cobrarse al entregar. Cliente y administrador ven el
reembolso. Sin migración de esquema.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1); TypeScript 6 + React 19 (Vite 8)

**Primary Dependencies**: Spring Web MVC, Spring Data JPA, Spring Security (JWT), Flyway; Stripe por su API
REST (`RestClient`, sin SDK); React Router 8

**Storage**: PostgreSQL; esquema versionado con Flyway (V1). El 014 no necesita migración (research §4)

**Testing**: JUnit 5 + Mockito + MockMvc contra `mikuna_test_db` (Stripe sustituido por mock);
`StripeGatewayTest` con servidor simulado; Vitest en el frontend

**Target Platform**: aplicación web local (backend en 8080, frontend Vite)

**Project Type**: web application (backend + frontend)

**Performance Goals**: cancelar con reembolso en una sola petición del administrador (< 15 s con la
pasarela, límite de lectura existente)

**Constraints**: Stripe solo en modo pruebas; sin webhooks; reembolso total únicamente; idempotente ante
reintentos

**Scale/Scope**: 1 endpoint nuevo, ~6 clases backend modificadas, 1 servicio nuevo; 3 pantallas frontend
(detalle admin, pedido del cliente, etiquetas de pago)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
|-----------|--------------|--------|
| I. Monolito modular | La cancelación vive en `admin/service`; el reembolso en `payment`; sin módulos nuevos | ✅ |
| II. Separación | Stripe sigue aislado en `StripeGateway`; reglas en servicios | ✅ |
| III. Seguridad | Ruta bajo `/api/admin/**` (rol ADMIN); el actor sale del token; motivo validado en backend | ✅ |
| IV. Consistencia | Bloqueo pedido → pago → repartidor; reembolso dentro de la transacción; idempotencia `refund-<orderId>`; transiciones solo por `transitionTo`. Enmienda 5.0.0 habilita la cancelación administrativa y el reembolso total | ✅ |
| V. Pruebas | Integración de cancelación, fallo de pasarela, reintento, carreras y efectivo; unitarias de pasarela, pago, avisos; Vitest de etiquetas y reglas | ✅ |
| VI. Trazabilidad | RF-16, RF-17, RF-33; FR-001..FR-018 mapeados en tasks | ✅ |
| VII. Incremental | Solo lo de la spec; sin reembolsos parciales ni solicitudes del cliente | ✅ |

Re-check tras el diseño: sin violaciones. Complejidad: ninguna que justificar.

## Project Structure

### Documentation (this feature)

```text
specs/014-cancelacion-reembolso/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/admin-order-cancel.md
├── checklists/requirements.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── admin/
│   ├── controller/AdminOrderController.java        # + POST /{id}/cancel
│   ├── dto/AdminOrderDetail.java                   # + transactions
│   ├── dto/CancelOrderRequest.java                 # nuevo
│   └── service/AdminOrderCancellationService.java  # nuevo
│       service/AdminOrderService.java              # detalle con transacciones
├── common/exception/RefundFailedException.java     # nuevo (+ GlobalExceptionHandler)
├── delivery/service/CourierDeliveryService.java    # complete → collectCash
├── notification/service/NotificationRecipients.java# avisos de cancelación por ADMIN
├── order/
│   ├── dto/OrderResponse.java                      # + cancellation
│   ├── model/Order.java                            # transiciones a CANCELLED
│   └── service/(Checkout|Order|Tracking)…          # pasan la cancelación al DTO
└── payment/
    ├── model/{Payment,PaymentStatus,TransactionType,TransactionStatus}.java
    └── service/{PaymentService,StripeGateway}.java # refund, voidCash, collectCash; efectivo no se captura al aceptar

frontend/src/features/
├── admin/{AdminOrdersPage.tsx,admin-api.ts,admin-logic.ts}  # acción Cancelar + transacciones
└── checkout/{checkout-api.ts,OrderPage.tsx}                 # REFUNDED, efectivo, cancelación
```

**Structure Decision**: web application existente (`backend/`, `frontend/`), organizada por
funcionalidad; el 014 extiende los módulos `admin`, `payment`, `order`, `delivery` y `notification`.

## Complexity Tracking

Sin violaciones de la constitución que justificar.
