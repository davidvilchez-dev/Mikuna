---

description: "Tareas de implementación: confirmación del pedido y pago con Stripe en pruebas (RF-14, RF-15, RF-16, RF-17, RF-04)"
---

# Tasks: Confirmación del pedido y procesamiento del pago

**Input**: Documentos de diseño en `specs/006-confirmacion-pedido-pago/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/checkout-api.md](./contracts/checkout-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra PostgreSQL
local (`mikuna_test_db`) con `StripeGateway` **simulado** (nunca Stripe real) y ≥ 90 % de líneas de
lógica de negocio. Vitest para la lógica del frontend. No se usa Playwright ni otra herramienta E2E.

**Organization**: Tareas agrupadas por historia de usuario de la spec. El orden sigue las dependencias
reales: US1 (confirmar y reservar) es el MVP; US2 (revalidar), US3 (fallos e idempotencia), US4
(capturar y liberar) y US5 (registro y consulta) se apoyan en ella.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1–US5 según [spec.md](./spec.md)
- Paquete base del backend: `backend/src/main/java/com/mikuna/backend/` (abreviado `BE/`).
  Pruebas: `backend/src/test/java/com/mikuna/backend/` (abreviado `BT/`).
  Frontend: `frontend/src/` (abreviado `FE/`).
- Nunca abrir, copiar ni imprimir `backend/.env`. Las claves reales de Stripe solo viven ahí.

---

## Phase 1: Setup (configuración compartida)

**Purpose**: Configuración y dependencias del frontend.

- [X] T001 Configuración de Stripe y moneda. En `backend/src/main/resources/application.properties` añadir `stripe.secret-key=${STRIPE_SECRET_KEY}`, `stripe.publishable-key=${STRIPE_PUBLISHABLE_KEY}` y `mikuna.payments.currency=${MIKUNA_PAYMENTS_CURRENCY:pen}` (sin valor por defecto para las claves). En `backend/.env.example` añadir `MIKUNA_PAYMENTS_CURRENCY=pen` como comentario opcional (las dos claves de Stripe ya existen sin valor). En `backend/src/test/resources/application.properties` añadir claves **ficticias** `stripe.secret-key=sk_test_ficticia_solo_pruebas`, `stripe.publishable-key=pk_test_ficticia_solo_pruebas` y `mikuna.payments.currency=pen`.
- [X] T002 [P] En `frontend/` ejecutar `pnpm add @stripe/stripe-js@10.3.0 @stripe/react-stripe-js@7.1.0` (research §10) y confirmar que `package.json` y el lockfile quedan actualizados.
- [X] T003 [P] En `frontend/vite.config.ts` ampliar `test.coverage.include` con `src/features/checkout/checkout-validation.ts` (mantener `thresholds.lines: 90`).

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: Configuración validada, pasarela aislada, errores, entidades y soporte de pruebas que usan todas las historias.

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta terminar esta fase.

- [X] T004 [P] Crear `BE/payment/service/StripeProperties.java` (`@ConfigurationProperties`, `@Validated`, registrado con `@EnableConfigurationProperties`): `secretKey` debe empezar con `sk_test_`, `publishableKey` con `pk_test_` y `currency` ser un código de 3 letras minúsculas; si no, el arranque falla con un mensaje claro que **no** incluye la clave. Sobrescribir `toString` para no exponerlas. `BT/payment/service/StripePropertiesTest.java`: claves de prueba se aceptan; `sk_live_`, vacía, `pk_live_` y moneda inválida se rechazan sin que el mensaje contenga la clave.
- [X] T005 [P] En `BE/common/exception/` crear `PaymentDeclinedException` (→ 402, con `code` `PAYMENT_DECLINED` o `PAYMENT_ACTION_REQUIRED`), `PaymentUnavailableException` (→ 503, `code=PAYMENT_UNAVAILABLE`, «No pudimos procesar el pago ahora; no se hizo ningún cobro, intenta de nuevo»), `OrderConflictException` (→ 409 con `code`: `CART_EMPTY`, `NO_ADDRESS`, `NOT_ORDERABLE`, `AMOUNTS_CHANGED`; opcionalmente lleva el `CheckoutResponse` vigente), `PaymentStateException` (→ 409, «El pago ya no está disponible para esa operación») y `OrderNotFoundException` (→ 404, «Pedido no encontrado»). Registrarlas en `GlobalExceptionHandler.java` con `ProblemDetail` y la propiedad `code`; ningún mensaje incluye datos de tarjeta ni detalles internos de Stripe. No se define `CONFIRMATION_IN_PROGRESS`: las confirmaciones simultáneas se serializan por el bloqueo del carrito.
- [X] T006 [P] Crear los modelos de `BE/payment/model/`: `PaymentStatus` (`AUTHORIZED`, `CAPTURED`, `RELEASED`), `TransactionType` (`AUTHORIZATION`, `CAPTURE`, `RELEASE`), `TransactionStatus` (`AUTHORIZED`, `CAPTURED`, `RELEASED`, `FAILED`), `Payment` (tabla `pagos`: `id` UUID, `order_id` UUID **UNIQUE**, `customer_id` UUID, `status` enum, `amount` NUMERIC(10,2), `currency` String(3), `gateway_ref` String, `created_at`, `updated_at`) y `PaymentTransaction` (tabla `transacciones_pago`: `id` UUID, `order_id` UUID nulo, `customer_id` UUID, `confirmation_key` String(64) nulo, `type`, `status`, `amount` NUMERIC(10,2), `result_code` String nulo, `result_message` String nulo, `gateway_ref` String nulo, `created_at`; índices por `order_id` y por `(customer_id, confirmation_key)`). Las transiciones permitidas de `Payment` son solo `AUTHORIZED→CAPTURED` y `AUTHORIZED→RELEASED`; el método de transición lanza `PaymentStateException` en cualquier otro caso. Sin estado de reembolso.
- [X] T007 [P] Crear los modelos de `BE/order/model/`: `OrderStatus` (solo `PENDING`), `PaymentMethod` (solo `CARD`), `Order` (tabla `pedidos`: `id` UUID generado en el servidor, `customer_id` UUID, `restaurant_id` UUID, `status` enum, `confirmation_key` String(64) con **UNIQUE (customer_id, confirmation_key)**, `payment_method` enum, `subtotal`, `delivery_fee`, `commission`, `total` NUMERIC(10,2), `restaurant_name` String, `delivery_label`, `delivery_street`, `delivery_district`, `delivery_reference`, `delivery_phone` String, `created_at` Instant; índice por `(customer_id, created_at)`) y `OrderLine` (tabla `lineas_pedido`: `id` UUID, `order_id` UUID FK con borrado en cascada, `product_id` UUID, `product_name` String, `unit_price` NUMERIC(10,2), `quantity` int 1–20, `line_total` NUMERIC(10,2) = `unit_price × quantity` exacto). Sin setters públicos de importes: se fijan en el constructor.
- [X] T008 [P] Crear `BE/payment/repository/PaymentRepository.java` (con `findByOrderIdForUpdate` usando `@Lock(PESSIMISTIC_WRITE)`) y `PaymentTransactionRepository.java`; `BE/order/repository/OrderRepository.java` (`findByIdAndCustomerId`, `findByCustomerIdAndConfirmationKey`).
- [X] T009 Crear `BE/payment/service/StripeGateway.java` (`@Component`, `RestClient` sobre `https://api.stripe.com/v1`, autenticación con la clave secreta, **sin** registrarla en logs, tiempos de conexión y lectura acotados, patrón de `CloudinaryImageStorage` con constructor que acepta `RestClient.Builder` para pruebas). Operaciones: `authorize(amountCents, currency, paymentMethodId, idempotencyKey, metadata)` (`POST /payment_intents` con `capture_method=manual`, `confirm=true`, `automatic_payment_methods[enabled]=true`, `automatic_payment_methods[allow_redirects]=never`) que devuelve un resultado con `intentId` y estado (`REQUIRES_CAPTURE`, `REQUIRES_ACTION`, `DECLINED`); `capture(intentId, key)` (`/capture`) y `cancel(intentId, key)` (`/cancel`). Todas con encabezado `Idempotency-Key`. Un 402/`card_error` se traduce a resultado `DECLINED` (con código y mensaje seguros); tiempo agotado o 5xx lanzan `PaymentUnavailableException`; un resultado `requires_action` se devuelve como `REQUIRES_ACTION`. `BT/payment/service/StripeGatewayTest.java` con `MockRestServiceServer`: cuerpo y encabezados correctos, aprobada, rechazada, `requires_action`, 5xx y tiempo agotado, captura y cancelación, y que la clave secreta nunca aparece en excepciones.
- [X] T010 En `BE/cart/service/CartService.java` añadir los métodos que necesita la confirmación: `Cart lockForCheckout(UUID customerId)` (reutiliza `loadOrCreateLocked`), `CartView snapshot(Cart cart)` o equivalente que devuelva el `CartResponse` vigente y los datos para fijar el pedido (líneas con producto y precio vigentes, restaurante y dirección), y `void clearAfterCheckout(Cart cart)` (usa `Cart.clear()`, conserva la dirección seleccionada). No cambiar los contratos de 005; `BT/cart/service/` mantiene verdes las pruebas existentes. La vista del checkout NO debe cambiar el estado del carrito: reutilizar la construcción de `view` sin el efecto de `resolveAddress` (que retira y guarda la dirección fuera de zona); en `confirm`, una dirección fuera de zona se rechaza con `NOT_ORDERABLE` en lugar de limpiarse en silencio.
- [X] T011 Actualizar `BT/support/IntegrationTestSupport.java`: en `cleanDatabase()` borrar `delete from transacciones_pago`, `delete from pagos`, `delete from lineas_pedido` y `delete from pedidos` **antes** de `lineas_carrito`/`carritos`; declarar `@MockitoBean StripeGateway stripeGateway` para todas las pruebas de integración y un helper que lo configure en modo «aprueba» (intent `pi_test_1`, `REQUIRES_CAPTURE`). Crear `BT/support/TestOrders.java` con un helper que arma un carrito listo (restaurante abierto con productos, dirección en zona seleccionada) usando `TestProducts` y `TestCarts`.

**Checkpoint**: la aplicación compila con las claves ficticias, las pruebas de 002–005 siguen verdes y `StripeGateway` está cubierta sin tocar Stripe real.

---

## Phase 3: User Story 1 - Confirmar el pedido y reservar el pago (Priority: P1) 🎯 MVP

**Goal**: El cliente revisa el resumen, elige un método de pago y confirma; el monto se reserva, el pedido
queda Pendiente con precios y dirección fijados y el carrito queda vacío (RF-14, RF-15, RF-16).

**Independent Test**: Con un carrito válido y la pasarela simulada en «aprueba», `POST /api/customer/orders`
devuelve 201 con pedido `PENDING`, pago `AUTHORIZED`, importes iguales a los recalculados y carrito vacío;
sin sesión 401 y con otro rol 403.

- [X] T012 [P] [US1] Crear los DTO de `BE/order/dto/`: `CheckoutResponse` (`cart` = `CartResponse`, `ready`, `blockers`, `paymentMethods`, `publishableKey`), `ConfirmOrderRequest` (`paymentMethod` obligatorio y solo `CARD`; `paymentMethodId` obligatorio, patrón `pm_[A-Za-z0-9_]+`, máximo 255; `expectedTotal` obligatorio, ≥ 0 con a lo sumo 2 decimales) y `OrderResponse` (según [checkout-api](./contracts/checkout-api.md); nunca expone `gateway_ref`).
- [X] T013 [US1] Crear `BE/payment/service/PaymentService.java` con `authorize(customerId, orderId, amount, paymentMethodId, confirmationKey)`: convierte el monto a céntimos con `BigDecimal` exacto (`movePointRight(2)` y `longValueExact`), llama a `StripeGateway.authorize` con `Idempotency-Key = "confirm-" + customerId + "-" + confirmationKey` y metadatos del pedido (sin datos personales), y devuelve el `intentId` si el estado es `REQUIRES_CAPTURE`. `DECLINED` lanza `PaymentDeclinedException(PAYMENT_DECLINED)`; `REQUIRES_ACTION` cancela el intent y lanza `PaymentDeclinedException(PAYMENT_ACTION_REQUIRED)`. Incluye `recordAuthorized(...)` que guarda `Payment` (`AUTHORIZED`) y la `PaymentTransaction` (`AUTHORIZATION`/`AUTHORIZED`). `BT/payment/service/PaymentServiceTest.java` (Mockito): conversión a céntimos sin redondeos, estados y claves de idempotencia.
- [X] T014 [US1] Crear `BE/order/service/CheckoutService.java` con `checkout(customerId)` (resumen, `ready` y `blockers`) y `confirm(customerId, confirmationKey, request)`. **Orden fijo de `confirm`**: 1) bloquear el carrito (`lockForCheckout`); 2) buscar un pedido por `(customerId, confirmationKey)` y, si existe, devolverlo (200) sin validar nada más ni tocar la pasarela; 3) rechazar `CART_EMPTY` (sin líneas), `NO_ADDRESS` (sin dirección seleccionada) y método de pago distinto de `CARD`; 4) recalcular importes con `CartPricing` mediante el resumen de `CartService`; 5) generar el `orderId` y autorizar con `PaymentService`; 6) guardar `Order` + `OrderLine` (precios, nombres y dirección copiados) + `Payment` + transacción, vaciar el carrito y devolver `OrderResponse`. El monto autorizado es siempre el total recalculado. Las validaciones de elegibilidad y la comparación con `expectedTotal` se añaden en US2. `BT/order/service/CheckoutServiceTest.java` (Mockito): pedido con importes y líneas fijados, carrito vaciado conservando la dirección, `CART_EMPTY`/`NO_ADDRESS` sin invocar la pasarela, y búsqueda por clave antes de validar el carrito.
- [X] T015 [US1] Crear `BE/order/controller/CheckoutController.java` (`GET /api/customer/checkout`) y `BE/order/controller/OrderController.java` (`POST /api/customer/orders` con `@RequestHeader("Idempotency-Key")` de 1–64 caracteres → 201; `GET` se completa en US5). El cliente sale siempre del token (`@AuthenticationPrincipal AccountPrincipal`). `BT/order/OrderConfirmationIntegrationTest.java` (`@SpringBootTest` + MockMvc): 201 con pedido `PENDING` y `payment.status=AUTHORIZED`, líneas y precios fijados (cambiar el precio del producto después no altera el pedido), carrito vacío y dirección seleccionada conservada, 401 sin token, 403 con rol restaurante y con cuenta `DISABLED`, carrito vacío y sin dirección → 409 `CART_EMPTY`/`NO_ADDRESS` sin llamar a la pasarela, método de pago no ofrecido → 400, y campos desconocidos (`total`, `customerId`) → 400. Además, `GET /api/customer/checkout`: `ready=false` con `blockers` correctos (`CART_EMPTY`, `NO_ADDRESS`, `RESTAURANT_UNAVAILABLE`, `LINES_UNAVAILABLE`), `publishableKey` presente y la clave secreta ausente de la respuesta; el pedido se conserva tras reiniciar el contexto de la aplicación (SC-001) y dos pedidos distintos del mismo cliente (con claves distintas, en momentos distintos) son independientes, cada uno con su propia reserva.
- [X] T016 [P] [US1] Frontend: `FE/features/checkout/checkout-validation.ts` (+ `checkout-validation.test.ts` con Vitest) con la regla espejo del id de método de pago y de la clave de idempotencia, y `FE/features/checkout/checkout-api.ts` (`getCheckout`, `confirmOrder` con `Idempotency-Key` generado con `crypto.randomUUID()` y reutilizado en los reintentos del mismo intento) usando `apiRequest` de `auth-api`.
- [X] T017 [US1] Frontend: `FE/features/checkout/CheckoutPage.tsx` (ruta `/confirmar-pedido`): resumen con productos, dirección e importes, selección del método de pago (solo «Tarjeta»), `Elements` + `CardElement` de `@stripe/react-stripe-js` cargados con la `publishableKey` del servidor, creación del `PaymentMethod` con `stripe.createPaymentMethod` y envío de solo su id; botón deshabilitado mientras se procesa. Éxito → navega a `/pedido/:id`. `FE/features/checkout/checkout.css`. No mostrar la confirmación del pedido como aceptación del restaurante.
- [X] T018 [US1] Frontend: añadir las rutas `/confirmar-pedido` y `/pedido/:id` en `FE/App.tsx` y un botón «Continuar» en `FE/features/cart/CartPage.tsx` (solo con carrito con líneas y dirección seleccionada) que lleva a `/confirmar-pedido`. Sin Playwright.

**Checkpoint**: un cliente confirma un pedido de extremo a extremo con la pasarela simulada y, manualmente, con Stripe en pruebas.

---

## Phase 4: User Story 2 - Validar de nuevo antes de registrar (Priority: P1)

**Goal**: Al confirmar se revalidan restaurante, productos, cantidades, dirección e importes; si algo falla no hay pedido ni reserva (RF-14).

**Independent Test**: Cada caso inválido responde 409 con su `code` y la pasarela **no** es invocada.

- [X] T019 [US2] En `BE/order/service/CheckoutService.java` completar la revalidación antes de autorizar: restaurante habilitado y abierto (`OrderEligibility.canReceiveOrders`), todas las líneas elegibles (si alguna no lo es se rechaza con `NOT_ORDERABLE` y un mensaje que lista los productos afectados; no se descartan en silencio), cantidades 1–20, dirección seleccionada dentro de zona (`DeliveryZone.covers`), y comparación `expectedTotal` contra el total recalculado → `OrderConflictException(AMOUNTS_CHANGED)` con el `CheckoutResponse` vigente. Ninguno de estos rechazos llama a `StripeGateway`.
- [X] T020 [US2] `BT/order/OrderValidationIntegrationTest.java`: restaurante pausado, fuera de horario, no aprobado o con cuenta inhabilitada; producto no disponible o retirado (mensaje lista el producto); cantidad inválida forzada por SQL; dirección fuera de zona tras cambiar la configuración; `expectedTotal` distinto tras modificar el precio de un producto (409 `AMOUNTS_CHANGED` con resumen vigente); importes enviados por el cliente ignorados/rechazados y reserva igual al total recalculado al céntimo; en todos, `verify(stripeGateway, never()).authorize(...)` y sin filas en `pedidos`, `pagos`; el carrito se conserva íntegro. Incluye el caso base: con `expectedTotal` igual al recalculado, la reserva coincide al céntimo con el total aunque el cuerpo traiga campos de importe adicionales (rechazados con 400 por Jackson estricto).

---

## Phase 5: User Story 3 - Fallo de pago y confirmación repetida (Priority: P1)

**Goal**: Mensajes claros ante fallos de pago y un único pedido y una única reserva ante reintentos y simultaneidad; sin reserva huérfana (RF-14, RF-16).

**Independent Test**: Autorización rechazada, 3DS y pasarela caída no crean pedido; 10 envíos de la misma clave dejan un pedido y una reserva.

- [X] T021 [P] [US3] Crear `BE/payment/service/PaymentRecorder.java` (`@Component`, `@Transactional(propagation = REQUIRES_NEW)`) con `recordFailure(customerId, orderId|null, confirmationKey, type, amount, code, message)` para registrar una `PaymentTransaction` `FAILED` que persista aunque la transacción principal se revierta; solo guarda códigos y mensajes seguros. Usarlo desde `PaymentService` en los fallos de autorización (rechazo, 3DS, pasarela no disponible). `BT/payment/service/PaymentRecorderTest.java`.
- [X] T022 [US3] En `BE/order/service/CheckoutService.java` completar idempotencia y compensación. **Estructura**: separar `confirm` en dos niveles. El método público `confirm` **no es transaccional** y llama a un método transaccional interno `confirmInTransaction` (en un bean aparte, p. ej. `BE/order/service/CheckoutTransaction.java`, para que `@Transactional` funcione). Dentro de la transacción, tras autorizar, usar `saveAndFlush` de pedido, pago y transacción dentro de un `try` para que cualquier fallo de BD aflore antes del commit. **Compensación**: si falla cualquier paso posterior a la autorización, cancelar el `PaymentIntent` con `PaymentService.cancelQuietly(intentId, key)` (best effort, registra su resultado) y propagar el error, **salvo** que la causa sea una clave duplicada. **Duplicado**: si `confirmInTransaction` falla con `DataIntegrityViolationException` por `(customer_id, confirmation_key)`, el método público (ya fuera de la transacción abortada) vuelve a leer el pedido por clave y lo devuelve con 200; no se cancela la reserva, porque es la misma del pedido existente (misma `Idempotency-Key` hacia Stripe). **Tiempo agotado en la autorización**: un único reintento con la misma clave de idempotencia; si devuelve `REQUIRES_CAPTURE`, cancelarlo y responder `PaymentUnavailableException`. El carrito se conserva en todo fallo. Actualizar la estructura del plan si hace falta (`CheckoutTransaction` en `order/service`).
- [X] T023 [US3] `BT/order/OrderPaymentFailureIntegrationTest.java`: pasarela rechaza (402 `PAYMENT_DECLINED`, sin pedido, carrito intacto, transacción `FAILED` registrada sin pedido), tarjeta que exige 3DS (402 `PAYMENT_ACTION_REQUIRED`, se cancela el intent, sin pedido), pasarela no disponible (503, sin pedido ni reserva), y compensación: forzar un fallo al guardar tras autorizar y verificar `cancel` en la pasarela, sin pedido y carrito intacto. Además: fallo forzado en el commit (p. ej. restricción violada al vaciar el buffer tras autorizar) → se invoca `cancel` en la pasarela y no queda pedido.
- [X] T024 [US3] `BT/order/OrderIdempotencyConcurrencyIntegrationTest.java`: reintento secuencial con la misma clave devuelve el mismo pedido (200) y `authorize` se invoca una sola vez; **al menos 10 envíos simultáneos** de la misma clave (`ExecutorService`) producen exactamente un pedido, un `Payment` y una `PaymentTransaction` de autorización; dos claves distintas con el mismo carrito: solo una prospera porque el carrito se vacía (la otra recibe `CART_EMPTY`); y la restricción única de la base de datos es la que impide el duplicado cuando se evita el bloqueo (inserción directa por SQL con la misma clave falla). Además: insertar un pedido con la misma clave por SQL y forzar que una segunda confirmación llegue a autorizar (sin pasar por la búsqueda previa): devuelve el pedido existente (200) y la pasarela **no** recibe `cancel`.
- [X] T025 [P] [US3] Frontend: en `FE/features/checkout/CheckoutPage.tsx` mostrar mensajes claros para 402 (rechazo y verificación adicional no soportada: «usa otra tarjeta»), 503 (reintentar), 409 `AMOUNTS_CHANGED` (reemplazar el resumen por el vigente y pedir confirmar de nuevo), 409 `NOT_ORDERABLE`/`CART_EMPTY`/`NO_ADDRESS` (enlace al carrito); reutilizar la misma `Idempotency-Key` al reintentar el mismo intento y generar una nueva solo tras cambiar el carrito o la tarjeta; evitar doble envío.

---

## Phase 6: User Story 4 - Operaciones de cobro y liberación de la reserva (Priority: P2)

**Goal**: `PaymentService.capture` y `release` listas para la spec 007: una sola vez, excluyentes, con fallos reintentables y sin reembolso (RF-16, RF-17).

**Independent Test**: Capturar cobra y registra; liberar nunca cobra; repetir o mezclar se rechaza sin tocar la pasarela; un fallo de pasarela se registra y puede reintentarse.

- [X] T026 [US4] En `BE/payment/service/PaymentService.java` añadir `capture(UUID orderId)` y `release(UUID orderId)` `@Transactional`: bloquear `Payment` con `findByOrderIdForUpdate`, exigir `AUTHORIZED` (si no, `PaymentStateException` sin llamar a la pasarela), llamar a `StripeGateway.capture` o `cancel` con claves `"capture-" + orderId` y `"release-" + orderId`, transicionar a `CAPTURED`/`RELEASED` y registrar la `PaymentTransaction`. Un fallo de pasarela registra `FAILED` mediante `PaymentRecorder`, deja el estado `AUTHORIZED` y se puede reintentar. No existe ninguna operación de reembolso ni endpoint HTTP.
- [X] T027 [US4] `BT/payment/PaymentLifecycleIntegrationTest.java`: capturar cobra el monto total reservado y registra `CAPTURE`; liberar registra `RELEASE` y nunca llama a `capture`; segunda captura, segunda liberación y captura tras liberar (y viceversa) → `PaymentStateException` y la pasarela no se invoca de nuevo; fallo de pasarela al capturar/liberar registra `FAILED`, estado sin cambios y el reintento prospera; captura y liberación **simultáneas** sobre el mismo pedido: solo una prospera y el estado final es coherente con la única transacción exitosa; verificar por reflexión/estructura que no existen métodos de reembolso. `BT/payment/service/PaymentServiceTest.java` ampliado con los casos de estado.

---

## Phase 7: User Story 5 - Registro y consulta del pago de un pedido (Priority: P2)

**Goal**: Cada transacción queda registrada y asociada a su pedido, y el cliente consulta su pedido y el estado del pago sin ver datos ajenos ni de tarjeta (RF-17, RF-04).

**Independent Test**: Tras confirmar, capturar y liberar, las transacciones existen con su tipo y estado; `GET` del pedido propio responde 200 y el ajeno 404; ningún dato de tarjeta en registros ni respuestas.

- [X] T028 [US5] En `BE/order/controller/OrderController.java` y `BE/order/service/CheckoutService.java` añadir `GET /api/customer/orders/{id}` → 200 `OrderResponse` con `payment.status` (`AUTHORIZED`, `CAPTURED` o `RELEASED`, leído de `PaymentRepository`) con `findByIdAndCustomerId`; un pedido inexistente o ajeno → 404 (`OrderNotFoundException`) sin revelar su existencia.
- [X] T029 [US5] `BT/order/OrderQueryIntegrationTest.java`: pedido propio 200 con estado de pago correcto antes y después de capturar o liberar; pedido ajeno 404; 401 sin token; 403 otro rol y cuenta `DISABLED`; `BT/payment/PaymentTransactionIntegrationTest.java`: autorización, captura, liberación y fallo quedan registrados con tipo, estado, monto, resultado, referencia y fecha, asociados al pedido (el fallo de autorización sin `order_id`); inspeccionar respuestas JSON y filas de `transacciones_pago`/`pagos` para comprobar que no contienen números de tarjeta, CVC ni la clave secreta, y que `OrderResponse` no expone `gateway_ref`.
- [X] T030 [P] [US5] Frontend: `FE/features/checkout/OrderPage.tsx` (ruta `/pedido/:id`): muestra estado «Pendiente de aceptación», productos, dirección, importes y estado del pago («Reservado»/«Cobrado»/«Liberado»); un 404 muestra «Pedido no encontrado». No muestra aceptación, rechazo, cancelación ni historial.

---

## Phase 8: Polish & Cross-Cutting

- [X] T031 [P] Confirmar que `GlobalExceptionHandler`, los logs y las excepciones no exponen claves ni datos de tarjeta (búsqueda por `sk_test_`, `pk_test_`, `card`, `cvc` en `BE/` y pruebas) y que `backend/.env` no está versionado (`git ls-files backend/.env` vacío).
- [X] T032 Ejecutar `cd backend ; ./mvnw verify` y comprobar el reporte JaCoCo: ≥ 90 % de líneas de lógica de negocio incluyendo `order` y `payment` (sin excluir reglas), con el desglose de líneas cubiertas y totales. Ejecutar `cd frontend ; pnpm test:coverage ; pnpm build ; pnpm lint`.
- [ ] T033 (pruebas automáticas hechas; recorrido manual con Stripe pendiente hasta tener claves de prueba en `backend/.env`) Recorrer [quickstart.md](./quickstart.md): pruebas automáticas y, si hay claves de prueba en `backend/.env`, el recorrido manual con tarjetas de prueba. Si no hay claves, registrar el recorrido manual como pendiente sin declararlo hecho.
- [X] T034 [P] Trazabilidad (constitución VI): en `specs/006-confirmacion-pedido-pago/validation.md` registrar RF-04, RF-14, RF-15, RF-16 y RF-17 como verificados/parciales según la evidencia; RNF-06 y RNF-08 como **pendientes** (HTTP local y claves de prueba); y los pendientes: webhooks, conciliación, caída entre autorizar y guardar y vencimiento de reservas (spec 007). Sin declarar pagos completos más allá de la evidencia.

---

## Dependencies & Execution Order

- **Phase 1** → **Phase 2** (bloquea todo) → **US1** → **US2**, **US3** (ambas amplían `CheckoutService` de US1) → **US4** (necesita `PaymentService`/`PaymentRecorder` de US1 y US3) → **US5** (necesita pedidos y pagos de US1 y las operaciones de US4 para sus pruebas) → **Polish**.
- Dentro de la Fase 2: T004–T008 en paralelo; T009 después de T004; T010 independiente; T011 después de T006–T009.
- US2 y US3 modifican el mismo archivo (`CheckoutService.java`): ejecutarlas en secuencia, no en paralelo.

## Parallel Opportunities

- T002 y T003 (Setup); T004, T005, T006, T007 y T008 (Foundational).
- T012 y T016 (DTO y frontend de validación/API de US1); T021 y T025 (US3); T030 (US5) respecto del backend.

## Implementation Strategy

- **MVP**: Fases 1–3 (US1) con la pasarela simulada: un pedido Pendiente con pago reservado y carrito vacío. Luego US2 y US3 (todas P1) antes de considerar el incremento utilizable; US4 y US5 (P2) cierran RF-16/RF-17.
- Verificar tras cada historia: compilación, pruebas de la historia y que las de 002–005 siguen verdes. No repetir comprobaciones aprobadas sin cambios (constitución V).
