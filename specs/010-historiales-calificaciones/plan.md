# Implementation Plan: Historiales y calificaciones

**Branch**: `010-historiales-calificaciones` | **Date**: 2026-11-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/010-historiales-calificaciones/spec.md`

## Summary

Implementa RF-30 y RF-31 sobre los pedidos, pagos y entregas de las specs 006 a 008:

- **Backend, módulo `history` (nuevo, solo lectura salvo la calificación):** depende de `order`, `delivery`,
  `payment`, `restaurant` y `account`; ningún módulo depende de él (sin ciclos). Contiene:
  - `HistoryService`: tres consultas paginadas (cliente, restaurante, repartidor) con filtro por estado y por
    rango de fechas, ordenadas por `createdAt` y `id` descendentes. El actor sale del token; el filtro de
    pertenencia va dentro de la consulta (`customer_id`, `restaurant_id` del restaurante propio, `courier_id`).
  - `Rating` (tabla `calificaciones`, unicidad por `order_id`) y `RatingService`: valida pedido propio, estado
    `DELIVERED` y valor 1–5; la unicidad en PostgreSQL resuelve las solicitudes simultáneas.
- **Endpoints nuevos (ver [contracts/history-api.md](./contracts/history-api.md)):**
  `GET /api/customer/orders/history`, `GET /api/restaurant/orders/history`, `GET /api/courier/deliveries/history`
  y `POST /api/customer/orders/{id}/rating`. Reutilizan los matchers por rol de `SecurityConfig`.
- **Sin cambios en `order`, `delivery` ni `payment`:** el pedido ya guarda nombres, precios e importes fijados
  (`OrderLine`, `Order`), la entrega conserva su `courier_id` al terminar, y el pago tiene su estado. El historial
  solo los lee (FR-005 se cumple por construcción).
- **Frontend:** página `/historial` común a CLIENTE, RESTAURANTE y REPARTIDOR (misma lista paginada con filtros,
  distinta fuente según el rol) y control de calificación en la tarjeta del pedido entregado del cliente.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**: las ya instaladas; ninguna nueva.

**Storage**: PostgreSQL local; tabla nueva `calificaciones` por `ddl-auto=update`. Sin migraciones.

**Testing**: JUnit 5 + Mockito (validación de calificación, normalización de página y filtros);
`@SpringBootTest` + MockMvc sobre `mikuna_test_db` (401/403/404, aislamiento entre usuarios, orden y paginación sin
repetir ni omitir, filtros, datos congelados tras cambiar y retirar un producto, una sola calificación bajo
solicitudes simultáneas); JaCoCo ≥ 90 % de líneas de negocio; Vitest para la lógica de la interfaz. Sin Playwright.

**Target Platform**: igual que 002–009.

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: SC-001 (primera página < 2 s con 1.000 operaciones) con índices por actor y fecha.
RNF-01 aplazado.

**Constraints**:
- Cliente, restaurante y repartidor salen del token; ningún identificador de actor se acepta del cliente.
- Pedido ajeno o inexistente → 404 al calificar; el historial solo devuelve filas propias.
- Restaurante: del cliente solo el nombre. Repartidor: nombre y dirección de entrega, sin teléfono; sin comisión,
  importes ni precios (FR-016, FR-017).
- El historial no exige que el restaurante o el repartidor estén habilitados para recibir pedidos; la cuenta
  bloqueada ya no inicia sesión (RF-04).
- La calificación es inmutable: sin endpoints de edición ni borrado.
- Tamaño de página por defecto 20, máximo 100.

**Scale/Scope**: 4 endpoints HTTP nuevos, 1 tabla nueva, 1 módulo nuevo, 1 página nueva (`/historial`) y un
control de calificación en `OrderPage`.

## Constitution Check

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Un módulo nuevo justificado (`history`) con `model/repository/service/controller/dto`; sin ciclos (nadie lo importa). Sin interfaces ni infraestructura anticipadas. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; pertenencia, estado y valor se validan en servicios; el backend ignora cualquier actor o estado enviado por el cliente. | ✅ |
| III. Seguridad | Rutas bajo `/api/customer`, `/api/restaurant` y `/api/courier` ya protegidas por rol; pruebas 401/403/404 y de aislamiento. Sin integraciones externas nuevas. | ✅ |
| IV. Concurrencia | RF-31: unicidad `order_id` en PostgreSQL; la violación se traduce en 409 sin tocar la original. El estado `DELIVERED` es final, así que comprobarlo antes de insertar no admite carreras. | ✅ |
| V. Pruebas | JUnit/Mockito + integración; JaCoCo ≥ 90 %. | ✅ |
| VI. Trazabilidad | RF-30, RF-31, RF-04, RNF-07, RNF-11. | ✅ |
| VII. Incremental | Solo lo de la spec; decisiones en [research.md](./research.md). | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Paginación por desplazamiento.** Con ordenación fija por fecha de creación e id, recorrer páginas no repite
   ni omite mientras no lleguen pedidos nuevos; uno nuevo desplaza las páginas siguientes un elemento. Se
   acepta; un cursor sería infraestructura anticipada (research §2).
2. **Historial del repartidor.** Lista `Delivery` con `courier_id` igual al propio y pedido `ASSIGNED`,
   `PICKED_UP` o `DELIVERED`. Las entregas que rechazó o le quitaron ya no tienen su `courier_id`, así que no
   aparecen (decisión de la clarificación 1).
3. **Spec 011.** Las vistas de administración quedan fuera; `history` no expone nada al rol ADMIN.

Resultado: **PASA** (re-evaluado tras la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/010-historiales-calificaciones/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/history-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── history/                                    # módulo nuevo
│   ├── model/        Rating
│   ├── repository/   RatingRepository, HistoryQueryRepository (consultas paginadas con filtros)
│   ├── service/      HistoryService (3 historiales), RatingService (RF-31)
│   ├── controller/   CustomerHistoryController, RestaurantHistoryController, CourierHistoryController
│   └── dto/          HistoryPage, HistoryItem, RatingRequest, RatingResponse
└── common/exception/ RatingConflictException (409); reutiliza OrderNotFoundException (404)
backend/src/test/java/com/mikuna/backend/history/   # unit + integration

frontend/src/
├── App.tsx                                  # + /historial
└── features/history/HistoryPage.tsx, history-api.ts, history-logic.ts (filtros, página, estrellas)
    features/checkout/OrderPage.tsx          # + control de calificación
```

**Structure Decision**: un solo módulo `history` agrupa historial y calificación porque ambos son vistas del
pedido terminado y comparten pertenencia y lectura; no pertenecen a `order` (que gestiona el flujo) ni a
`delivery`. Las tres rutas viven bajo el prefijo de su rol para reutilizar la autorización existente.

## Complexity Tracking

Sin violaciones que justificar.
