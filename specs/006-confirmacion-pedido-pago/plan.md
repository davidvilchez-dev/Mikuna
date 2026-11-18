# Implementation Plan: Confirmación del pedido y procesamiento del pago

**Branch**: `006-confirmacion-pedido-pago` | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-confirmacion-pedido-pago/spec.md`

## Summary

Este incremento implementa RF-14, RF-15, RF-16 y RF-17 con la autorización de RF-04. Las piezas son:

- **Backend, pagos (RF-16/RF-17):** módulo nuevo `payment` con `StripeGateway` (API REST de Stripe por
  HTTPS, solo pruebas, aislada como `CloudinaryImageStorage`), `PaymentService` (autorizar, capturar,
  liberar; bloqueo de la fila del pago), y las entidades `Payment` y `PaymentTransaction`.
- **Backend, pedidos (RF-14/RF-15):** módulo nuevo `order` con `Order`, `OrderLine` y `CheckoutService`,
  bajo `/api/customer/checkout` y `/api/customer/orders`. Confirmar bloquea el carrito del cliente,
  revalida todo, recalcula importes con `CartPricing`, autoriza el pago, guarda el pedido con precios y
  dirección fijados y vacía el carrito en una transacción.
- **Idempotencia:** encabezado `Idempotency-Key` del cliente + `UNIQUE (customer_id, confirmation_key)`
  + la misma clave hacia Stripe (research §5).
- **Sin reserva huérfana:** compensación cancelando el `PaymentIntent` si falla cualquier paso tras
  autorizar (research §6). Capturar y liberar quedan como operaciones internas para la spec 007.
- **Frontend:** `/confirmar-pedido` (resumen, método y formulario de tarjeta de Stripe) y `/pedido/:id`;
  botón «Continuar» en `/carrito`.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19

**Primary Dependencies**: backend, las ya instaladas (sin SDK de Stripe; `RestClient`). Frontend:
`@stripe/stripe-js` 10.3.0 y `@stripe/react-stripe-js` 7.1.0 (nuevas).

**Storage**: PostgreSQL local; tablas nuevas `pedidos`, `lineas_pedido`, `pagos` y
`transacciones_pago` por `ddl-auto=update`. Sin migraciones.

**Testing**: JUnit 5 + Mockito (reglas de confirmación, estados de pago, compensación); `@SpringBootTest`
+ MockMvc sobre `mikuna_test_db` con `StripeGateway` simulado (401/403/404/409/402/503, un solo pedido
ante confirmaciones simultáneas, captura/liberación excluyentes); JaCoCo ≥ 90 % de líneas de negocio;
Vitest para la validación del formulario. Sin llamadas a Stripe real ni Playwright.

**Target Platform**: igual que 002–005 (HTTP en localhost; Vite 5173 → API 8080; el navegador carga
Stripe.js por HTTPS).

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: SC-001 (< 1 min el flujo completo en pruebas locales). RNF-01 aplazado.

**Constraints**:
- Propietario, restaurante, precios, importes, dirección y estado nunca se aceptan del cliente; el
  monto autorizado es siempre el total recalculado en el servidor (`expectedTotal` solo se compara).
- Datos de tarjeta nunca entran, se guardan ni se registran: el backend solo recibe `pm_...` y guarda
  referencias de Stripe.
- `STRIPE_SECRET_KEY` debe empezar con `sk_test_` y `STRIPE_PUBLISHABLE_KEY` con `pk_test_`, o el
  arranque falla; las claves no aparecen en logs ni respuestas. Moneda configurable (`pen`).
- Un pedido ajeno responde 404. Sin endpoints HTTP para capturar o liberar.
- Llamadas a Stripe con `Idempotency-Key` y tiempos de espera acotados.

**Scale/Scope**: 3 endpoints HTTP, 4 tablas, 2 páginas nuevas y un ajuste en el carrito.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Dos módulos nuevos (`order`, `payment`) con solo las capas necesarias. Dependencias en un sentido: `order` → `cart`, `address`, `catalog`, `restaurant`, `payment`; `payment` no depende de `order`. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; reglas en servicios; `StripeGateway` separa la integración de las reglas y se sustituye en pruebas. El backend recalcula importes y revalida; el cliente solo envía método, `pm_` y el total esperado. | ✅ |
| III. Seguridad | `/api/customer/**` ya exige `CUSTOMER` + cuenta habilitada; pertenencia por token (ajeno → 404). Stripe solo en modo de pruebas por HTTPS; claves solo en `backend/.env` y validadas al arrancar; sin datos de tarjeta. RNF-06/08 no se declaran cumplidos. | ✅ |
| IV. Concurrencia | Confirmación serializada por bloqueo del carrito + `UNIQUE (customer_id, confirmation_key)` + idempotencia hacia Stripe; captura y liberación con bloqueo de la fila `pagos` y estados excluyentes; compensación ante fallo tras autorizar. Pruebas de integración concurrentes. Flujo de estados: solo `PENDING`. | ✅ |
| V. Pruebas | JUnit + Mockito con `StripeGateway` simulado; integración contra PostgreSQL local; JaCoCo ≥ 90 %; Vitest. Sin Playwright. | ✅ |
| VI. Trazabilidad | RF-04, RF-14, RF-15, RF-16, RF-17 aplicables; RNF-07 y RNF-11 aplicables; RNF-06 y RNF-08 con evidencia de despliegue pendiente. Aceptación/rechazo/cancelación (007) e historial (010) fuera. | ✅ |
| VII. Incremental | Solo lo de la spec; decisiones puntuales en [research.md](./research.md); sin SDK ni webhooks. | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Riesgo residual de pago.** Una caída del servidor justo entre autorizar y guardar puede dejar una
   reserva sin pedido; la compensación cubre fallos controlados, no caídas. Webhooks y conciliación
   están aplazados (constitución IV); se anota como pendiente y no se declara resuelto.
2. **Moneda.** El sandbox debe admitir `pen`; es configurable (research §4).
3. **Vencimiento de reservas de Stripe.** Una reserva sin captura expira en la pasarela tras varios
   días; su manejo corresponde a la spec 007.
4. **`expectedTotal`.** Es el único importe que envía el cliente y solo se compara; decisión derivada
   de FR-003 para pedir nueva confirmación cuando cambian los precios.

Resultado: **PASA** (re-evaluado tras el diseño de la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/006-confirmacion-pedido-pago/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/checkout-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── payment/
│   ├── model/        Payment, PaymentStatus, PaymentTransaction, TransactionType, TransactionStatus
│   ├── repository/   PaymentRepository (bloqueo de fila), PaymentTransactionRepository
│   └── service/      PaymentService (autorizar, capturar, liberar), PaymentRecorder (REQUIRES_NEW para
│                     fallos), StripeGateway (REST sobre HTTPS), StripeProperties (claves sk_test_/pk_test_,
│                     moneda, validación al arrancar)
├── order/
│   ├── model/        Order, OrderLine, OrderStatus (solo PENDING), PaymentMethod
│   ├── repository/   OrderRepository
│   ├── service/      CheckoutService (resumen, orden de validación, idempotencia, compensación),
│   │                 CheckoutTransaction (tramo transaccional de la confirmación)
│   ├── controller/   CheckoutController (/api/customer/checkout), OrderController (/api/customer/orders)
│   └── dto/          CheckoutResponse, ConfirmOrderRequest, OrderResponse
├── cart/service/CartService.java   # + método para entregar el carrito bloqueado y vaciarlo al confirmar
├── common/exception/               # + PaymentDeclinedException (402), PaymentUnavailableException (503),
│                                   #   OrderConflictException (409 con code), PaymentStateException (409),
│                                   #   OrderNotFoundException (404); handlers en GlobalExceptionHandler
└── resources/application.properties # + stripe.secret-key / publishable-key, mikuna.payments.currency
backend/.env.example                 # ya incluye STRIPE_SECRET_KEY y STRIPE_PUBLISHABLE_KEY (sin valor)
backend/src/test/resources/application.properties # + claves ficticias de prueba y moneda
backend/src/test/java/com/mikuna/backend/{order,payment}/   # unit + integration (StripeGateway mock)

frontend/src/
├── App.tsx                          # + /confirmar-pedido, /pedido/:id
├── features/cart/CartPage.tsx       # + botón «Continuar» (cliente con carrito listo)
└── features/checkout/
    ├── CheckoutPage.tsx             # resumen, método de pago y CardElement de Stripe
    ├── OrderPage.tsx                # pedido y estado del pago
    ├── checkout-api.ts              # fetch con token y Idempotency-Key
    └── checkout.css
```

**Structure Decision**: se mantiene la aplicación web existente. El backend añade los módulos `order` y
`payment` por funcionalidad (constitución I) y el frontend añade `features/checkout`.

## Complexity Tracking

Sin violaciones que justificar.
