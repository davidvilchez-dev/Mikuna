---

description: "Tareas de implementación: carrito, direcciones y cálculo de importes (RF-11, RF-12, RF-13, RF-04 parcial)"
---

# Tasks: Carrito, direcciones y cálculo de importes

**Input**: Documentos de diseño en `specs/005-carrito-direcciones-importes/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/cart-api.md](./contracts/cart-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra
PostgreSQL local (`mikuna_test_db`) y ≥ 90 % de líneas de lógica de negocio. Vitest para la lógica
del frontend. No se usa Playwright ni otra herramienta E2E de navegador.

**Organization**: Tareas agrupadas por historia de usuario. Las tres historias son P1; el orden
US1 (direcciones) → US2 (carrito) → US3 (importes) sigue las dependencias reales: el carrito usa la
dirección seleccionada y los importes usan el carrito.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3 según [spec.md](./spec.md). Mapeo: US1 = direcciones (spec US2),
  US2 = carrito (spec US1), US3 = importes (spec US3).
- Paquete base del backend: `backend/src/main/java/com/mikuna/backend/` (abreviado `BE/`).
  Pruebas: `backend/src/test/java/com/mikuna/backend/` (abreviado `BT/`).
  Frontend: `frontend/src/` (abreviado `FE/`).

---

## Phase 1: Setup (configuración compartida)

**Purpose**: Solo configuración; no se añaden dependencias.

- [X] T001 Configuración de negocio sin valores por defecto (research §7). En `backend/src/main/resources/application.properties` añadir `mikuna.delivery.districts=${MIKUNA_DELIVERY_DISTRICTS}`, `mikuna.delivery.fee=${MIKUNA_DELIVERY_FEE}` y `mikuna.delivery.commission-rate=${MIKUNA_COMMISSION_RATE}` (**sin** valor por defecto: la constitución prohíbe inventarlos). En `backend/.env.example` añadir las tres variables `MIKUNA_DELIVERY_DISTRICTS`, `MIKUNA_DELIVERY_FEE` y `MIKUNA_COMMISSION_RATE` **sin valores** y con un comentario que indique el formato (distritos separados por comas, soles con punto decimal, porcentaje de 0 a 100). En `backend/src/test/resources/application.properties` añadir valores de prueba fijos: `mikuna.delivery.districts=Ayacucho,San Juan Bautista,Carmen Alto`, `mikuna.delivery.fee=5.00`, `mikuna.delivery.commission-rate=5`. No abrir ni copiar `backend/.env`.
- [X] T002 [P] En `frontend/vite.config.ts` ampliar `test.coverage.include` con `src/features/cart/cart-validation.ts` (mantener `thresholds.lines: 90`).

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: Seguridad por rol, configuración validada, errores y soporte de pruebas que usan las tres historias.

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta terminar esta fase.

- [X] T003 En `BE/common/security/SecurityConfig.java` añadir, junto a las reglas por rol, `.requestMatchers("/api/customer/**").hasRole("CUSTOMER")` antes de `anyRequest().authenticated()`. Sin rutas públicas nuevas. El estado de la cuenta lo sigue comprobando `AccountStatusFilter`.
- [X] T004 [P] Crear `BE/cart/service/DeliveryProperties.java` (`@ConfigurationProperties("mikuna.delivery")`, record con `List<String> districts`, `BigDecimal fee` y `BigDecimal commissionRate`, con `@Validated`): `districts` no vacío y con elementos no vacíos; `fee` ≥ 0 con a lo sumo 2 decimales; `commissionRate` entre 0 y 100. Registrarlo con `@EnableConfigurationProperties` en `BackendApplication` o en una clase de configuración del módulo. Si falta o es inválido, el arranque falla con un mensaje claro. `BT/cart/service/DeliveryPropertiesTest.java`: valores válidos se aceptan; lista vacía, tarifa negativa, comisión > 100 y comisión negativa se rechazan.
- [X] T005 [P] En `BE/common/exception/` crear `AddressNotFoundException` (→ 404, «Dirección no encontrada»), `OutsideDeliveryZoneException` (→ 400 con `errors.district` = «Esa dirección está fuera de la zona de reparto»), `CartConflictException` (→ 409, con `code`: `CART_OTHER_RESTAURANT`, `PRODUCT_NOT_ORDERABLE` o `CART_LIMIT`, y mensaje en español) y `CartLineNotFoundException` (→ 404, «Línea no encontrada»). Registrarlas en `GlobalExceptionHandler.java` con el mismo `ProblemDetail` que las de 004 y sin datos de otros clientes. Las 409 (`CartConflictException` y `AddressLimitException`) deben incluir la propiedad `code` en el `ProblemDetail` (`ADDRESS_LIMIT` para la de direcciones), porque la interfaz distingue por ella `CART_OTHER_RESTAURANT` (T030).
- [X] T006 Actualizar `BT/support/IntegrationTestSupport.java`: en `cleanDatabase()` borrar `delete from lineas_carrito`, `delete from carritos` y `delete from direcciones` **antes** de `productos`. Añadir `protected Account customer(String email)` (`registrationService.registerPublic("Cliente", email, PASSWORD, PASSWORD, Role.CUSTOMER)`). Crear `BT/support/TestCarts.java` con `address(customerId, street, district)` que inserta una dirección con `jdbc` y devuelve su id. Reutilizar `TestProducts` de 004 (`approvedRestaurant`, `product`).

**Checkpoint**: la aplicación compila, las pruebas de 002–004 siguen verdes y la ruta `/api/customer/**` rechaza lo que no es `CUSTOMER`.

---

## Phase 3: User Story 1 - Registrar y seleccionar direcciones de entrega (Priority: P1) 🎯 MVP

**Goal**: El cliente administra su libreta de direcciones dentro de la zona cubierta (RF-12). La
selección para el pedido se completa en US2, que introduce el carrito.

**Independent Test**: Registrar direcciones dentro y fuera de la zona, editarlas y eliminarlas, y comprobar que otro cliente no las ve.

### Tests de US1 (escribirlas primero; deben fallar)

- [X] T007 [P] [US1] `BT/address/service/DeliveryZoneTest.java`: un distrito de la lista es válido; coincide sin distinguir mayúsculas, tildes ni espacios sobrantes; uno ausente, vacío o `null` no lo es.
- [X] T008 [P] [US1] `BT/address/service/AddressServiceTest.java` (Mockito): registra y guarda recortando campos; rechaza fuera de zona sin guardar; rechaza la undécima dirección (límite 10); editar hacia un distrito fuera de zona se rechaza sin modificar; editar y eliminar una dirección ajena o inexistente → `AddressNotFoundException`.
- [X] T009 [P] [US1] `BT/address/AddressIntegrationTest.java` (`extends IntegrationTestSupport`): alta válida 201; sin sesión 401; rol RESTAURANT/ADMIN/COURIER 403; cuenta inhabilitada 403; campos vacíos, `street` de menos de 5 o más de 150 caracteres, teléfono inválido y distrito fuera de zona 400 con `errors`; campo desconocido (`customerId`) 400; listar solo las propias; `PUT` y `DELETE` sobre la dirección de otro cliente → 404 sin cambios; undécima dirección → 409.
- [X] T010 [P] [US1] `FE/features/cart/cart-validation.test.ts`: dirección válida; `street` < 5 o > 150, `district` vacío o > 100, `label` > 50, `reference` > 200 y teléfono con letras o de menos de 7 o más de 15 dígitos devuelven un mensaje por campo; y cantidad (entero 1–20).

### Implementación de US1

- [X] T011 [US1] Crear `BE/address/model/Address.java` (`@Entity`, tabla `direcciones`) con los campos y restricciones de [data-model.md](./data-model.md): `id` `UUID` PK generado; `customerId` `UUID` NOT NULL (`customer_id`, columna simple, del token); `label` `varchar(50)` NULL, ≤ 50; `street` `varchar(150)` NOT NULL, recortado, 5–150; `district` `varchar(100)` NOT NULL, 2–100; `reference` `varchar(200)` NULL, ≤ 200; `contactPhone` `varchar(20)` NULL (`contact_phone`), 7–15 dígitos si se envía; `createdAt` NOT NULL (`created_at`, `@PrePersist`); `updatedAt` NOT NULL (`updated_at`, `@PreUpdate`). `@Table(indexes = @Index(name = "idx_direcciones_cliente", columnList = "customer_id"))`. Constructor protegido; fábrica `Address.create(customerId, label, street, district, reference, contactPhone, now)` y `update(...)` que recortan; sin setter de `customerId`.
- [X] T012 [P] [US1] Crear `BE/address/repository/AddressRepository.java` (depende de T011): `Optional<Address> findByIdAndCustomerId(UUID id, UUID customerId)`, `List<Address> findByCustomerIdOrderByCreatedAtAsc(UUID customerId)` y `long countByCustomerId(UUID customerId)`.
- [X] T013 [P] [US1] Crear `BE/address/dto/AddressRequest.java` (record con Bean Validation y los mismos mensajes en español de las specs 002–004: `label` `@Size(max = 50)`, `street` `@NotBlank @Size(min = 5, max = 150)`, `district` `@NotBlank @Size(min = 2, max = 100)`, `reference` `@Size(max = 200)`, `contactPhone` `@Pattern` para 7–15 dígitos, opcional) y `BE/address/dto/AddressResponse.java` (`id`, `label`, `street`, `district`, `reference`, `contactPhone`, `selected`, `createdAt`, `updatedAt`).
- [X] T014 [US1] Crear `BE/address/service/DeliveryZone.java` (`@Component`, depende de T004): `boolean covers(String district)` que normaliza (NFD, sin marcas diacríticas, `toLowerCase(Locale.ROOT)`, recortado, espacios internos colapsados) tanto la lista configurada como el distrito recibido; `null` o vacío → `false`.
- [X] T015 [US1] Crear `BE/address/service/AddressService.java` (depende de T012–T014): `create`, `list`, `update` y `delete` por `customerId` del token. Rechazar `district` fuera de zona con `OutsideDeliveryZoneException` al crear y editar; máximo 10 por cliente (409 con una excepción propia `AddressLimitException` en `BE/common/exception/`, mensaje «Alcanzaste el máximo de 10 direcciones», registrada en `GlobalExceptionHandler`); recurso ajeno → `AddressNotFoundException`. La limpieza de la dirección seleccionada al eliminar se añade en US2 (T024), cuando existe el carrito. `@Transactional` en las escrituras. `selected` se rellena en US2 (T025); en US1 siempre `false`.
- [X] T016 [US1] Crear `BE/address/controller/AddressController.java` (`/api/customer/addresses`, `@AuthenticationPrincipal AccountPrincipal`): `POST` (201), `GET`, `PUT /{id}` y `DELETE /{id}` (204) según [cart-api.md](./contracts/cart-api.md). Sin `customerId` en cuerpo ni ruta; JSON con `@Valid`.
- [X] T017 [P] [US1] Crear `FE/features/cart/cart-validation.ts` con `validateAddress(data)` y `validateQuantity(value)` (reglas de T010, mensajes en español) y completar `FE/features/cart/cart-api.ts` con el cliente de direcciones: `listAddresses`, `createAddress`, `updateAddress`, `deleteAddress` usando `apiRequest` con el token y tipos `Address` y `AddressData`.
- [X] T018 [P] [US1] Crear `FE/features/cart/AddressForm.tsx`, `FE/features/cart/AddressesPage.tsx` y `FE/features/cart/cart.css`: libreta con alta, edición y eliminación (con confirmación), errores por campo (incluido el distrito fuera de zona devuelto por el backend), estado vacío y de carga. Solo visible para la sesión de cliente; sin sesión o con otro rol, redirigir a `/iniciar-sesion` o mostrar aviso, como `ProductsPage`.
- [X] T019 [US1] En `FE/App.tsx` añadir la ruta `/mis-direcciones` y en `FE/features/home/components/SiteHeader.tsx` el enlace «Mis direcciones» cuando hay sesión de cliente.

**Checkpoint**: T007–T010 pasan; la libreta funciona de extremo a extremo.

---

## Phase 4: User Story 2 - Armar el carrito (Priority: P1)

**Goal**: El cliente agrega, modifica y elimina productos de un solo restaurante y selecciona su dirección de entrega (RF-11, RF-12).

**Independent Test**: Con un cliente y restaurantes con productos, agregar, cambiar cantidad, eliminar, vaciar, intentar mezclar restaurantes y seleccionar una dirección; comprobar el estado persistido tras reiniciar.

### Tests de US2 (escribirlas primero; deben fallar)

- [X] T020 [P] [US2] `BT/cart/service/CartServiceTest.java` (Mockito + `OrderEligibility` simulado): agregar crea el carrito perezosamente y fija el restaurante; agregar de nuevo suma; cantidad 0, negativa o > 20 se rechaza sin modificar; suma que supere 20 → 409 `CART_LIMIT`; línea 31 → 409 `CART_LIMIT`; producto de otro restaurante → 409 `CART_OTHER_RESTAURANT` sin cambios; producto no disponible, retirado o de restaurante que no puede recibir pedidos → `PRODUCT_NOT_ORDERABLE`/404 sin cambios; cambiar cantidad y eliminar línea ajena o inexistente → `CartLineNotFoundException`; eliminar la última línea deja `restaurantId = null`; vaciar conserva la dirección; seleccionar dirección ajena → `AddressNotFoundException`, fuera de zona → `OutsideDeliveryZoneException`.
- [X] T021 [P] [US2] `BT/cart/CartIntegrationTest.java` (`extends IntegrationTestSupport`): flujo completo contra PostgreSQL (agregar, sumar, cambiar, eliminar, vaciar) con el estado persistido; 401 sin sesión, 403 con otro rol y con cuenta inhabilitada; otro restaurante → 409 y tras `DELETE /api/customer/cart` se acepta; producto no disponible, retirado y restaurante pausado, fuera de horario, pendiente o con cuenta inhabilitada → rechazados sin cambios; línea de otro cliente → 404; campo desconocido (`customerId`, `price`, `restaurantId`) → 400; seleccionar dirección propia 200 y ajena 404; eliminar la dirección seleccionada deja el carrito sin dirección.
- [X] T022 [P] [US2] `BT/cart/CartConcurrencyIntegrationTest.java`: N altas simultáneas (hilos) del mismo producto en un mismo carrito suman la cantidad esperada (sin pérdida ni duplicado, respetando el máximo de 20: las que excedan responden 409); altas simultáneas de productos distintos del mismo restaurante generan una línea cada una; dos altas simultáneas con productos de dos restaurantes distintos en un carrito vacío: solo prospera una y no quedan restaurantes mezclados; primera creación simultánea del carrito de un cliente produce un único carrito.

### Implementación de US2

- [X] T023 [US2] Crear `BE/cart/model/Cart.java` (`@Entity`, tabla `carritos`) y `BE/cart/model/CartLine.java` (tabla `lineas_carrito`) según [data-model.md](./data-model.md). `Cart`: `id` `UUID` PK generado; `customerId` `UUID` NOT NULL (`customer_id`) con `UNIQUE` (`uk_carritos_cliente`); `restaurantId` `UUID` NULL (`restaurant_id`); `selectedAddressId` `UUID` NULL (`selected_address_id`); `createdAt` y `updatedAt` NOT NULL (`@PrePersist`/`@PreUpdate`); `@OneToMany(mappedBy = "cart", cascade = ALL, orphanRemoval = true) @OrderBy("addedAt")` líneas. `CartLine`: `id` `UUID`; `@ManyToOne(fetch = LAZY) cart` NOT NULL (`cart_id`); `productId` `UUID` NOT NULL (`product_id`); `quantity` `int` NOT NULL, 1–20; `addedAt` NOT NULL (`added_at`); `@Table(uniqueConstraints = @UniqueConstraint(name = "uk_lineas_carrito_producto", columnNames = {"cart_id", "product_id"}))`. Métodos de dominio en `Cart`: `addOrIncrease(productId, quantity, now)`, `changeQuantity`, `removeLine`, `clear()` (deja `restaurantId = null`, sin tocar `selectedAddressId`), `selectAddress(id)` y `clearAddress()`; sin setter de `customerId`.
- [X] T024 [US2] Crear `BE/cart/repository/CartRepository.java` (depende de T023) con `Optional<Cart> findByCustomerId(UUID customerId)`, `@Lock(PESSIMISTIC_WRITE) @Query("select c from Cart c where c.customerId = :customerId") Optional<Cart> lockByCustomerId(UUID customerId)` (research §2) y `@Modifying @Query("update Cart c set c.selectedAddressId = null where c.customerId = :customerId and c.selectedAddressId = :addressId") int clearSelection(UUID customerId, UUID addressId)`; completar en `BE/address/service/AddressService.java` (T015) la llamada a `clearSelection` al eliminar. Crear `BE/cart/repository/CartLineRepository.java` solo si hace falta (preferir operar por el agregado `Cart`).
- [X] T025 [P] [US2] Crear los DTO de `BE/cart/dto/`: `AddLineRequest(productId @NotNull, quantity @NotNull @Min(1) @Max(20))`, `QuantityRequest(quantity @NotNull @Min(1) @Max(20))` (mensajes: «La cantidad debe ser un entero entre 1 y 20»), `SelectAddressRequest(addressId @NotNull)`, `CartLineResponse`, `CartRestaurantResponse(id, name, attention)`, `CartAddressResponse(id, street, district)` y `CartResponse(restaurant, lines, address, totals, notices)` según [cart-api.md](./contracts/cart-api.md). `totals` se rellena en US3; en esta fase puede enviarse `null`. Actualizar `AddressService.list` para marcar `selected` según el carrito.
- [X] T026 [US2] Crear `BE/cart/service/CartService.java` (depende de T023–T025, T014 y `OrderEligibility`/`ProductRepository`/`RestaurantRepository`): `@Transactional` en cada mutación, con `loadOrCreateLocked(customerId)` (primero `lockByCustomerId`; si no existe, crear y, ante `DataIntegrityViolationException` por `uk_carritos_cliente`, releer con bloqueo). Operaciones: `get`, `addLine`, `changeQuantity`, `removeLine`, `clear` y `selectAddress`. Reglas de `addLine`: producto con `findById` y `active == true` (si no, `ProductNotFoundException`); restaurante con `findVisibleById` (si no existe o no es visible → `CartConflictException(PRODUCT_NOT_ORDERABLE)`); `available == true` y `OrderEligibility.canReceiveOrders(restaurante, true)` (si no, `PRODUCT_NOT_ORDERABLE`, mensaje que indica si es el producto o el restaurante); `restaurantId` del carrito distinto y con líneas → `CART_OTHER_RESTAURANT`; cantidad acumulada ≤ 20 y ≤ 30 líneas, si no `CART_LIMIT`. Eliminar la última línea o vaciar deja `restaurantId = null`. `selectAddress` exige dirección propia (`AddressRepository.findByIdAndCustomerId`) y `DeliveryZone.covers`. `get` construye `CartResponse`: por línea lee el producto vigente (precio, nombre, imagen), evalúa elegibilidad (`available`/`reason`: `PRODUCT_UNAVAILABLE`, `PRODUCT_WITHDRAWN`, `RESTAURANT_UNAVAILABLE`), revalida la dirección seleccionada (si ya no está en zona o no existe, la limpia y añade un aviso) y agrega `notices`. Usar consultas por lote (`findAllById`) para no hacer N+1.
- [X] T027 [US2] Crear `BE/cart/controller/CartController.java` (`/api/customer/cart`): `GET`, `POST /lines` (200), `PUT /lines/{lineId}`, `DELETE /lines/{lineId}`, `DELETE` (vaciar) y `PUT /address`, todas con `@AuthenticationPrincipal AccountPrincipal` y devolviendo `CartResponse`.
- [X] T028 [P] [US2] Ampliar `FE/features/cart/cart-api.ts` con `getCart`, `addToCart`, `changeQuantity`, `removeLine`, `clearCart` y `selectAddress` y los tipos de [cart-api.md](./contracts/cart-api.md). Ajustar `apiRequest` en `FE/features/auth/auth-api.ts` para que el error exponga el `code` del `ProblemDetail` (necesario para distinguir `CART_OTHER_RESTAURANT`), sin romper su uso actual.
- [X] T029 [US2] Crear `FE/features/cart/CartPage.tsx` (ruta `/carrito`): lista de líneas con imagen, nombre, precio, cantidad editable (validada con `validateQuantity`), eliminar, vaciar, líneas no disponibles resaltadas con su motivo, avisos (`notices`) y selector de dirección entre las propias con enlace a `/mis-direcciones` si no hay ninguna. Estados vacío, de carga y de error; sin presentar como implementados la confirmación ni el pago (solo un aviso de que llegan más adelante).
- [X] T030 [US2] En `FE/features/catalog/RestaurantCatalogPage.tsx` añadir «Agregar al carrito» por producto (cantidad 1) para la sesión de cliente: sin sesión lleva a `/iniciar-sesion`; con otro rol no se muestra; ante `CART_OTHER_RESTAURANT` ofrece «Vaciar carrito y agregar» (usa `clearCart` y reintenta) y ante `PRODUCT_NOT_ORDERABLE` muestra el mensaje. Con el restaurante sin atención se deshabilita el botón con el aviso existente. En `FE/App.tsx` añadir la ruta `/carrito` y en `SiteHeader.tsx` el enlace «Carrito» para el cliente.

**Checkpoint**: T020–T022 pasan; se puede armar el carrito y elegir dirección de extremo a extremo.

---

## Phase 5: User Story 3 - Ver los importes calculados (Priority: P1)

**Goal**: Subtotal, envío, comisión y total calculados en el servidor y recalculados en cada cambio (RF-13).

**Independent Test**: Con un carrito y una dirección seleccionada, comprobar los cuatro importes y que se recalculan al cambiar cantidades, productos y dirección.

### Tests de US3 (escribirlas primero; deben fallar)

- [X] T031 [P] [US3] `BT/cart/service/CartPricingTest.java` (unitaria, sin Spring): subtotal = Σ precio × cantidad solo de líneas elegibles; envío = tarifa fija con dirección válida; comisión = `subtotal × tasa / 100` con `HALF_UP` a 2 decimales (casos de redondeo: `0.005 → 0.01`, `33.33 × 5 % = 1.67`, `10.10 × 5 % = 0.51`); total = subtotal + envío + comisión; sin dirección → envío, comisión y total `null` y `pending = "ADDRESS"`; sin líneas elegibles → todo `0.00` y `pending = "EMPTY"`; línea no elegible no suma; tasa 0 y tarifa 0 válidas; el total siempre coincide con la suma de sus componentes; la comisión se redondea una sola vez (no por línea) y el total no recalcula el redondeo; precios de dos decimales × cantidades 1–20 son exactos (p. ej. `0.10 × 3 = 0.30`, sin errores de coma flotante); escala siempre 2 (`5` → `5.00`) en subtotal, envío, comisión, total y `lineTotal`.
- [X] T032 [P] [US3] `BT/cart/CartTotalsIntegrationTest.java` (`extends IntegrationTestSupport`): con dos productos y dirección seleccionada, los importes esperados con los valores de prueba (tarifa 5.00, comisión 5 %); recálculo tras agregar, cambiar cantidad, eliminar y cambiar de dirección; sin dirección solo subtotal y `pending = "ADDRESS"`; carrito vacío `0.00`; cambio de precio del producto reflejado en los importes; producto marcado no disponible o restaurante pausado sale como no disponible y no suma; dirección que deja de estar en zona (cambiando la configuración de prueba con `@TestPropertySource` o dando de baja la dirección por `jdbc` con otro distrito) se retira de la selección con aviso; el cliente no puede forzar importes: `total`, `subtotal`, `deliveryFee`, `commission`, `price`, `unitPrice` y `lineTotal` en cualquier cuerpo de `POST/PUT` del carrito y de direcciones → 400 sin cambios; el JSON serializa los importes con dos decimales (`5.00`, no `5` ni `5.0`) y `total` = `subtotal` + `deliveryFee` + `commission` al céntimo en todos los casos probados.

### Implementación de US3

- [X] T033 [P] [US3] Crear `BE/cart/dto/TotalsResponse.java` (`subtotal`, `deliveryFee`, `commission`, `total` como `BigDecimal` con dos decimales, y `pending` `String` nulo, `ADDRESS` o `EMPTY`) y `BE/cart/service/CartPricing.java` (`@Component`, depende de `DeliveryProperties`): `TotalsResponse compute(List<BigDecimal> eligibleLineTotals, boolean hasValidAddress)` con las reglas de research §3, `BigDecimal` con `setScale(2, RoundingMode.HALF_UP)`, sin dependencias de HTTP ni de repositorios, sin `double`/`float` y con la regla de FR-010 (comisión redondeada una sola vez; total = suma de componentes redondeados). Los importes salen como número JSON con escala 2 (`setScale(2)` en el constructor del record), verificado en T032.
- [X] T034 [US3] Integrar `CartPricing` en `CartService.get` y en el `CartResponse` de cada mutación (T026): cada respuesta devuelve los importes recalculados con los precios vigentes; `lineTotal` por línea elegible y `null` para las no elegibles; no se persisten precios, así que no hay aviso de «precio cambiado»: el carrito siempre muestra el precio vigente (spec, contrato y quickstart ya lo reflejan).
- [X] T035 [P] [US3] En `FE/features/cart/CartPage.tsx` mostrar el resumen: subtotal, envío, comisión y total en soles con dos decimales (`Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })`), o el aviso «Selecciona una dirección para calcular el envío y el total» cuando `pending = "ADDRESS"`; recalcular tras cada acción usando la respuesta del backend (nunca calcular importes en el navegador). Estilos en `FE/features/cart/cart.css`.

**Checkpoint**: T031–T032 pasan; el resumen coincide con el cálculo esperado en cada cambio.

---

## Phase 6: Polish y verificación

**Purpose**: Evidencia de aceptación (constitución V y VI); sin ampliar el alcance.

- [X] T036 Ejecutar `cd backend ; ./mvnw verify` y confirmar que todas las pruebas (002–005) pasan y que el informe JaCoCo muestra ≥ 90 % de líneas de la lógica de negocio de `address` y `cart` (reglas, zona, importes), sin excluir reglas para alcanzar el umbral. Ejecutar `cd frontend ; pnpm test:coverage ; pnpm build ; pnpm lint`.
- [X] T037 Revisión de seguridad de RNF-07 sobre las rutas nuevas: matriz de 401 (sin/invalid token), 403 (rol y cuenta inhabilitada) y 404 (recurso ajeno) cubierta por T009 y T021; confirmar que no hay ids de cliente, restaurante ni importes en ningún cuerpo de solicitud.
- [ ] T038 Recorrido manual (incluye medir SC-006: las operaciones del carrito responden en menos de 2 s con datos locales) de [quickstart.md](./quickstart.md) (con los valores de negocio indicados por el usuario en `backend/.env`) y registro de resultados en `specs/005-carrito-direcciones-importes/validation.md` con el formato de `specs/004-catalogo-consulta/validation.md`: requisitos RF-04/RF-11/RF-12/RF-13 en estado verificado o parcial, cobertura medida, y los aplazamientos (E2E, RNF-01, RNF-03, HTTPS, CI/CD) como pendientes sin declararlos cumplidos.
- [X] T039 (sin README ni documento de arranque que describa `backend/.env`; las variables quedan en `backend/.env.example`) Actualizar la documentación de arranque en `docs/` (o el README existente que ya describa las variables de `backend/.env`) con las tres variables `MIKUNA_DELIVERY_*`; no incluir valores reales.

---

## Dependencies & Execution Order

- **Setup (T001–T002)** → **Foundational (T003–T006)** → historias → **Polish (T036–T039)**.
- **US1** (direcciones) depende solo de Foundational. **US2** (carrito) depende de US1 (`DeliveryZone`, `AddressRepository`) y completa `selected` y la limpieza de la selección. **US3** (importes) depende de US2.
- Dentro de cada historia: pruebas (fallan) → modelo → repositorio/DTO → servicio → controlador → frontend.

### Oportunidades de paralelismo

- Setup: T001 ‖ T002.
- Foundational: T004 ‖ T005 (T003 y T006 en secuencia por tocar archivos compartidos).
- US1: pruebas T007–T010 en paralelo; luego T012 ‖ T013 ‖ T017 ‖ T018 una vez listo T011.
- US2: pruebas T020–T022 en paralelo; T025 ‖ T028 tras T023.
- US3: T031 ‖ T032; T033 ‖ T035.

## Implementation Strategy

1. **MVP**: Setup + Foundational + US1 (libreta de direcciones funcionando y probada).
2. **Incremento 2**: US2 (carrito con un solo restaurante y dirección seleccionada).
3. **Incremento 3**: US3 (importes). Con las tres historias el incremento 005 queda completo.
4. Parar tras cada checkpoint, validar y pasar al siguiente. No se avanza a US2 sin que US1 esté verde.
5. **Antes de ejecutar la aplicación** el usuario debe indicar la lista de distritos, la tarifa de envío y el porcentaje de comisión (T001).
