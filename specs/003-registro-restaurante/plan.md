# Implementation Plan: Registro, aprobación y datos del restaurante

**Branch**: `003-registro-restaurante` (directorio de spec; sin rama Git nueva) | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-registro-restaurante/spec.md`

## Summary

Este incremento implementa RF-05, RF-06 y RF-07, con la autorización de RF-04 aplicada a cada
operación. Las piezas son:

- **Backend:** un módulo nuevo `restaurant` (entidad, horarios, reglas de solicitud, resolución y
  gestión) con dos controladores: `/api/restaurant/me` para el titular y `/api/admin/restaurants`
  para el administrador.
- **Seguridad:** el rol se exige por ruta (`ROLE_RESTAURANT` / `ROLE_ADMIN`, ya cargado desde la BD
  por `AccountStatusFilter`). El estado del restaurante se valida en el servicio. La pertenencia
  se garantiza sin recibir ids: el restaurante propio se resuelve siempre desde el token.
- **Consistencia:** `UNIQUE(owner_id)` para una única solicitud por cuenta y actualización
  condicional en PostgreSQL (`WHERE status = ...`) para que solo prospere una resolución.
- **Habilitación:** `acceptingOrders` se calcula en el servicio (aprobado + cuenta habilitada +
  disponible + dentro de horario) y se devuelve al titular y al administrador.
- **Frontend:** páginas React `/mi-restaurante` (solicitud, estado, datos, horario, disponibilidad)
  y `/admin/solicitudes` (revisión), enlazadas desde `/mi-cuenta`.
- **Adaptación de 002:** se retira `restaurantApproval` de `Account` y `AccountResponse`; el estado
  comercial pasa a ser del `Restaurant`.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19 (sin cambios)

**Primary Dependencies**: las ya instaladas en 002 (Web MVC, Data JPA, Security, Bean Validation,
`react-router`). No se añade ninguna.

**Storage**: PostgreSQL local; tablas nuevas `restaurantes` y `horarios_restaurante` creadas por
`ddl-auto=update`. Sin migraciones.

**Testing**: JUnit 5 + Mockito (reglas de solicitud, resolución, habilitación); `@SpringBootTest` +
MockMvc sobre `mikuna_test_db` (401/403/404/409, concurrencia); JaCoCo ≥ 90 % de líneas de negocio;
Vitest para la validación de horarios del frontend. Sin Playwright.

**Target Platform**: igual que 002 (HTTP en localhost; Vite 5173 → API 8080).

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: sin objetivo propio (RNF-01 aplazado).

**Constraints**:
- Estado, titular y aprobación nunca se aceptan del cliente (el Jackson estricto de 002 rechaza
  campos desconocidos con 400).
- Errores en `ProblemDetail`, sin datos de otros restaurantes.
- Zona horaria de la plataforma configurable (`mikuna.timezone`, por defecto `America/Lima`).

**Scale/Scope**: 9 endpoints, 2 tablas, 2 páginas nuevas y ajustes menores en `AccountPage`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Un solo módulo nuevo (`restaurant`) con `model`, `repository`, `service`, `controller` y `dto`. Depende de `account` (lectura de cuentas) y no al revés. Sin interfaces vacías. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; reglas y transiciones en el servicio; repositorios JPA. El backend recalcula estado y habilitación; el cliente no envía estado, titular ni aprobación. | ✅ |
| III. Seguridad | Todas las rutas protegidas (ninguna pública nueva). Rol por ruta + estado de cuenta (filtro de 002) + estado del restaurante en el servicio + pertenencia por token. Solo un restaurante aprobado puede quedar habilitado. Pruebas 401/403/ajeno. | ✅ |
| IV. Concurrencia | Una solicitud por cuenta: `UNIQUE(owner_id)`. Una resolución por solicitud: `UPDATE … WHERE status='PENDING'` con recuento de filas. Pruebas de integración concurrentes. Sin bloqueos en memoria. | ✅ |
| V. Pruebas | JUnit + Mockito, integración contra PostgreSQL local, JaCoCo ≥ 90 % y Vitest. Sin Playwright. | ✅ |
| VI. Trazabilidad | RF-05, RF-06, RF-07 y RF-04 (parcial: pedidos y entregas pendientes). RNF-07 y RNF-11 aplicables. RF-08/09/10 y RF-32 quedan fuera. | ✅ |
| VII. Incremental | Solo lo de la spec, con tecnologías ya elegidas. Sin dependencias nuevas. Decisiones en [research.md](./research.md). | ✅ |

**Discrepancia a señalar (sin ampliar el alcance):** la spec 002 guardaba `restaurantApproval` en
`cuentas` y su contrato y pruebas lo exponen. Aquí se sustituye por la entidad `Restaurant`; se
actualizan el código, las pruebas y el contrato de 002 afectados (lista en
[data-model.md](./data-model.md)). La columna `restaurant_approval` de bases locales existentes
queda sin uso (nullable); `ddl-auto=update` no la elimina.

Resultado: **PASA** (re-evaluado tras el diseño de la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/003-registro-restaurante/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/restaurant-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── account/                       # − RestaurantApproval; Account sin restaurantApproval
├── auth/dto/AccountResponse.java  # − restaurantApproval
├── restaurant/
│   ├── model/        Restaurant, RestaurantStatus, OpeningHour (embeddable)
│   ├── repository/   RestaurantRepository (+ resoluciones condicionales)
│   ├── service/      RestaurantService (solicitud, datos, horario, disponibilidad),
│   │                 RestaurantReviewService (listar, aprobar, rechazar),
│   │                 OrderEligibility (regla «habilitado para recibir pedidos»)
│   ├── controller/   RestaurantController (/api/restaurant/me), RestaurantAdminController
│   └── dto/          RestaurantRequest, ScheduleRequest, AvailabilityRequest, RejectRequest,
│                     RestaurantResponse, AdminRestaurantResponse
├── common/security/SecurityConfig.java   # + reglas por rol de /api/restaurant/** y /api/admin/**
└── common/exception/  # + RestaurantNotFoundException (404), RestaurantStateException (403), conflicto (409)
backend/src/test/java/com/mikuna/backend/restaurant/   # unit + integration

frontend/src/
├── App.tsx                         # + rutas /mi-restaurante y /admin/solicitudes
└── features/restaurant/
    ├── RestaurantPage.tsx          # solicitud, estado, datos, horario y disponibilidad
    ├── AdminRequestsPage.tsx       # lista pendientes, aprobar y rechazar con motivo
    ├── restaurant-api.ts           # fetch a /api/restaurant/me y /api/admin/restaurants
    ├── restaurant-validation.ts    # reglas espejo y de horario (+ .test.ts)
    └── restaurant.css
frontend/src/features/auth/AccountPage.tsx   # enlaces según rol; sin restaurantApproval
```

**Structure Decision**: se mantiene la aplicación web existente. El backend añade el módulo
`restaurant` por funcionalidad (constitución I); el frontend añade `features/restaurant` junto a
`features/auth`.

## Complexity Tracking

Sin violaciones que justificar.
