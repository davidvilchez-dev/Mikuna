# Implementation Plan: Carrito, direcciones y cálculo de importes

**Branch**: `005-carrito-direcciones-importes` (directorio de spec; sin rama Git nueva) | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-carrito-direcciones-importes/spec.md`

## Summary

Este incremento implementa RF-11, RF-12 y RF-13, con la autorización de RF-04 aplicada a cada
operación. Las piezas son:

- **Backend, direcciones (RF-12):** módulo nuevo `address` con la entidad `Address`,
  `AddressService` y `DeliveryZone` (lista de distritos cubiertos, de configuración). El controlador
  `/api/customer/addresses` queda bajo una regla por rol nueva (`ROLE_CUSTOMER`). El cliente
  propietario sale siempre del token.
- **Backend, carrito (RF-11):** módulo nuevo `cart` con `Cart`, `CartLine` y `CartService`, bajo
  `/api/customer/cart`. Cada mutación bloquea la fila del carrito del cliente (research §2) para que
  no se pierdan ni dupliquen líneas. La elegibilidad reutiliza `OrderEligibility` (003) y los
  productos de `catalog` (004).
- **Backend, importes (RF-13):** `CartPricing` calcula subtotal, envío, comisión y total siempre en
  el servidor con `BigDecimal`. El resumen se calcula al consultar, no se guarda (research §3).
- **Dirección seleccionada:** columna `selected_address_id` del carrito; seleccionar valida
  pertenencia y zona; el resumen la revalida en cada consulta.
- **Frontend:** `/carrito` (líneas, importes y selección de dirección), `/mis-direcciones` (libreta
  de direcciones) y el botón «Agregar al carrito» en el catálogo público de 004 (con sesión de
  cliente; sin sesión lleva a iniciar sesión).

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19 (sin cambios)

**Primary Dependencies**: las ya instaladas (Web MVC, Data JPA, Security, Bean Validation,
`react-router`). No se añade ninguna.

**Storage**: PostgreSQL local; tablas nuevas `direcciones`, `carritos` y `lineas_carrito` creadas
por `ddl-auto=update`. Sin migraciones.

**Testing**: JUnit 5 + Mockito (reglas de carrito, zona, importes y redondeo);
`@SpringBootTest` + MockMvc sobre `mikuna_test_db` (401/403/404/409, un solo restaurante,
elegibilidad, propiedad por cliente y concurrencia de altas en el mismo carrito); JaCoCo ≥ 90 % de
líneas de negocio; Vitest para validación de dirección y cantidad. Sin Playwright.

**Target Platform**: igual que 002–004 (HTTP en localhost; Vite 5173 → API 8080).

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: SC-006 (< 2 s con datos locales). RNF-01 y la caché quedan aplazados.

**Constraints**:
- Cliente propietario, precios, importes y restaurante del carrito nunca se aceptan del cliente
  (Jackson estricto: campo desconocido → 400).
- Importes solo en servidor (nunca del cliente) y solo en `BigDecimal`, sin `double` ni `float`: precio × cantidad exacto; la comisión se redondea una vez (`setScale(2, RoundingMode.HALF_UP)`); total = subtotal + envío + comisión ya redondeados; todo se serializa con dos decimales. Spec 006 no debe reutilizar estos valores: debe recalcularlos con `CartPricing` al confirmar.
- Tarifa de envío, porcentaje de comisión y distritos cubiertos son **configuración** (no hay
  valores por defecto en la constitución): `mikuna.delivery.*` leídos de `backend/.env`; sin ellos
  el arranque falla con un mensaje claro. Las pruebas fijan valores de prueba.
- Un recurso ajeno responde 404, nunca 403, para no revelar su existencia.

**Scale/Scope**: 5 endpoints de dirección, 6 de carrito, 3 tablas, 2 páginas nuevas y ajustes en el
catálogo público, la cabecera y `App.tsx`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Dos módulos nuevos (`address`, `cart`) con solo las capas que necesitan. Dependencias en un sentido: `cart` → `address`, `catalog`, `restaurant`; ninguno vuelve a `cart`. Sin interfaces vacías. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; reglas y cálculo en servicios; repositorios JPA. El backend recalcula importes y elegibilidad; el cliente solo envía producto, cantidad e id de dirección. | ✅ |
| III. Seguridad | Rutas `/api/customer/**` con `hasRole("CUSTOMER")` + cuenta habilitada (filtro de 002) + pertenencia por token (ajeno → 404). Ninguna ruta pública nueva. Sin datos sensibles nuevos. | ✅ |
| IV. Concurrencia | Mutaciones del carrito serializadas por bloqueo de la fila `carritos` (`PESSIMISTIC_WRITE`); `UNIQUE (cart_id, product_id)` como red de seguridad; `UNIQUE (customer_id)` para un solo carrito. Pruebas de integración concurrentes. | ✅ |
| V. Pruebas | JUnit + Mockito, integración contra PostgreSQL local, JaCoCo ≥ 90 % y Vitest. Sin Playwright. | ✅ |
| VI. Trazabilidad | RF-04 (parcial), RF-11, RF-12, RF-13; RNF-07 y RNF-11 aplicables; RNF-01 y RNF-03 aplazados. Confirmación, pago e historial fuera. | ✅ |
| VII. Incremental | Solo lo de la spec, sin dependencias nuevas ni caché; decisiones en [research.md](./research.md). | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Reglas de negocio de la constitución.** Zona, envío y comisión se consultaron al usuario el
   2026-11-17 (lista de distritos, tarifa fija, porcentaje como cargo al cliente). Los **valores
   concretos** (distritos, tarifa, porcentaje) los debe proporcionar el usuario antes de ejecutar
   la app; se piden como variables de entorno sin valor por defecto.
2. **Regla por rol nueva.** `SecurityConfig` solo protegía `RESTAURANT` y `ADMIN`; se añade
   `/api/customer/** → CUSTOMER`. Es la única ampliación de seguridad.
3. **Elegibilidad al agregar.** Se exige que el restaurante pueda recibir pedidos *ahora*
   (`OrderEligibility.canReceiveOrders`), como dice la spec. Cambiar eso (p. ej. permitir armar el
   carrito fuera de horario) sería una decisión de negocio nueva.
4. **Zona por distrito.** `Restaurant.zone` ya es texto; la zona de reparto se valida contra la
   lista de distritos de la **dirección**, sin relacionarla con la del restaurante (no se pidió).

Resultado: **PASA** (re-evaluado tras el diseño de la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/005-carrito-direcciones-importes/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/cart-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── address/
│   ├── model/        Address
│   ├── repository/   AddressRepository
│   ├── service/      AddressService, DeliveryZone (distritos cubiertos, sin tildes ni mayúsculas),
│   │                 AddressDeletedEvent (el carrito deja de seleccionar la dirección eliminada)
│   ├── controller/   AddressController (/api/customer/addresses)
│   └── dto/          AddressRequest, AddressResponse
├── cart/
│   ├── model/        Cart, CartLine
│   ├── repository/   CartRepository (bloqueo de fila), CartLineRepository
│   ├── service/      CartService (reglas y mutaciones), CartPricing (importes)
│   ├── controller/   CartController (/api/customer/cart)
│   └── dto/          AddLineRequest, QuantityRequest, SelectAddressRequest, CartResponse,
│                     CartLineResponse, TotalsResponse
├── common/config/                        # DeliveryProperties y DeliveryConfig (distritos, tarifa, comisión)
├── common/security/SecurityConfig.java   # + /api/customer/** hasRole("CUSTOMER")
├── common/exception/                     # + AddressNotFoundException (404), OutsideDeliveryZoneException (400),
│                                         #   CartConflictException (409: otro restaurante),
│                                         #   ProductNotOrderableException (409); handlers en GlobalExceptionHandler
└── resources/application.properties      # + mikuna.delivery.districts / delivery-fee / commission-rate
backend/.env.example                       # + MIKUNA_DELIVERY_DISTRICTS, MIKUNA_DELIVERY_FEE, MIKUNA_COMMISSION_RATE (sin valores)
backend/src/test/resources/application.properties   # + valores de prueba de mikuna.delivery.*
backend/src/test/java/com/mikuna/backend/{address,cart}/   # unit + integration

frontend/src/
├── App.tsx                              # + /carrito, /mis-direcciones
├── features/catalog/RestaurantCatalogPage.tsx   # + «Agregar al carrito» (cliente) y aviso de carrito de otro restaurante
├── features/home/components/SiteHeader.tsx      # + enlaces «Carrito» y «Mis direcciones» para el cliente
└── features/cart/
    ├── CartPage.tsx                 # líneas, cantidades, importes y selección de dirección
    ├── AddressesPage.tsx            # libreta: alta, edición, eliminación
    ├── AddressForm.tsx
    ├── cart-api.ts                  # fetch con token (cliente)
    ├── cart-validation.ts           # reglas espejo de dirección y cantidad (+ .test.ts)
    └── cart.css
```

**Structure Decision**: se mantiene la aplicación web existente. El backend añade los módulos
`address` y `cart` por funcionalidad (constitución I) y el frontend añade `features/cart`.

## Complexity Tracking

Sin violaciones que justificar.
