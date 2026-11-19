---

description: "Tareas de implementación: administración de la plataforma (RF-32, RF-33, RF-04)"
---

# Tasks: Administración de la plataforma

**Input**: Documentos de diseño en `specs/011-administracion-plataforma/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/admin-api.md](./contracts/admin-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra PostgreSQL local
(`mikuna_test_db`) y ≥ 90 % de líneas de lógica de negocio. Vitest para la lógica del frontend. Sin Playwright ni
otra herramienta E2E; sin Stripe (este incremento no toca el pago).

**Organization**: Tareas agrupadas por historia. US1 (consultar cuentas), US2 (activar y desactivar), US3
(pedidos y entregas por estado) y US5 (acceso exclusivo y registro) son P1; US4 (detalle de pedido y entrega) es P2.
US2 y US5 comparten el registro de acciones: el registro se crea en US2 y US5 lo verifica de punta a punta.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1–US5 según [spec.md](./spec.md)
- Rutas abreviadas: `BE/` = `backend/src/main/java/com/mikuna/backend/`; `BT/` =
  `backend/src/test/java/com/mikuna/backend/`; `FE/` = `frontend/src/`.
- Nunca abrir, copiar ni imprimir `backend/.env`.
- Las pruebas de integración extienden `IntegrationTestSupport` (`BT/support/`) y usan `TestAccounts`,
  `TestOrders`, `TestRestaurants` y `DeliveryTestSupport`; revisar sus utilidades antes de crear otras.
- Sin cambios de comportamiento en `order`, `delivery` ni `payment`: `admin` solo los lee. Las únicas ediciones a
  módulos existentes aplican `operation_enabled` (T002, T012–T015) y el índice de T003.

---

## Phase 1: Setup

Sin tareas: no hay dependencias nuevas ni configuración (plan: «ninguna nueva»).

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: bandera de operación en la cuenta, registro de acciones, errores y paginación comunes.

- [X] T001 [P] Crear `BE/admin/model/ActionScope.java` (`ACCESS`, `OPERATION`), `BE/admin/model/ActionType.java`
  (`DEACTIVATED`, `ACTIVATED`) y `BE/admin/model/AdminAccountAction.java` (tabla `acciones_admin_cuenta`, solo
  inserción, sin setters ni borrado): `id` UUID generado; `account_id` UUID not null; `scope` y `action` como
  **texto plano** (sin `@Enumerated` con CHECK, como `Account.role`); `admin_id` UUID not null; `created_at`
  Instant not null no actualizable; índice `ix_acciones_cuenta_fecha (account_id, created_at)`.
- [X] T002 [P] Editar `BE/account/model/Account.java`: añadir `operation_enabled` con
  `@Column(name = "operation_enabled", nullable = false, columnDefinition = "boolean not null default true")`
  (boolean not null default true, para que `ddl-auto=update` lo añada con filas existentes), `isOperationEnabled()`
  y `canOperate()` = `status == ENABLED && operation_enabled`. No modificar `status` ni `create(...)`.
- [X] T003 [P] Añadir en `BE/order/model/Order.java` el índice `ix_pedidos_estado_fecha` con
  `columnList = "status, created_at"` en `@Table(indexes = …)`, sin tocar columnas ni lógica.
- [X] T004 [P] Crear `BE/common/exception/AdminAccountProtectedException.java` (409, código
  `ADMIN_ACCOUNT_PROTECTED`, mismo patrón que `OrderConflictException`) y registrar su manejo en
  `BE/common/exception/GlobalExceptionHandler.java`. Reutilizar `RegistrationValidationException` para 400 con
  campo y `OrderNotFoundException`/`RestaurantNotFoundException` para 404 donde apliquen.
- [X] T005 [P] Crear `BE/admin/service/AdminPaging.java`: valida `page` entero ≥ 0 (por defecto 0) y `size`
  entero de 1 a 100 (por defecto 20); fuera de rango → `RegistrationValidationException("page"|"size", …)` con
  los mensajes de `HistoryFilter`; expone `PageRequest` y un helper para convertir enums de filtro (valor
  inexistente → 400 con el nombre del campo). Reutilizar `catalog.dto.PageResponse`.
- [X] T006 [P] Crear `BE/admin/repository/AdminAccountActionRepository.java` (`JpaRepository`) con
  `findByAccountIdOrderByCreatedAtDescIdDesc(UUID)` y la variante filtrada por `scope`.
- [X] T007 [P] Crear los DTO base en `BE/admin/dto/`: `AccountSummary` (id, name, email, role, `accessEnabled`,
  `operationEnabled` nulo para clientes, `createdAt`), `ActionEntry` (action, scope, createdAt, adminId,
  adminName) y `AccountDetail` (AccountSummary + `restaurant` opcional con nombre, categoría, dirección,
  teléfono y estado de registro 003 + `availability` opcional del repartidor + `actions[]`). Ningún DTO incluye
  `password_hash` ni credenciales (FR-019).

**Checkpoint**: modelo, errores, paginación y DTO listos; ninguna ruta nueva expuesta todavía.

---

## Phase 3: User Story 1 - El administrador consulta usuarios, restaurantes y repartidores (Priority: P1) 🎯 MVP

**Goal**: listar, buscar y ver el detalle de cuentas por categoría, con su estado y su registro de acciones.

**Independent Test**: con cuentas de cada tipo, `GET /api/admin/users`, `/restaurants/search` y `/couriers` listan,
filtran y buscan; el detalle muestra estado y acciones; id inexistente → 404.

### Tests de US1

- [X] T008 [P] [US1] Prueba unitaria `BT/admin/service/AdminAccountQueryServiceTest.java` (Mockito): escape de `%` y
  `_` en `q`, rechazo de `role`, `status`, `registration` u `operation` inválidos (400 con el campo), mapeo de
  `operationEnabled` nulo para clientes y `actions` en orden descendente.
- [X] T009 [P] [US1] Prueba de integración `BT/admin/AdminAccountListIntegrationTest.java`: cuentas de cada rol y un
  administrador; `/users` nunca devuelve ADMIN; filtros por `role`, `status` y `q` (nombre o correo, sin
  distinguir mayúsculas); `/restaurants/search` por `registration`, `operation` y `q` (nombre del restaurante o
  correo del titular); `/couriers` por `operation` y `q`; paginación (`size=2`, sin repetir ni omitir); sin
  coincidencias → lista vacía; `page=-1`, `size=0` y `size=101` → 400; detalle propio de cada categoría; id
  inexistente → 404; un id de administrador en `/restaurants/{id}` o `/couriers/{id}` → 404; ninguna respuesta
  contiene `password` ni `hash`.

### Implementación de US1

- [X] T010 [US1] Crear `BE/admin/repository/AdminAccountQueryRepository.java` con consultas JPQL paginadas sobre
  `Account` (y `Restaurant` unido por `ownerId`) con filtros opcionales; `q` con `lower(...) like :q escape '\'`;
  `/users` excluye rol ADMIN; orden fijo `name asc, id asc`. Índice o consulta sin N+1: traer restaurantes y
  disponibilidad por lote.
- [X] T011 [US1] Crear `BE/admin/service/AdminAccountQueryService.java` (`@Transactional(readOnly = true)`):
  listados y detalles de usuarios, restaurantes y repartidores; para el detalle incluye `actions` (con
  `adminName` resuelto por lote) y, en restaurante y repartidor, solo las acciones de ámbito `OPERATION`; en
  usuario, todas. Detalle de restaurante o repartidor cuyo titular sea ADMIN o no exista → 404.
- [X] T012 [US1] Crear `BE/admin/controller/AdminUserController.java` (`GET /api/admin/users`,
  `GET /api/admin/users/{id}`), `BE/admin/controller/AdminRestaurantManagementController.java`
  (`GET /api/admin/restaurants/search`, `GET /api/admin/restaurants/{id}`) y
  `BE/admin/controller/AdminCourierController.java` (`GET /api/admin/couriers`, `GET /api/admin/couriers/{id}`)
  según [contracts/admin-api.md](./contracts/admin-api.md). Verificar que `/restaurants/search` no colisiona con
  `RestaurantAdminController` (spec 003).

**Checkpoint**: US1 verificable con la API.

---

## Phase 4: User Story 2 - El administrador activa y desactiva cuentas (Priority: P1)

**Goal**: desactivar y reactivar el acceso de usuarios y la operación de restaurantes y repartidores, con registro
y efecto inmediato en el resto de la plataforma.

**Independent Test**: desactivar un usuario → su token da 403 y no inicia sesión; desactivar un restaurante → sale
de la búsqueda y no recibe pedidos nuevos, pero su titular entra y sus pedidos en curso avanzan; desactivar un
repartidor → deja de ser candidato y su entrega en curso se completa; reactivar revierte; repetir no duplica el
registro.

### Tests de US2

- [X] T013 [P] [US2] Prueba unitaria `BT/admin/service/AdminAccountServiceTest.java` (Mockito): cambio efectivo
  inserta una acción con el administrador del token; cambio ya aplicado (0 filas y cuenta ya en destino) no
  inserta y responde con el estado actual; cuenta inexistente → 404; cuenta de rol ADMIN (incluida la propia) →
  `AdminAccountProtectedException`; `activate`/`deactivate` de restaurante o repartidor usan el ámbito
  `OPERATION` y la cuenta del titular.
- [X] T014 [P] [US2] Prueba de integración `BT/admin/AdminAccountStatusIntegrationTest.java`: (a) `disable` de un
  cliente → su token ya emitido recibe 403 en la siguiente solicitud y `POST /api/auth/login` falla; `enable` →
  vuelve a entrar; (b) `deactivate` de un restaurante aprobado → ausente de `/api/public/restaurants` y de
  `/api/public/restaurants/{id}`, confirmar un pedido nuevo falla (`OrderEligibility`), su titular sigue iniciando
  sesión y viendo `/api/restaurant/orders/history`, y un pedido ya en curso se acepta, prepara y deja listo;
  `activate` lo devuelve; (c) `deactivate` de un repartidor → ausente de los candidatos y de la asignación
  automática y manual, conserva el acceso y su entrega asignada se recoge y entrega; (d) repetir cada cambio →
  200 sin acción nueva; (e) `disable` sobre el administrador (propio y otro) → 409; (f) dos `disable` simultáneos
  sobre la misma cuenta → un solo cambio y exactamente una acción registrada; (g) activar la operación de un
  restaurante PENDING no cambia su estado de registro y sigue ausente de `/api/public/restaurants`.
- [X] T015 [P] [US2] Ajustar las pruebas existentes que construyen cuentas o restaurantes si dejan de compilar por
  `Account.operation_enabled` (revisar `BT/support/TestAccounts`, `TestRestaurants` y `DeliveryTestSupport`);
  ninguna aserción previa cambia de significado.

### Implementación de US2

- [X] T016 [US2] Editar `BE/account/repository/AccountRepository.java`: añadir
  `@Modifying(clearAutomatically = true, flushAutomatically = true)` con
  `update Account a set a.status = :to where a.id = :id and a.status = :from and a.role <> 'ADMIN'` y
  `update Account a set a.operationEnabled = :to where a.id = :id and a.operationEnabled <> :to and a.role = :role`,
  ambos devolviendo `int` (actualización condicional, constitución IV).
- [X] T017 [US2] Crear `BE/admin/service/AdminAccountService.java` (`@Transactional`): `setAccess(adminId, id,
  enabled)`, `setRestaurantOperation(adminId, restaurantId, active)` y `setCourierOperation(adminId, courierId,
  active)`; carga la cuenta, rechaza ADMIN con 409, ejecuta la actualización condicional y solo si afecta 1 fila
  inserta la `AdminAccountAction` (administrador del token, fecha del `Clock`); si afecta 0 devuelve el estado
  actual sin registrar. Devuelve `AccountDetail` (reutiliza `AdminAccountQueryService`). Para restaurantes
  resuelve la cuenta por `Restaurant.ownerId`; para repartidores exige rol COURIER.
- [X] T018 [US2] Añadir (después de T012, mismos archivos) a los tres controladores de T012 los `POST` de `disable`/`enable` (usuarios) y
  `deactivate`/`activate` (restaurantes y repartidores); el administrador sale de
  `@AuthenticationPrincipal AccountPrincipal`; sin cuerpo de solicitud. No añadir ningún `DELETE`.
- [X] T019 [P] [US2] Editar `BE/restaurant/repository/RestaurantRepository.java`: en `findVisible` y
  `findVisibleById` cambiar la subconsulta a `a.status = 'ENABLED' and a.operationEnabled = true`.
- [X] T020 [P] [US2] Editar `BE/restaurant/service/RestaurantService.java` (línea de `canReceiveOrders`) y
  `BE/restaurant/service/RestaurantReviewService.java` para calcular `ownerEnabled` con `account.canOperate()`
  en lugar de `isEnabled()`. No tocar `OrderEligibility` ni `CartService` (ya reciben el resultado).
- [X] T021 [P] [US2] Editar `BE/delivery/repository/CourierAvailabilityRepository.java`: en la constante
  `CANDIDATE` añadir `and a.operation_enabled = true` junto a `a.status = 'ENABLED' and a.role = 'COURIER'`.
  No tocar `DeliveryAdminService.release` (sigue exigiendo el acceso inhabilitado, plan punto 2).
- [X] T022 [P] [US2] Frontend: crear `FE/features/admin/admin-api.ts` (cliente tipado de las rutas de cuentas con
  el token existente, mismo patrón que `FE/features/delivery/admin-deliveries-api.ts`) y
  `FE/features/admin/admin-logic.ts` (etiquetas de estado y rol, construcción de parámetros de listado, estado
  de página) con su prueba Vitest `FE/features/admin/admin-logic.test.ts`.
- [X] T023 [US2] Frontend: crear `FE/features/admin/AdminAccountsPage.tsx` (pestañas Usuarios, Restaurantes y
  Repartidores; búsqueda, filtro de estado, paginación, detalle con registro de acciones y botones
  activar/desactivar con confirmación, mensajes de 409, y enlace «Ver cuenta del titular» en el detalle de
  restaurante y repartidor que abre esa cuenta en la pestaña Usuarios, donde se bloquea su acceso), registrar la ruta `/admin/cuentas` en
  `FE/App.tsx` y mostrar el enlace solo al ADMIN en la navegación existente.

**Checkpoint**: US1 y US2 verificables; el efecto en búsqueda, pedidos, candidatos y acceso queda probado.

---

## Phase 5: User Story 3 - El administrador consulta pedidos y entregas por estado (Priority: P1)

**Goal**: listados de todos los pedidos y entregas de la plataforma, filtrados por estado, paginados y del más
reciente al más antiguo.

**Independent Test**: con pedidos y entregas en distintos estados, filtrar por un estado devuelve solo esos,
ordenados por `created_at` e `id` descendentes y paginados; estado inválido → 400; sin coincidencias → lista vacía.

### Tests de US3

- [X] T024 [P] [US3] Prueba unitaria `BT/admin/service/AdminOrderServiceTest.java` (Mockito): estado de pedido
  inválido → 400 con `status`; estado de entrega fuera de READY/ASSIGNED/PICKED_UP/DELIVERED → 400; página y
  tamaño fuera de rango → 400; mapeo de repartidor nulo en entregas sin asignar.
- [X] T025 [P] [US3] Prueba de integración `BT/admin/AdminOrderListIntegrationTest.java`: pedidos de varios
  clientes y restaurantes en estados distintos; `/orders` sin filtro devuelve todos, del más reciente al más
  antiguo, con paginación sin repetir ni omitir; `?status=DELIVERED` solo entregados; `/deliveries` con
  `?status=ASSIGNED` y `READY`; `?status=PENDING` en `/deliveries` → 400; filtro sin coincidencias → lista vacía;
  pedidos de restaurantes o repartidores desactivados siguen visibles.
- [X] T025a [P] [US3] Prueba de integración `BT/admin/AdminPerformanceIntegrationTest.java`: con 1.000 cuentas y
  1.000 pedidos sembrados, la primera página de `/users`, `/orders` y `/deliveries` responde en menos de 2 s
  (misma técnica que `BT/history/HistoryPerformanceIntegrationTest`).

### Implementación de US3

- [X] T026 [P] [US3] Crear los DTO `BE/admin/dto/AdminOrderSummary.java` (id, status, createdAt, total, cliente
  {id, name}, restaurante {id, name guardado}, estado del pago) y `BE/admin/dto/AdminDeliverySummary.java`
  (id de la entrega, orderId, status del pedido, createdAt del pedido, repartidor {id, name} opcional,
  restaurante {name}).
- [X] T027 [US3] Crear `BE/admin/repository/AdminOrderQueryRepository.java` con consultas paginadas de
  `Order` (filtro opcional por estado) y de `Delivery` unida a `Order` (filtro opcional por estado del pedido),
  ambas con orden fijo `o.createdAt desc, o.id desc`.
- [X] T028 [US3] Crear `BE/admin/service/AdminOrderService.java` (`@Transactional(readOnly = true)`) con
  `listOrders` y `listDeliveries`; resuelve por lote nombres de clientes, repartidores y estado de pago
  (`PaymentRepository`); sin N+1. Solo lectura (FR-018).
- [X] T029 [P] [US3] Crear `BE/admin/controller/AdminOrderController.java` (`GET /api/admin/orders`) y
  `BE/admin/controller/AdminDeliveryQueryController.java` (`GET /api/admin/deliveries`); comprobar que no
  colisionan con `AdminDeliveryController` (`/pending`, `/{orderId}/assign`, `/{orderId}/release`).
- [X] T030 [P] [US3] Frontend: ampliar `FE/features/admin/admin-api.ts` y `admin-logic.ts` (estados de pedido y de
  entrega, etiquetas) con pruebas en `admin-logic.test.ts`; crear `FE/features/admin/AdminOrdersPage.tsx`
  (pestañas Pedidos y Entregas, filtro de estado, paginación, lista vacía) y registrar `/admin/pedidos` en
  `FE/App.tsx` con enlace solo para ADMIN.

**Checkpoint**: RF-33 listados verificables.

---

## Phase 6: User Story 4 - El administrador ve el detalle de un pedido o entrega (Priority: P2)

**Goal**: detalle con estado de pago y cambios de estado.

**Independent Test**: el detalle de un pedido entregado muestra cliente, restaurante, repartidor, productos,
importes, pago y la secuencia cronológica de cambios con fecha; el de una entrega añade el pedido asociado.

### Tests de US4

- [X] T031 [P] [US4] Prueba de integración `BT/admin/AdminOrderDetailIntegrationTest.java`: pedido entregado →
  cliente, restaurante, repartidor, líneas con precios fijados, importes, estado de pago `CAPTURED` y
  `statusChanges` en orden cronológico con actor y rol; pedido rechazado → pago `RELEASED` y sin repartidor;
  pedido ajeno a cualquier filtro sigue accesible; `GET /api/admin/deliveries/{id}` con el pedido asociado y su
  pago; ids inexistentes → 404; ninguna respuesta contiene `password`.

### Implementación de US4

- [X] T032 [P] [US4] Crear `BE/admin/dto/StatusChangeEntry.java` (from, to, changedAt, actorId, actorName
  opcional, actorRole, reason), `BE/admin/dto/AdminOrderDetail.java` (resumen + líneas con nombre, precio unitario,
  cantidad y total de línea + subtotal, envío, comisión, total + pago {status, amount} + entrega opcional +
  `statusChanges[]`) y `BE/admin/dto/AdminDeliveryDetail.java` (entrega, estado, repartidor, `statusChanges[]` y
  pedido asociado con su estado de pago).
- [X] T033 [US4] Ampliar `BE/admin/service/AdminOrderService.java` con `orderDetail(id)` y `deliveryDetail(id)`
  (usa `OrderStatusChangeRepository` ordenado por `changedAt` e `id`; ids inexistentes → `OrderNotFoundException`
  → 404); ampliar `AdminOrderController` y `AdminDeliveryQueryController` con `GET /api/admin/orders/{id}` y
  `GET /api/admin/deliveries/{id}`.
- [X] T034 [P] [US4] Frontend: detalle desplegable en `FE/features/admin/AdminOrdersPage.tsx` con pago, productos
  y cambios de estado; funciones de formato en `admin-logic.ts` con prueba Vitest.

**Checkpoint**: RF-33 completo.

---

## Phase 7: User Story 5 - Acceso exclusivo del administrador y registro de acciones (Priority: P1)

**Goal**: solo ADMIN accede; cada acción efectiva queda registrada con fecha y responsable.

**Independent Test**: cada endpoint rechaza cliente, restaurante, repartidor (403) y petición sin sesión (401);
cada cambio efectivo aparece en el detalle con fecha y administrador, y los no efectivos no.

### Tests de US5

- [X] T035 [P] [US5] Prueba de integración `BT/admin/AdminAccessControlIntegrationTest.java`: recorrer todos los
  endpoints de [contracts/admin-api.md](./contracts/admin-api.md) (GET y POST) con token de CUSTOMER, RESTAURANT y
  COURIER → 403; sin token → 401; token de ADMIN deshabilitado → 403; verificar que tras los rechazos ninguna
  cuenta cambió de estado y no se registró ninguna acción. Además, `POST`, `PUT`, `PATCH` y `DELETE` sobre
  `/api/admin/orders`, `/api/admin/orders/{id}`, `/api/admin/deliveries` y `/api/admin/deliveries/{id}` responden
  405 con token de ADMIN y no cambian ningún pedido ni entrega (FR-018).
- [X] T036 [P] [US5] Prueba de integración `BT/admin/AdminAuditIntegrationTest.java`: dos administradores
  desactivan y reactivan cuentas; el detalle lista cada acción efectiva con acción, ámbito, fecha y nombre del
  administrador responsable, en orden descendente; los cambios repetidos no aparecen; el registro no tiene
  endpoint de modificación ni de borrado (405 o 404 en `PUT`/`PATCH`/`DELETE` sobre cuentas).

### Implementación de US5

- [X] T037 [US5] Comprobar en `BE/common/security/SecurityConfig.java` que `/api/admin/**` sigue siendo
  `hasRole("ADMIN")` y que ninguna ruta nueva queda fuera; corregir solo si una prueba de T035 falla.

**Checkpoint**: acceso y trazabilidad verificados.

---

## Phase 8: Polish & Cross-Cutting

- [X] T038 [P] Medir la cobertura con JaCoCo y confirmar ≥ 90 % de líneas de lógica de negocio del paquete
  `admin` y de las ediciones en `account`, `restaurant` y `delivery`; reportar código medido, líneas cubiertas y
  totales, porcentaje y exclusiones justificadas (constitución V).
- [X] T039 [P] Ejecutar `./mvnw test` y `npm test`/`npm run build` en `frontend/`; ejecutar los pasos de
  [quickstart.md](./quickstart.md) en local y registrar resultados en `specs/011-administracion-plataforma/validation.md`
  con la trazabilidad RF-32, RF-33, RF-04, RNF-07 y RNF-11 (pendiente, parcial o verificado).
- [X] T040 Revisión final: confirmar que no existe ningún `DELETE` de cuentas, que ninguna respuesta expone
  credenciales, que RNF-06/RNF-08 siguen sin declararse cumplidos y que `validation.md` no atribuye cumplimiento
  de RNF no medidos.

---

## Dependencies & Execution Order

- **Foundational (T001–T007)** bloquea todo. Dentro de ella, todo es `[P]`.
- **US1 (T008–T012)** depende de T001, T002, T005–T007. Es el MVP de lectura.
- **US2 (T013–T023)** depende de T001, T002, T004 y de T011 (para devolver `AccountDetail`); T019–T021 tocan
  archivos distintos y se hacen en paralelo; T014 requiere T016–T021.
- **US3 (T024–T030)** depende de T003 y T005; es independiente de US1 y US2.
- **US4 (T031–T034)** depende de US3 (mismos servicio y controladores).
- **US5 (T035–T037)** depende de que existan los endpoints de US1–US4 (T035) y de US2 (T036).
- **Polish (T038–T040)** al final.

## Parallel Example

- Tras la fase 2: US1 (T010–T012), US2 (T016–T021) y US3 (T026–T029) en paralelo por desarrolladores distintos,
  porque tocan archivos distintos salvo `AdminAccountQueryService`, que US2 consume.
- Dentro de US2: T019, T020 y T021 a la vez; T022 en el frontend a la vez que el backend.

## Implementation Strategy

1. **MVP**: Foundational + US1 + US2 (consultar y desactivar cuentas con efecto real). Detener y validar con los
   pasos 3–9 del quickstart.
2. **Incremento 2**: US3 y US5 (supervisión de pedidos y entregas, acceso y registro verificados).
3. **Incremento 3**: US4 (detalle) y Polish con cobertura y `validation.md`.

## Trazabilidad

RF-32 → US1, US2, US5; RF-33 → US3, US4; RF-04/RNF-07 → US5 (T035); RNF-11 → T038. RF-06 (spec 003) y RF-24
(spec 008) quedan fuera de alcance y no se modifican.
