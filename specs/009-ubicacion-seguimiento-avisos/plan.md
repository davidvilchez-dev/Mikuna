# Implementation Plan: Ubicación, seguimiento y avisos

**Branch**: `009-ubicacion-seguimiento-avisos` | **Date**: 2026-11-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/009-ubicacion-seguimiento-avisos/spec.md`

## Summary

Implementa RF-26 a RF-29 sobre los pedidos y entregas de las specs 006 a 008:

- **Backend, módulo `order` (ampliado):** `OrderStatusService.changeStatus` publica un `OrderStatusChangedEvent`
  (con el id del `OrderStatusChange` recién guardado) y `CheckoutTransaction` publica `OrderPlacedEvent` solo
  cuando el pedido se crea (no en reenvíos idempotentes). `order` no importa `delivery` ni `notification`.
  Nuevo `GET /api/customer/orders/{id}/tracking` (RF-27/28): estado, etapas y, solo en `PICKED_UP`, la ubicación.
  `TrackingService` la obtiene mediante la interfaz `CourierLocationLookup`, que implementa `delivery`; así `order`
  no depende de `delivery`.
- **Backend, módulo `delivery` (ampliado):** `CourierLocation` (una fila por repartidor, reemplazada en cada envío),
  `DeliveryMilestone` (ubicación permanente de recogida/entrega) y `LocationService`. `PUT /api/courier/location`
  (RF-26). Un `@EventListener` síncrono, en la misma transacción del cambio de estado, guarda el hito al pasar a
  `PICKED_UP` y `DELIVERED` y borra la ubicación vigente al terminar o liberarse la entrega.
- **Backend, módulo `notification` (nuevo, depende de `order`, `restaurant`, `delivery`):** `Notification` con
  unicidad `(source_key, recipient_id)`; `NotificationListener` (`@TransactionalEventListener(AFTER_COMMIT)`, nueva
  transacción, hasta 3 intentos, nunca propaga el error) aplica la regla de destinatarios; `NotificationController`
  (`/api/notifications`) lista, cuenta no leídos y marca leídos (RF-29).
- **Tiempo real:** el cliente consulta el seguimiento cada 5 s (polling); sin WebSocket/SSE (ver research §1).
- **Frontend:** `OrderPage` muestra etapas y ubicación con actualización periódica; `CourierPage` envía la
  ubicación del navegador cada ~10 s durante una entrega en curso; campana de avisos con contador en la cabecera y
  página `/avisos` para los tres roles.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**: las ya instaladas; ninguna nueva. Sin servicio de mapas externo (ver research §3).

**Storage**: PostgreSQL local; tablas nuevas `ubicaciones_repartidor`, `hitos_entrega`, `avisos` por
`ddl-auto=update`. Sin migraciones.

**Testing**: JUnit 5 + Mockito (regla de destinatarios, validación de coordenadas, reintentos de avisos);
`@SpringBootTest` + MockMvc sobre `mikuna_test_db` (401/403/404, pertenencia, visibilidad de ubicación por estado,
no duplicados, el cambio de estado sobrevive al fallo del aviso, orden de ubicaciones); JaCoCo ≥ 90 % de líneas de
negocio; Vitest para la lógica de la interfaz. Sin Playwright.

**Target Platform**: igual que 002–008.

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: SC-001 (< 3 s), SC-002 (ubicación visible < 10 s: sondeo de 5 s). RNF-01 aplazado.

**Constraints**:
- Repartidor, cliente y destinatario salen del token; estado, entrega y propiedad nunca se aceptan del cliente.
- Pedido ajeno o inexistente → 404; avisos ajenos → 404.
- La ubicación solo se devuelve si el pedido es del cliente del token y está `PICKED_UP`.
- El fallo de un aviso nunca revierte ni bloquea el cambio de estado.

**Scale/Scope**: 6 endpoints HTTP nuevos, 3 tablas nuevas, 1 módulo nuevo, 1 página nueva (`/avisos`) y ajustes en
`OrderPage` y `CourierPage`.

## Constitution Check

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Un módulo nuevo justificado (`notification`) con `model/repository/service/controller/dto`; ubicación vive en `delivery`. `order` publica eventos y no depende de ellos. Sin ciclos. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; reglas de destinatario, visibilidad y validación en servicios; el backend decide quién ve la ubicación. | ✅ |
| III. Seguridad | `/api/courier/**`, `/api/customer/**` por rol; `/api/notifications/**` para CUSTOMER, RESTAURANT y COURIER; propiedad por token; pruebas 401/403/404. Sin integraciones externas nuevas (sin mapa externo). | ✅ |
| IV. Concurrencia | Ubicación: upsert condicional por `recorded_at` (la más reciente gana, sin sobrescribir con una antigua). Avisos: unicidad `(source_key, recipient_id)` en PostgreSQL evita duplicados ante reintentos. Hito y cambio de estado, una sola transacción. | ✅ |
| V. Pruebas | JUnit/Mockito + integración; JaCoCo ≥ 90 %. | ✅ |
| VI. Trazabilidad | RF-26…RF-29, RF-04, RNF-07, RNF-11. | ✅ |
| VII. Incremental | Solo lo de la spec; decisiones en [research.md](./research.md). | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Sondeo en lugar de empuje.** «Sin recargar» se cumple con sondeo de 5 s; evita infraestructura anticipada y
   el límite de `EventSource` con cabeceras de autorización.
2. **Avisos tras el commit.** Si el proceso cae entre el commit y la escritura del aviso, ese aviso se pierde; es
   el compromiso aceptado en la spec (el cambio de estado nunca depende del aviso).
3. **Hito desde la ubicación vigente.** Al recoger/entregar se guarda la última ubicación conocida; si el
   repartidor nunca envió una, el hito no se crea (no se inventa una posición) y el cambio de estado sigue.

Resultado: **PASA** (re-evaluado tras la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/009-ubicacion-seguimiento-avisos/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/tracking-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── order/
│   ├── model/        OrderStatusChangedEvent, OrderPlacedEvent
│   ├── service/      OrderStatusService.changeStatus publica el evento; CheckoutTransaction publica
│   │                 OrderPlacedEvent al crear; TrackingService (etapas + ubicación por estado);
│   │                 CourierLocationLookup (interfaz mínima para leer la ubicación vigente sin
│   │                 depender de `delivery`)
│   ├── controller/   OrderController (+ GET /{id}/tracking)
│   └── dto/          TrackingResponse
├── delivery/
│   ├── model/        CourierLocation, DeliveryMilestone, MilestoneType
│   ├── repository/   CourierLocationRepository (upsert condicional), DeliveryMilestoneRepository
│   ├── service/      LocationService, DeliveryMilestoneListener, CourierLocationLookupImpl
│   ├── controller/   CourierController (+ PUT /api/courier/location)
│   └── dto/          LocationRequest
├── notification/                               # módulo nuevo
│   ├── model/        Notification, NotificationType
│   ├── repository/   NotificationRepository
│   ├── service/      NotificationService (consulta, leído), NotificationListener (destinatarios, reintentos)
│   ├── controller/   NotificationController (/api/notifications)
│   └── dto/          NotificationResponse, UnreadCountResponse
├── common/security/  SecurityConfig (+ /api/notifications/** → CUSTOMER, RESTAURANT, COURIER)
└── common/exception/ LocationRejectedException (409)
backend/src/test/java/com/mikuna/backend/{delivery,notification,order}/   # unit + integration

frontend/src/
├── App.tsx                                  # + /avisos
├── features/notifications/NotificationsPage.tsx, notifications-api.ts, useUnreadCount.ts
├── features/checkout/OrderPage.tsx          # + etapas, ubicación, sondeo (tracking-api.ts, tracking-logic.ts)
└── features/delivery/CourierPage.tsx        # + envío de ubicación (courier-api.ts, location-sender.ts)
```

**Structure Decision**: `notification` es módulo propio porque los avisos son una responsabilidad transversal a
tres roles; la ubicación pertenece a `delivery` (es del repartidor y de su entrega); el seguimiento del cliente
vive en `order` porque es una vista del pedido.

## Complexity Tracking

Sin violaciones que justificar.
