# Implementation Plan: Administración de la plataforma

**Branch**: `011-administracion-plataforma` | **Date**: 2026-11-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/011-administracion-plataforma/spec.md`

## Summary

Implementa RF-32 y RF-33 sobre cuentas (spec 002), restaurantes (003), pedidos (006/007) y entregas (008):

- **Hallazgo de diseño.** Hoy «restaurante o repartidor habilitado» se deriva de `Account.status`, el mismo que
  bloquea el inicio de sesión (`AccountStatusFilter`, `RestaurantRepository.findVisible*`, `CANDIDATE` de
  `CourierAvailabilityRepository`). La clarificación 1 exige separar ambas cosas: desactivar un restaurante o
  repartidor NO bloquea el acceso de su titular. Se añade una segunda bandera en `Account`, `operation_enabled`
  (puede operar: recibir pedidos / ser candidato), independiente de `status` (puede iniciar sesión).
- **Backend, módulo `admin` (nuevo):** depende de `account`, `restaurant`, `order`, `delivery` y `payment`;
  ningún módulo depende de él (sin ciclos). Contiene:
  - `AdminAccountService`: listado paginado, búsqueda y detalle de usuarios, restaurantes y repartidores;
    activar/desactivar el acceso (`status`) y la operación (`operation_enabled`) con **actualización condicional**
    (`... where id = :id and status = :from`): solo el cambio efectivo registra la acción; repetir el mismo cambio
    responde 200 sin registrar nada (FR-010). Rechaza con 409 las cuentas de rol ADMIN, incluida la propia.
  - `AdminAccountAction` (tabla `acciones_admin_cuenta`, solo inserción): cuenta afectada, ámbito
    (`ACCESS`/`OPERATION`), acción, administrador y fecha (FR-017).
  - `AdminOrderService`: lista de pedidos y de entregas por estado, paginadas y ordenadas por `created_at` e `id`
    descendentes (misma regla que la spec 010), y detalle de pedido y de entrega con pago, repartidor y cambios de
    estado (`OrderStatusChange`). Solo lectura (FR-018).
- **Cambios mínimos en módulos existentes**, solo para aplicar `operation_enabled`: `Account` (columna y
  `canOperate()`), `RestaurantRepository.findVisible*`, los llamadores de `OrderEligibility`
  (`RestaurantService`, `RestaurantReviewService`) y `CourierAvailabilityRepository.CANDIDATE`. Los pedidos y
  entregas en curso no se tocan: `OrderStatusService.enabledRestaurant` solo exige `APPROVED`, y aceptar, recoger
  y entregar no consultan la candidatura (FR-006/FR-007).
- **Endpoints nuevos** (ver [contracts/admin-api.md](./contracts/admin-api.md)), todos bajo `/api/admin/**`, que
  `SecurityConfig` ya limita al rol ADMIN (401 sin sesión, 403 otros roles); no hace falta tocar `SecurityConfig`.
- **Frontend:** página `/admin/cuentas` (pestañas Usuarios, Restaurantes, Repartidores con búsqueda, filtro de
  estado y acciones) y página `/admin/pedidos` (pestañas Pedidos y Entregas con filtro de estado, paginación y
  detalle). Enlaces visibles solo para ADMIN.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**: las ya instaladas; ninguna nueva.

**Storage**: PostgreSQL local; columna nueva `cuentas.operation_enabled` (`boolean not null default true`, para que
`ddl-auto=update` la añada con filas existentes) y tabla nueva `acciones_admin_cuenta`. Sin migraciones.

**Testing**: JUnit 5 + Mockito (reglas de `AdminAccountService`: administrador protegido, idempotencia,
validación de filtros y página); `@SpringBootTest` + MockMvc sobre `mikuna_test_db` (401/403 por rol en todos los
endpoints; desactivar acceso → 403 en la siguiente solicitud con el token ya emitido; restaurante desactivado fuera
de búsqueda y sin pedidos nuevos pero con sus pedidos en curso avanzando; repartidor desactivado fuera de
candidatos y con su entrega en curso completándose; titular que conserva el inicio de sesión; orden, paginación y
filtros; 404; registro de acciones; dos desactivaciones simultáneas con un solo registro); JaCoCo ≥ 90 % de líneas
de negocio; Vitest para la lógica de la interfaz. Sin Playwright.

**Target Platform**: igual que 002–010.

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: listados y detalles en menos de 2 s con 1.000 cuentas y 1.000 pedidos; índices por
`(status, created_at)` en pedidos y `(account_id, created_at)` en acciones. RNF-01 aplazado.

**Constraints**:
- El administrador sale del token; ningún identificador de administrador se acepta del cliente.
- Las cuentas de rol ADMIN no se pueden desactivar (409), ni la propia.
- Sin eliminación de cuentas: no existe ningún `DELETE`.
- Ninguna respuesta incluye `password_hash` ni credenciales (FR-019).
- Estado de filtro o página inválidos → 400 con el campo; sin coincidencias → lista vacía.
- Tamaño de página por defecto 20, máximo 100 (misma regla que la spec 010).

**Scale/Scope**: 16 endpoints HTTP nuevos, 1 tabla nueva, 1 columna nueva, 1 módulo nuevo, 2 páginas nuevas.

## Constitution Check

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Un módulo nuevo justificado (`admin`) con `model/repository/service/controller/dto`; sin ciclos (nadie lo importa). Sin interfaces ni infraestructura anticipadas. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; reglas (administrador protegido, idempotencia, filtros) en servicios; el backend ignora cualquier rol, estado o administrador enviado por el cliente. | ✅ |
| III. Seguridad | `/api/admin/**` ya exige ROLE_ADMIN; rol y estado se recargan de la BD en cada acceso, así que la desactivación surte efecto en la siguiente solicitud. Pruebas 401/403 y de no exposición de credenciales. Sin integraciones externas nuevas. | ✅ |
| IV. Concurrencia | Cambio de estado de cuenta con actualización condicional en PostgreSQL; el registro de acción solo se inserta si la fila cambió, en la misma transacción. No se tocan las transiciones del pedido ni la asignación única de entrega. | ✅ |
| V. Pruebas | JUnit/Mockito + integración; JaCoCo ≥ 90 %. | ✅ |
| VI. Trazabilidad | RF-32, RF-33, RF-04, RNF-07, RNF-11. Fuera: RF-06 (003), RF-24 (008), reembolsos. | ✅ |
| VII. Incremental | Solo lo de la spec; decisiones en [research.md](./research.md). | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Dos banderas en la cuenta.** `status` (acceso) y `operation_enabled` (operar) son independientes; es el único
   cambio al modelo existente y lo exige la clarificación 1 (research §1).
2. **Spec 008, `release`.** La liberación administrativa de una entrega sigue exigiendo el *acceso* inhabilitado
   (`!courier.isEnabled()`); desactivar solo la operación del repartidor no la habilita, coherente con FR-007
   (la entrega en curso continúa).
3. **Spec 003.** Aprobar y rechazar siguen en `RestaurantAdminController`; el detalle y las acciones de
   restaurante viven en `admin` bajo rutas distintas, sin cambiar el contrato 003.

4. **Glosario.** «Desactivar/activar» = bandera de operación (restaurantes y repartidores);
   «deshabilitar/habilitar» = acceso (inicio de sesión). En la API: `deactivate`/`activate` y `disable`/`enable`.

Resultado: **PASA** (re-evaluado tras la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/011-administracion-plataforma/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/admin-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── account/model/Account.java                  # + operation_enabled, canOperate()
├── account/repository/AccountRepository.java   # + actualizaciones condicionales de acceso y operación
├── restaurant/repository/RestaurantRepository.java      # findVisible* exigen operation_enabled
├── restaurant/service/RestaurantService.java, RestaurantReviewService.java   # ownerEnabled = canOperate()
├── delivery/repository/CourierAvailabilityRepository.java   # CANDIDATE exige operation_enabled
└── admin/                                      # módulo nuevo
    ├── model/        AdminAccountAction, ActionScope, ActionType
    ├── repository/   AdminAccountActionRepository, AdminAccountQueryRepository, AdminOrderQueryRepository
    ├── service/      AdminAccountQueryService (lecturas), AdminAccountService (cambios), AdminOrderService
    ├── controller/   AdminUserController, AdminRestaurantManagementController, AdminCourierController,
    │                 AdminOrderController, AdminDeliveryQueryController
    └── dto/          AccountSummary, AccountDetail, ActionEntry, AdminOrderSummary, AdminOrderDetail,
                      AdminDeliverySummary, AdminDeliveryDetail, StatusChangeEntry
backend/src/main/java/com/mikuna/backend/common/exception/   AdminAccountProtectedException (409)
backend/src/test/java/com/mikuna/backend/admin/               # unit + integration

frontend/src/
├── App.tsx                                      # + /admin/cuentas, /admin/pedidos
└── features/admin/  AdminAccountsPage.tsx, AdminOrdersPage.tsx, admin-api.ts, admin-logic.ts
```

**Structure Decision**: un solo módulo `admin` agrupa la gestión de cuentas y la consulta de pedidos y entregas
porque comparten actor, registro y paginación, y ninguna es regla del flujo de `order` ni de `delivery`. Los
cambios a `account`, `restaurant` y `delivery` se limitan a aplicar la bandera de operación donde ya se decidía
«habilitado».

## Complexity Tracking

Sin violaciones que justificar.
