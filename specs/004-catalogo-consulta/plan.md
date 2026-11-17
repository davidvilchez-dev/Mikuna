# Implementation Plan: Catálogo y consulta de restaurantes y productos

**Branch**: `004-catalogo-consulta` (directorio de spec; sin rama Git nueva) | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-catalogo-consulta/spec.md`

## Summary

Este incremento implementa RF-08, RF-09 y RF-10, con la autorización de RF-04 aplicada a cada
operación. Las piezas son:

- **Backend, gestión (RF-08):** un módulo nuevo `catalog` con la entidad `Product` y
  `ProductService`. El controlador `/api/restaurant/me/products` queda bajo la regla por rol ya
  existente (`ROLE_RESTAURANT`). El restaurante propietario se resuelve siempre desde el token; no
  se reciben ids de restaurante.
- **Backend, consulta pública (RF-09, RF-10):** `PublicRestaurantController` en
  `/api/public/restaurants/**` (solo `GET`, sin sesión). Muestra restaurantes aprobados con cuenta
  habilitada y, de cada uno, solo sus productos activos y disponibles.
- **Situación de atención:** `OrderEligibility` (spec 003) incorpora `attentionStatus()` con los
  valores `OPEN`, `OUTSIDE_HOURS` y `PAUSED`; `canReceiveOrders()` se apoya en ella para que haya
  una sola regla.
- **Imágenes:** subida multipart validada (tipo real, ≤ 2 MB) y almacenada en Cloudinary mediante
  `CloudinaryImageStorage`, con la API REST firmada y `RestClient`; no se añade ninguna dependencia.
- **Consistencia:** `@Version` + `version` enviada por el cliente (409 ante edición desfasada) y
  `UNIQUE` sobre nombre y categoría de los productos activos (409 ante duplicado, también
  concurrente).
- **Frontend:** `/restaurantes` (búsqueda), `/restaurantes/:id` (catálogo) y
  `/mi-restaurante/productos` (gestión); el inicio y `/mi-restaurante` enlazan a ellas.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.2); TypeScript ~6.0 con React 19 (sin cambios)

**Primary Dependencies**: las ya instaladas en 002 y 003 (Web MVC, Data JPA, Security, Bean
Validation, `react-router`). No se añade ninguna: Cloudinary se usa por su API REST con el
`RestClient` de Spring.

**Storage**: PostgreSQL local; tabla nueva `productos` creada por `ddl-auto=update`. Sin
migraciones. Imágenes en Cloudinary (carpeta `mikuna/products`); en la tabla solo se guardan la URL
y el `public_id`.

**Testing**: JUnit 5 + Mockito (reglas de producto, atención, búsqueda y validación de imagen);
`@SpringBootTest` + MockMvc sobre `mikuna_test_db` (401/403/404/409, visibilidad, concurrencia de
duplicado y de edición) con `CloudinaryImageStorage` sustituido por un mock; JaCoCo ≥ 90 % de líneas
de negocio; Vitest para la validación del formulario de producto. Sin Playwright ni llamadas reales
a Cloudinary en las pruebas.

**Target Platform**: igual que 002 y 003 (HTTP en localhost; Vite 5173 → API 8080).

**Project Type**: Aplicación web (frontend React + monolito Spring Boot)

**Performance Goals**: SC-006 (< 2 s con datos locales). RNF-01, RNF-03 y la caché quedan aplazados.

**Constraints**:
- El restaurante propietario, el estado de retiro y la disponibilidad del restaurante nunca se
  aceptan del cliente (Jackson estricto de 002: campo desconocido → 400).
- Las rutas públicas son solo de lectura y no devuelven datos de cuenta, titular, teléfono ni
  dirección.
- La imagen se valida por su contenido (firma del archivo), no solo por el tipo declarado.
- Credenciales de Cloudinary solo en `backend/.env`; nunca en commits (constitución).
- Errores en `ProblemDetail`, sin datos de otros restaurantes.

**Scale/Scope**: 6 endpoints de gestión, 3 públicos, 1 tabla, 3 páginas nuevas y ajustes menores en
el inicio, `RestaurantPage` y `apiRequest`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Comprobación | Estado |
| --- | --- | --- |
| I. Monolito modular | Un módulo nuevo (`catalog`) con `model`, `repository`, `service`, `controller` y `dto`. Depende de `restaurant` (lectura) y de `account` solo vía consulta del restaurante; no al revés. `CloudinaryImageStorage` es una clase concreta, sin interfaz vacía. | ✅ |
| II. Responsabilidades | Controladores solo HTTP; reglas, visibilidad y atención en servicios; repositorios JPA; el cliente de Cloudinary aislado en una clase. El backend recalcula visibilidad y atención. | ✅ |
| III. Seguridad | Gestión: rol `RESTAURANT` por ruta + cuenta habilitada (filtro de 002) + restaurante `APPROVED` en el servicio + pertenencia por token (producto ajeno → 404). Públicas: solo `GET /api/public/**`, justificadas en la spec (decisión del usuario), con DTO propios sin datos sensibles. Validación de imagen por contenido y tamaño. Secretos en `.env`. | ✅ |
| IV. Concurrencia | Duplicado de producto: `UNIQUE` con marca de activo (research §3). Edición desfasada: `@Version` + versión del cliente. Retiro y disponibilidad con la misma versión. Pruebas de integración concurrentes. | ✅ |
| V. Pruebas | JUnit + Mockito, integración contra PostgreSQL local, JaCoCo ≥ 90 % y Vitest. Sin Playwright. | ✅ |
| VI. Trazabilidad | RF-04 (parcial), RF-08, RF-09, RF-10; RNF-07 y RNF-11 aplicables; RNF-01 y RNF-03 aplazados. Carrito (spec 005), pedidos, pagos y RF-32 fuera. | ✅ |
| VII. Incremental | Solo lo de la spec, sin dependencias nuevas ni caché. Decisiones en [research.md](./research.md). | ✅ |

**Puntos a señalar (sin ampliar el alcance):**

1. **Rutas públicas nuevas.** La spec 002 solo declaraba públicas las de registro, acceso y
   `/actuator/health`. Se añade `GET /api/public/**` (lectura de restaurantes y catálogo), por
   decisión del usuario del 2026-11-17. La spec 002 y su `SecurityConfig` documentan la lista de
   rutas públicas; esta es la única ampliación.
2. **Cloudinary.** La constitución lo permite «cuando sea necesario» y con cifrado (HTTPS); aquí
   hace falta porque RF-08 exige imagen. No se prepara nada más (MapTiler, correo, etc.).
3. **Cambio en la spec 003.** `OrderEligibility` se refactoriza con comportamiento idéntico para
   exponer `attentionStatus()`. Las pruebas existentes de 003 deben seguir pasando.
4. **Búsqueda en memoria.** El filtrado por nombre, categoría y zona y el orden por atención se
   hacen en el servicio sobre los restaurantes visibles; es suficiente a escala local y evita
   extensiones de PostgreSQL (research §2). Su optimización queda en la spec de carga.

Resultado: **PASA** (re-evaluado tras el diseño de la fase 1, sin cambios).

## Project Structure

### Documentation (this feature)

```text
specs/004-catalogo-consulta/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/catalog-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (pendiente)
```

### Source Code (repository root)

```text
backend/src/main/java/com/mikuna/backend/
├── catalog/
│   ├── model/        Product
│   ├── repository/   ProductRepository
│   ├── service/      ProductService (gestión del catálogo propio),
│   │                 PublicCatalogService (búsqueda, filtros y catálogo público),
│   │                 ImageValidator (tipo real y tamaño),
│   │                 CloudinaryImageStorage (subida y borrado firmados)
│   ├── controller/   ProductController (/api/restaurant/me/products),
│   │                 PublicRestaurantController (/api/public/restaurants)
│   └── dto/          ProductRequest, AvailabilityRequest, ProductResponse (gestión),
│                     PublicProductResponse, PublicRestaurantResponse, PageResponse<T>,
│                     CatalogResponse, FiltersResponse
├── restaurant/
│   ├── model/        + AttentionStatus (OPEN, OUTSIDE_HOURS, PAUSED)
│   ├── repository/   RestaurantRepository + consultas de restaurantes visibles
│   └── service/      OrderEligibility + attentionStatus()
├── common/security/SecurityConfig.java   # + GET /api/public/** permitAll
├── common/exception/                     # + ProductNotFoundException (404), ProductConflictException (409),
│                                         #   ImageUnavailableException (503); handlers en GlobalExceptionHandler
└── resources/application.properties      # + cloudinary.folder, multipart 2MB (las credenciales ya están en backend/.env)
backend/.env.example                       # + cloudinary.cloud-name / api-key / api-secret (sin valores)
backend/src/test/java/com/mikuna/backend/catalog/   # unit + integration

frontend/src/
├── App.tsx                          # + /restaurantes, /restaurantes/:id, /mi-restaurante/productos
├── features/auth/auth-api.ts        # apiRequest admite FormData (sin Content-Type JSON forzado)
├── features/home/components/SiteHeader.tsx   # enlace «Restaurantes» a /restaurantes
├── features/restaurant/RestaurantPage.tsx    # enlace «Mi catálogo» cuando está aprobado
└── features/catalog/
    ├── RestaurantSearchPage.tsx     # búsqueda con filtros y resultados con su situación de atención
    ├── RestaurantCatalogPage.tsx    # catálogo público por categorías y aviso de atención
    ├── ProductsPage.tsx             # gestión: lista, alta, edición, disponibilidad y retiro
    ├── ProductForm.tsx              # formulario con vista previa de imagen
    ├── catalog-api.ts               # fetch público (sin token) y de gestión (con token)
    ├── catalog-validation.ts        # reglas espejo de producto e imagen (+ .test.ts)
    └── catalog.css
```

**Structure Decision**: se mantiene la aplicación web existente. El backend añade el módulo
`catalog` por funcionalidad (constitución I) y el frontend añade `features/catalog` junto a
`features/auth` y `features/restaurant`.

## Complexity Tracking

Sin violaciones que justificar.
