---

description: "Tareas de implementación: historiales y calificaciones (RF-30, RF-31, RF-04)"
---

# Tasks: Historiales y calificaciones

**Input**: Documentos de diseño en `specs/010-historiales-calificaciones/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/history-api.md](./contracts/history-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra PostgreSQL local
(`mikuna_test_db`) y ≥ 90 % de líneas de lógica de negocio. Vitest para la lógica del frontend. Sin Playwright ni
otra herramienta E2E; sin Stripe (este incremento no toca el pago).

**Organization**: Tareas agrupadas por historia. US1 (historial del cliente) y US2 (calificar) son P1 y el MVP;
US3 (restaurante), US4 (repartidor), US5 (orden, paginación y filtros) y US6 (datos congelados) son P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1–US6 según [spec.md](./spec.md)
- Rutas abreviadas: `BE/` = `backend/src/main/java/com/mikuna/backend/`; `BT/` =
  `backend/src/test/java/com/mikuna/backend/`; `FE/` = `frontend/src/`.
- Nunca abrir, copiar ni imprimir `backend/.env`.
- Las pruebas de integración extienden `IntegrationTestSupport` (`BT/support/`) y usan `TestAccounts`,
  `TestOrders`, `TestRestaurants` y `DeliveryTestSupport`; revisar sus utilidades antes de crear otras.
- Sin cambios de comportamiento en `order`, `delivery` ni `payment`: `history` solo los lee (única edición: el índice de T003).

---

## Phase 1: Setup

Sin tareas: no hay dependencias nuevas ni configuración (plan: «ninguna nueva»).

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: DTO comunes, validación de parámetros y consulta base paginada, comunes a los tres historiales.

- [X] T001 [P] Crear `BE/history/dto/HistoryItem.java` y `BE/history/dto/HistoryPage.java` según [data-model.md](./data-model.md): `HistoryItem` con `orderId`, `status`, `createdAt`, `restaurant` (id, nombre guardado, nulo en el historial del restaurante), `customerName` (nulo en el del cliente), `lines` (productId, name, unitPrice, quantity, lineTotal; en el del repartidor solo name y quantity), `totals` (subtotal, deliveryFee, commission, total; nulo en el del repartidor), `deliveryFee` (solo repartidor), `deliveryAddress` (street, district, reference; solo repartidor), `payment` (method, status; nulo en el del repartidor), `rating` (entero o nulo) y `canRate`; `HistoryPage` con `items`, `page`, `size`, `totalItems`, `totalPages`. El restaurante no ve dirección ni teléfono del cliente; el repartidor ve solo la dirección de entrega, sin teléfono (FR-016, FR-017).
- [X] T002 [P] Crear `BE/history/service/HistoryFilter.java` (record `status`, `from`, `to`, `page`, `size` con método estático de construcción y validación): `page` entero ≥ 0 (por defecto 0); `size` entero de 1 a 100 (por defecto 20); `status` uno de los valores de `OrderStatus`; `from` y `to` `yyyy-MM-dd`; `from` posterior a `to`, fecha mal formada o `status` inválido → `RegistrationValidationException` (400) con el nombre del campo. Convertir el rango a instantes `[from 00:00, to+1 día 00:00)` en la zona de la plataforma, leída de la propiedad existente `mikuna.timezone` (`MIKUNA_TIMEZONE`, por defecto `America/Lima`; la misma que usa `OrderEligibility`), sin constante propia; inclusivo en ambos extremos.
- [X] T003 [P] Añadir en `BE/order/model/Order.java` el índice `ix_pedidos_restaurante_fecha` con `columnList = "restaurant_id, created_at"` en `@Table(indexes = …)`, sin tocar columnas ni lógica.
- [X] T004 [P] Crear `BE/history/repository/HistoryQueryRepository.java` (Spring Data, `JpaRepository<Order, UUID>` solo de lectura) con tres consultas JPQL paginadas, todas con orden fijo `o.createdAt desc, o.id desc` y filtros opcionales de estado y rango (`:status is null or o.status = :status`, `o.createdAt >= :from`, `o.createdAt < :to`): `findForCustomer(customerId, …)` (`o.customerId = :customerId`), `findForRestaurant(restaurantId, …)` (`o.restaurantId = :restaurantId`) y `findForCourier(courierId, statuses, …)` (`exists` sobre `Delivery d` con `d.orderId = o.id and d.courierId = :courierId` y `o.status in :statuses`).
- [X] T009 [P] Crear `BE/history/model/Rating.java` y `BE/history/repository/RatingRepository.java` según [data-model.md](./data-model.md) (tabla `calificaciones`; `order_id` único, `customer_id`, `value` entero de 1 a 5, `created_at`; columnas no actualizables) con `findByOrderIdIn(Collection<UUID>)`. Necesario para que T005 muestre la calificación en el historial.
- [X] T005 [P] Crear `BE/history/service/HistoryAssembler.java`: dado una página de `Order`, obtiene en una consulta por lote cada `Payment` (por `PaymentRepository`), cada `Rating` (por `RatingRepository`, ver T009) y, cuando se pida, cada nombre de cliente (por `AccountRepository`), y construye los `HistoryItem` sin consultas por fila (research §5). `payment` solo cuando se pide; `canRate = status == DELIVERED y sin calificación`. Admite una variante «repartidor» (líneas de nombre y cantidad, `deliveryAddress` y `deliveryFee`, sin `totals` ni `payment`) que usa T028.
- [X] T006 [P] Crear `BE/common/exception/RatingConflictException.java` (409 con código `ORDER_NOT_DELIVERED` o `RATING_ALREADY_EXISTS`, mismo patrón que `OrderConflictException`) y registrar su manejo en `BE/common/exception/GlobalExceptionHandler.java` como `application/problem+json`.

**Checkpoint**: DTO, validación, consultas y ensamblador listos; ninguna ruta nueva expuesta todavía.

---

## Phase 3: User Story 1 - El cliente consulta el historial de sus pedidos (Priority: P1) 🎯 MVP

**Goal**: el cliente ve todos sus pedidos (incluidos Rechazado y Cancelado) con estado, productos, importes y pago.

**Independent Test**: con un cliente con pedidos Entregado, Rechazado y Cancelado, `GET /api/customer/orders/history`
devuelve los tres, solo los suyos, y 401 sin sesión.

### Tests for User Story 1

- [X] T007 [P] [US1] `BT/history/service/HistoryFilterTest.java` (unitaria): valores por defecto, `size` 0 y 101 rechazados, `page` negativa, estado inválido, fecha mal formada, `from` > `to`, rango inclusivo en la zona de la plataforma (`mikuna.timezone`).
- [X] T008 [US1] `BT/history/CustomerHistoryIntegrationTest.java` (integración): el cliente A ve sus pedidos Entregado, Rechazado y Cancelado con estado, líneas, importes y pago; el cliente B no ve ninguno de A; cliente sin pedidos → `items: []`; 401 sin token; 403 con rol restaurante, repartidor y administrador.

### Implementation for User Story 1

- [X] T010 [US1] Crear `BE/history/service/HistoryService.java` con `customerHistory(customerId, HistoryFilter)`: consulta de T004, ensamblado de T005 (con `payment`, sin `customerName`) y `HistoryPage`.
- [X] T011 [US1] Crear `BE/history/controller/CustomerHistoryController.java`: `GET /api/customer/orders/history` con `@AuthenticationPrincipal`, parámetros `page`, `size`, `status`, `from`, `to` (texto, validados por `HistoryFilter`). Añadir una prueba (en T008) de que `/history` no se interpreta como `/{id}` de `OrderController`.
- [X] T012 [P] [US1] `FE/features/history/history-api.ts` (cliente HTTP con `apiRequest`, tipos `HistoryItem`, `HistoryPage`, función `fetchHistory(role, token, filter)` que elige `/api/customer/orders/history`, `/api/restaurant/orders/history` o `/api/courier/deliveries/history` según el rol).
- [X] T013 [US1] `FE/features/history/HistoryPage.tsx` y la ruta `/historial` en `FE/App.tsx`: lista del historial con estado, fecha, productos, importes y pago, mensaje claro cuando no hay operaciones, y botón «Ver más» para la siguiente página; enlace «Historial» para CLIENTE, RESTAURANTE y REPARTIDOR en `FE/features/auth/AuthLayout.tsx`, junto a `NotificationsBell`. (Implementado en `FE/features/notifications/NotificationsBell.tsx`, que `AuthLayout` ya renderiza y que solo aparece para los roles con avisos e historial.)

**Checkpoint**: US1 funciona de forma independiente (MVP de RF-30 para el cliente).

---

## Phase 4: User Story 2 - El cliente califica un pedido entregado (Priority: P1)

**Goal**: el cliente emite una calificación entera de 1 a 5 para un pedido propio Entregado, una sola por pedido.

**Independent Test**: sobre un pedido Entregado propio, `POST /api/customer/orders/{id}/rating` con `{"value":4}` → 201; un
segundo intento → 409; pedido no entregado → 409; pedido ajeno → 404; valor inválido → 400.

### Tests for User Story 2

- [X] T014 [P] [US2] `BT/history/service/RatingServiceTest.java` (unitaria con Mockito): valores 1 y 5 aceptados; 0, 6, 3.5, nulo y texto rechazados sin guardar; pedido no entregado → `ORDER_NOT_DELIVERED`; pedido ajeno o inexistente → `OrderNotFoundException`; violación de unicidad → `RATING_ALREADY_EXISTS` sin modificar la original.
- [X] T015 [US2] `BT/history/RatingIntegrationTest.java` (integración): 201 y fila persistida con cliente y fecha; segundo intento 409 y valor original intacto; pedidos Pendiente, Rechazado y Cancelado → 409; pedido de otro cliente y pedido inexistente → 404 idénticos; `value` 0, 6, 3.5, ausente y `"abc"` → 400 sin fila; 401/403; no existen `PUT`, `PATCH` ni `DELETE` sobre la calificación (405/404).
- [X] T016 [US2] `BT/history/RatingConcurrencyIntegrationTest.java`: dos y cinco solicitudes simultáneas sobre el mismo pedido Entregado (hilos con `CountDownLatch`) → exactamente un 201 y el resto 409; una sola fila en `calificaciones` (SC-005).

### Implementation for User Story 2

- [X] T017 [P] [US2] Crear `BE/history/dto/RatingRequest.java` (`value` como `Integer` con `@NotNull @Min(1) @Max(5)`; rechazar valores no enteros en el JSON, p. ej. con `JsonNode` o deserialización estricta, para que `3.5` dé 400) y `BE/history/dto/RatingResponse.java` (`orderId`, `value`, `createdAt`).
- [X] T018 [US2] Crear `BE/history/service/RatingService.java`: `rate(customerId, orderId, value)` busca el pedido con `OrderRepository.findByIdAndCustomerId` (si no existe → `OrderNotFoundException`), exige `DELIVERED` (si no → `RatingConflictException` `ORDER_NOT_DELIVERED`), inserta `Rating` con `saveAndFlush` y traduce `DataIntegrityViolationException` por la unicidad en `RatingConflictException` `RATING_ALREADY_EXISTS`. `@Transactional`; sin comprobar-y-escribir como única garantía (constitución IV).
- [X] T019 [US2] Añadir a `BE/history/controller/CustomerHistoryController.java` `POST /api/customer/orders/{id}/rating` → 201 con `RatingResponse`; sin rutas de edición ni borrado.
- [X] T020 [P] [US2] En `FE/features/history/history-api.ts` añadir `rateOrder(token, orderId, value)`; en `FE/features/history/history-logic.ts` (nuevo) `isValidRating(value)` (entero de 1 a 5) y `ratingErrorMessage(code)` para `ORDER_NOT_DELIVERED`, `RATING_ALREADY_EXISTS` y 404.
- [X] T021 [US2] Control de calificación (cinco botones 1–5, deshabilitado mientras envía, mensaje de error) en `FE/features/history/HistoryPage.tsx` y en `FE/features/checkout/OrderPage.tsx`, visible solo si el pedido está `DELIVERED` y `canRate`; tras calificar muestra el valor emitido. (Adaptación: el control es `RatingControl.tsx` y vive en `HistoryPage.tsx`, donde se conoce `canRate`; `OrderPage.tsx` enlaza al historial para calificar porque `OrderResponse` no trae la calificación y no se modifica el módulo `order`.)
- [X] T022 [P] [US2] `FE/features/history/history-logic.test.ts` (Vitest): `isValidRating` con 1, 5, 0, 6, 3.5, `NaN`; mensajes de error por código.

**Checkpoint**: US1 y US2 completas; RF-31 verificable (incluida la concurrencia).

---

## Phase 5: User Story 3 - El restaurante consulta su historial y sus calificaciones (Priority: P2)

**Goal**: el restaurante ve solo los pedidos que recibió, con calificación y solo el nombre del cliente.

**Independent Test**: `GET /api/restaurant/orders/history` devuelve solo los pedidos del restaurante propio, con `rating` y
`customerName`, sin dirección ni teléfono.

### Tests for User Story 3

- [X] T023 [US3] `BT/history/RestaurantHistoryIntegrationTest.java`: el restaurante A ve sus pedidos en todos los estados con calificación cuando existe y `rating` nulo cuando no; el restaurante B no ve ninguno de A; la respuesta contiene `customerName` y no contiene dirección, referencia ni teléfono del cliente; un restaurante inhabilitado para recibir pedidos (estado del establecimiento distinto de habilitado) sigue viendo su historial; 401; 403 con rol cliente y repartidor; cuenta sin establecimiento registrado → 403.

### Implementation for User Story 3

- [X] T024 [US3] En `BE/history/service/HistoryService.java` añadir `restaurantHistory(accountId, HistoryFilter)`: resolver el restaurante propio con `RestaurantRepository.findByOwnerId` sin comprobar si está habilitado para recibir pedidos (solo si no existe registro → `RestaurantNotEnabledException`), consulta `findForRestaurant`, ensamblado con `payment` y `customerName`, sin `restaurant` ni `canRate`.
- [X] T025 [US3] Crear `BE/history/controller/RestaurantHistoryController.java`: `GET /api/restaurant/orders/history` y comprobar en T023 que no choca con `RestaurantOrderController` `/{id}`.
- [X] T026 [US3] `FE/features/history/HistoryPage.tsx`: mostrar `customerName` y la calificación (o «Sin calificar») cuando el rol es RESTAURANTE.

**Checkpoint**: US3 funciona de forma independiente.

---

## Phase 6: User Story 4 - El repartidor consulta su historial y sus calificaciones (Priority: P2)

**Goal**: el repartidor ve sus entregas (Entregadas y en curso) con productos, dirección de entrega, costo de envío, la calificación del pedido y el nombre del cliente, sin comisión ni importes.

**Independent Test**: `GET /api/courier/deliveries/history` devuelve solo las entregas a su cargo en estado Asignado, Recogido o
Entregado.

### Tests for User Story 4

- [X] T027 [US4] `BT/history/CourierHistoryIntegrationTest.java` (usar `DeliveryTestSupport`): el repartidor A ve sus entregas Entregadas y las Asignadas o Recogidas en curso, con calificación del pedido cuando existe; el repartidor B no ve ninguna de A; una entrega que A rechazó o le fue liberada y reasignada NO aparece; la respuesta incluye `customerName`, `deliveryAddress` (calle, distrito, referencia), `lines` con solo nombre y cantidad y `deliveryFee`, y NO incluye `payment`, `totals`, `commission`, `subtotal`, precios unitarios, totales de línea ni teléfono del cliente; un repartidor con la disponibilidad apagada sigue viendo su historial; filtro `status=PENDING` → 400; 401; 403 con rol cliente y restaurante.

### Implementation for User Story 4

- [X] T028 [US4] En `BE/history/service/HistoryService.java` añadir `courierHistory(courierId, HistoryFilter)` con los estados `ASSIGNED`, `PICKED_UP`, `DELIVERED`; un `status` fuera de ese conjunto → 400 (validar antes de consultar); ensamblado sin `payment` ni `totals`, con `restaurant`, `customerName`, `deliveryAddress` (street, district, reference desde `Order`, nunca `deliveryPhone`), `deliveryFee`, y líneas reducidas a nombre y cantidad; no consultar la disponibilidad ni el estado del repartidor.
- [X] T029 [US4] Crear `BE/history/controller/CourierHistoryController.java`: `GET /api/courier/deliveries/history`.
- [X] T030 [US4] `FE/features/history/HistoryPage.tsx`: mostrar restaurante, `customerName`, dirección de entrega, productos con cantidad, costo de envío y calificación cuando el rol es REPARTIDOR; sin pago ni importes.

**Checkpoint**: los tres historiales funcionan; RF-30 cubierto.

---

## Phase 7: User Story 5 - Paginación, orden y filtros (Priority: P2)

**Goal**: cada historial se pagina del más reciente al más antiguo y se filtra por estado y fecha.

**Independent Test**: con más de 20 operaciones, recorrer las páginas sin repetir ni omitir; aplicar filtros de estado,
fechas y ambos; rango invertido → 400.

### Tests for User Story 5

- [X] T031 [US5] `BT/history/HistoryPagingFilterIntegrationTest.java` (parametrizada por rol cliente, restaurante y repartidor): 45 operaciones, páginas de 20 → 20/20/5 y cada id una sola vez en orden `created_at desc, id desc` (con `created_at` repetidos para probar el desempate); página fuera de rango → 200 y `items: []`; `size=0`, `size=101` y `page=-1` → 400; filtro por estado devuelve solo ese estado; filtro por rango incluye ambos extremos (pedido a las 00:00 y a las 23:59 del día en `America/Lima`); estado + rango combinados; `from` > `to` → 400 con mensaje claro.

### Implementation for User Story 5

- [X] T032 [P] [US5] `FE/features/history/history-logic.ts`: `buildQuery(filter)` (omite vacíos, formato `yyyy-MM-dd`), `validateRange(from, to)` (mensaje cuando `from` > `to`) y `mergePages(previous, next)` sin duplicar ids.
- [X] T033 [P] [US5] `FE/features/history/history-logic.test.ts` (ampliar): `buildQuery` con y sin filtros, `validateRange` con rango válido, invertido y vacío, `mergePages` con páginas solapadas.
- [X] T034 [US5] `FE/features/history/HistoryPage.tsx`: controles de filtro por estado (según el rol: el repartidor solo Asignado, Recogido y Entregado) y por fechas «desde» y «hasta»; aplicar un filtro reinicia a la página 0; el mensaje del rango invertido se muestra antes de llamar al servidor.

**Checkpoint**: orden, paginación y filtros verificados en los tres roles (SC-006, SC-007).

---

## Phase 8: User Story 6 - Datos del momento del pedido (Priority: P2)

**Goal**: el historial conserva nombres, precios e importes aunque cambie o se retire el producto.

**Independent Test**: tras cambiar el nombre y el precio de un producto pedido y retirarlo, los tres historiales siguen
mostrando los valores originales.

### Tests for User Story 6

- [X] T035 [US6] `BT/history/HistorySnapshotIntegrationTest.java`: confirmar un pedido con un producto, cambiar nombre y precio del producto, luego retirarlo del catálogo (`ProductRepository`/API existente); comprobar que cliente, restaurante y repartidor siguen viendo el nombre, el precio unitario y los importes originales y la línea del producto retirado (SC-003).

**Checkpoint**: sin implementación adicional prevista (los datos ya están fijados en `OrderLine`); si la prueba falla, corregir en `HistoryAssembler` y no en `order`.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: verificaciones y evidencia del incremento.

- [X] T036 [P] Prueba de rendimiento acotada `BT/history/HistoryPerformanceIntegrationTest.java`: con 1.000 pedidos de un cliente, la primera página responde en menos de 2 s (SC-001); registrar el tiempo en `validation.md`. No sustituye RNF-01.
- [X] T037 [P] Revisar `BE/common/security/SecurityConfig.java`: confirmar que las tres rutas quedan bajo `/api/customer/**`, `/api/restaurant/**` y `/api/courier/**` y que ninguna es pública; añadir los casos 401/403 faltantes a las pruebas de integración de T008, T023 y T027.
- [X] T038 Ejecutar `cd backend && ./mvnw verify` y `cd frontend && pnpm test && pnpm build`; confirmar compilación y que JaCoCo reporta ≥ 90 % de líneas de negocio del paquete `history` y el reporte de ramas; sin excluir reglas de negocio.
- [ ] T039 Recorrer los 6 escenarios de [quickstart.md](./quickstart.md) en local cronometrando que calificar un pedido entregado toma menos de 30 s (SC-004), y registrar el resultado y la trazabilidad de RF-30, RF-31, RF-04 y RNF-07 en `specs/010-historiales-calificaciones/validation.md`; dejar RNF-01 y E2E automatizadas como pendientes.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 2)**: sin dependencias; bloquea todas las historias. T009 (`Rating`, `RatingRepository`) va
  antes de T005, que lo usa.
- **US1 (P1)** y **US2 (P1)**: tras la fase 2; US2 reutiliza `Rating` (T009) y sigue a US1 en el orden.
- **US3, US4**: tras US1 (reutilizan `HistoryService` y `HistoryPage`).
- **US5**: tras US1, US3 y US4 (prueba los tres historiales).
- **US6**: tras US1, US3 y US4.
- **Polish**: tras todas las historias.

### Parallel Opportunities

- T001–T004, T006 y T009 en paralelo; T005 tras T009.
- T007 y T012 en paralelo con T008.
- T014, T017, T020 y T022 en paralelo dentro de US2.
- Con las historias US3 y US4 terminadas la parte de frontend (T026, T030) puede hacerla otra persona en paralelo.

## Implementation Strategy

### MVP primero (US1 + US2)

1. Fase 2 (con T009) → US1 → validar el historial del cliente.
2. US2 → validar la calificación única, incluida la concurrencia.
3. Detenerse y validar; luego US3, US4, US5 y US6 en ese orden.

### Entrega incremental

Cada historia añade valor sin romper las anteriores. US3 y US4 reutilizan el ensamblador y la página; US5 y US6 solo
añaden pruebas y controles de interfaz.

## Notes

- [P] = otros archivos, sin dependencias pendientes.
- No se hace commit salvo que el usuario lo pida.
- Un cambio de comportamiento en `order`, `delivery` o `payment` indica un error de diseño: este incremento solo los lee.
