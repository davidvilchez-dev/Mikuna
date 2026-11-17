---

description: "Tareas de implementación: catálogo y consulta de restaurantes y productos (RF-08, RF-09, RF-10, RF-04 parcial)"
---

# Tasks: Catálogo y consulta de restaurantes y productos

**Input**: Documentos de diseño en `specs/004-catalogo-consulta/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/catalog-api.md](./contracts/catalog-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra
PostgreSQL local (`mikuna_test_db`) y ≥ 90 % de líneas de lógica de negocio. Vitest para la lógica
del frontend. No se usa Playwright ni otra herramienta E2E de navegador. Las pruebas **no llaman a
Cloudinary**: sustituyen `CloudinaryImageStorage` por un mock.

**Organization**: Tareas agrupadas por historia de usuario; cada historia se prueba por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3 según [spec.md](./spec.md)
- Paquete base del backend: `backend/src/main/java/com/mikuna/backend/` (abreviado `BE/`).
  Pruebas: `backend/src/test/java/com/mikuna/backend/` (abreviado `BT/`).
  Frontend: `frontend/src/` (abreviado `FE/`).

---

## Phase 1: Setup (infraestructura compartida)

**Purpose**: Solo configuración; no se añaden dependencias.

- [X] T001 Configuración de imágenes y subida. En `backend/src/main/resources/application.properties` añadir `cloudinary.folder=mikuna/products`, `spring.servlet.multipart.max-file-size=2MB` y `spring.servlet.multipart.max-request-size=3MB`; **no** añadir las credenciales: `cloudinary.cloud-name`, `cloudinary.api-key` y `cloudinary.api-secret` ya están en `backend/.env` y se leen con `spring.config.import`. En `backend/src/test/resources/application.properties` (sustituye al principal en las pruebas) añadir las mismas claves de carpeta y multipart y valores falsos `cloudinary.cloud-name=test`, `cloudinary.api-key=test`, `cloudinary.api-secret=test`. En `backend/.env.example` añadir las tres claves `cloudinary.*` **sin valores reales**. No abrir ni copiar `backend/.env`.
- [X] T002 [P] En `frontend/vite.config.ts` ampliar `test.coverage.include` con `src/features/catalog/catalog-validation.ts` (mantener `thresholds.lines: 90`).

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: Entidad, persistencia, regla de atención, errores, ruta pública y cliente de imágenes que usan las tres historias.

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta terminar esta fase.

- [X] T003 Situación de atención (research §6). Crear `BE/restaurant/model/AttentionStatus.java` (enum `OPEN`, `OUTSIDE_HOURS`, `PAUSED`). En `BE/restaurant/service/OrderEligibility.java` añadir `AttentionStatus attentionStatus(Restaurant restaurant)`: `PAUSED` si `!available`; si no, `OPEN` cuando la hora local (`Clock` y `mikuna.timezone`) cumple `opensAt ≤ hora < closesAt` del día; si no, `OUTSIDE_HOURS` (día cerrado o sin horario también). Reescribir `canReceiveOrders(restaurant, ownerEnabled)` como `ownerEnabled && status == APPROVED && attentionStatus(restaurant) == OPEN`, **con resultado idéntico al actual**. Ampliar la prueba existente de `OrderEligibility` en `BT/restaurant/service/` (o crear `OrderEligibilityTest.java` si no existe) con reloj fijo: pausado en horario → `PAUSED`; disponible dentro, fuera y en día cerrado; borde exacto de apertura (incluido) y cierre (excluido); `canReceiveOrders` sin cambios. Las pruebas de 003 deben seguir verdes.
- [X] T004 [P] Crear `BE/catalog/model/Product.java` (`@Entity`, tabla `productos`) con los campos y restricciones de [data-model.md](./data-model.md): `id` `UUID` PK generado; `restaurantId` `UUID` NOT NULL (`restaurant_id`, columna simple sin `@ManyToOne`); `name` `varchar(100)` NOT NULL, recortado, 2–100; `nameKey` `varchar(100)` NOT NULL (`name_key`, minúsculas y sin tildes, derivado); `description` `varchar(300)` NULL, opcional, ≤ 300; `category` `varchar(50)` NOT NULL, texto libre, 2–50; `categoryKey` `varchar(50)` NOT NULL (`category_key`, derivado como `nameKey`); `price` `numeric(8,2)` NOT NULL, `0.01`–`9999.99`, ≤ 2 decimales (`BigDecimal`); `imageUrl` `varchar(500)` NOT NULL (`image_url`); `imagePublicId` `varchar(200)` NOT NULL (`image_public_id`); `available` `boolean` NOT NULL, `true` al crear; `active` `Boolean` NULL (`true` mientras no está retirado, `NULL` al retirarlo); `withdrawnAt` `Instant` NULL (`withdrawn_at`); `createdAt` NOT NULL (`created_at`, `@PrePersist`); `updatedAt` NOT NULL (`updated_at`, `@PreUpdate`); `@Version long version`. `@Table(uniqueConstraints = @UniqueConstraint(name = "uk_productos_activo", columnNames = {"restaurant_id", "name_key", "category_key", "active"}), indexes = @Index(name = "idx_productos_restaurante", columnList = "restaurant_id, active, available"))`. Constructor protegido para JPA; fábrica `Product.create(restaurantId, name, description, category, price, imageUrl, imagePublicId, now)` que recorta, calcula las claves y deja `available = true` y `active = true`; `update(...)` que recalcula las claves; `changeImage(url, publicId)`; `setAvailable(boolean)`; `withdraw(now)` (`active = null`, `withdrawnAt = now`). Sin setter de `restaurantId`. Normalización de claves: `Normalizer` NFD, quitar marcas diacríticas y `toLowerCase(Locale.ROOT)`.
- [X] T005 Crear `BE/catalog/repository/ProductRepository.java` (`JpaRepository<Product, UUID>`, depende de T004): `Optional<Product> findByIdAndRestaurantIdAndActiveTrue(UUID id, UUID restaurantId)`, `Page<Product> findByRestaurantIdAndActiveTrue(UUID restaurantId, Pageable pageable)` (gestión) y `Page<Product> findByRestaurantIdAndActiveTrueAndAvailableTrue(UUID restaurantId, Pageable pageable)` (público).
- [X] T006 [P] En `BE/common/exception/` crear `ProductNotFoundException` (→ 404, «Producto no encontrado»), `ProductConflictException` (→ 409; mensajes «Ya existe un producto con ese nombre en esa categoría» y «El producto cambió; recarga e inténtalo de nuevo») e `ImageUnavailableException` (→ 503, «El servicio de imágenes no está disponible»), y registrarlas en `GlobalExceptionHandler.java` con el mismo `ProblemDetail` y sin datos internos. En el mismo handler: `ObjectOptimisticLockingFailureException` → 409 con el mensaje de versión desfasada; `MaxUploadSizeExceededException` → 413 «La imagen supera los 2 MB» (sobrescribir el manejo heredado de `ResponseEntityExceptionHandler` si no produce ese texto); `MissingServletRequestPartException` (falta la parte `image` o `product`) → 400 con `errors` del campo (`image`: «La imagen es obligatoria»).
- [X] T007 En `BE/common/security/SecurityConfig.java` añadir, junto a las rutas públicas y antes de las reglas por rol, `.requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()` (decisión del usuario; plan.md, punto 1). Sin otras rutas públicas; las rutas de gestión `/api/restaurant/**` ya exigen `ROLE_RESTAURANT`.
- [X] T008 [P] Crear `BE/catalog/dto/PageResponse.java` (record genérico `PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages)` con método estático `from(org.springframework.data.domain.Page<T>)` y otro `of(List<T> all, int page, int size)` para paginar una lista en memoria).
- [X] T009 [P] Crear `BE/catalog/service/CloudinaryProperties.java` (`@ConfigurationProperties("cloudinary")`, record con `cloudName`, `apiKey`, `apiSecret` y `folder`; registrar con `@EnableConfigurationProperties` en la propia clase de configuración o en `BackendApplication`) y `BE/catalog/service/CloudinaryImageStorage.java` (`@Component`, clase concreta sin interfaz, `RestClient` de Spring): `StoredImage upload(byte[] content, String filename, String contentType)` que hace `POST https://api.cloudinary.com/v1_1/{cloudName}/image/upload` multipart con `file`, `api_key`, `timestamp`, `folder` y `signature` (SHA-1 en hexadecimal de los parámetros firmables ordenados alfabéticamente `folder=…&timestamp=…` seguidos de `api_secret`; **verificar el formato exacto contra la documentación oficial de subida firmada de Cloudinary**) y devuelve `StoredImage(secureUrl, publicId)` (record); `void delete(String publicId)` con `…/image/destroy` firmado (mejor esfuerzo: registra y no propaga el fallo). Sin credenciales o ante cualquier fallo de red o respuesta no 2xx al subir → `ImageUnavailableException`. Constructor con `RestClient.Builder` para poder probarla. Nunca registrar el `api_secret` ni la firma.
- [X] T010 [P] `BT/catalog/service/CloudinaryImageStorageTest.java` (`MockRestServiceServer` sobre el `RestClient.Builder`): la subida envía `api_key`, `timestamp`, `folder` y una firma SHA-1 correcta para un `timestamp` conocido (usar un `Clock` o recibir el momento para que sea determinista); devuelve `secure_url` y `public_id` de la respuesta; respuesta 4xx/5xx, cuerpo sin `secure_url` y credenciales vacías → `ImageUnavailableException`; `delete` envía `public_id` firmado y un fallo del servicio no lanza excepción.
- [X] T011 Actualizar `BT/support/IntegrationTestSupport.java` (depende de T004 y T009): en `cleanDatabase()` borrar `delete from productos` **antes** de `horarios_restaurante`, `restaurantes` y cuentas; declarar `@MockitoBean protected CloudinaryImageStorage imageStorage` (en la clase base para compartir un único contexto) con `upload` devolviendo por defecto `new StoredImage("https://img.test/p.jpg", "mikuna/products/p")`. Crear `BT/support/TestProducts.java` con `approvedRestaurant(ownerEmail, name, category, zone)` (reutilizando `BT/support/TestRestaurants.java` y dejándolo `APPROVED`, `available = true` y con horario 00:00–23:59 todos los días, vía `jdbc`) y `product(restaurantId, name, category, price, available)` que inserta con `jdbc`, además de un `byte[]` mínimo válido de JPEG, PNG y WebP para las pruebas de imagen.

**Checkpoint**: la aplicación compila, las pruebas de 002 y 003 siguen verdes, y la base de productos, la regla de atención, la ruta pública y el cliente de imágenes están listos.

---

## Phase 3: User Story 1 - Administrar el catálogo de productos (Priority: P1) 🎯 MVP

**Goal**: El restaurante aprobado crea, edita, cambia la disponibilidad y retira sus productos con imagen (RF-08).

**Independent Test**: Con un restaurante aprobado, crear un producto con imagen, editarlo, marcarlo no disponible y retirarlo; comprobar lo persistido tras reiniciar, que un duplicado da 409, que una versión desfasada da 409, que un producto ajeno da 404 y que otros roles y estados dan 401/403.

### Tests for User Story 1 (obligatorias)

- [X] T012 [P] [US1] `BT/catalog/service/ImageValidatorTest.java`: acepta JPEG, PNG y WebP válidos (firma real); rechaza un PDF renombrado a `.jpg`, texto con `Content-Type: image/jpeg`, `Content-Type` no admitido (`image/gif`, `application/pdf`), archivo vacío y archivo > 2 MB (2 097 152 bytes; el límite exacto pasa, un byte más falla), con el mensaje de campo `image` correspondiente.
- [X] T013 [P] [US1] `BT/catalog/service/ProductServiceTest.java` (JUnit + Mockito, `ProductRepository`, `RestaurantRepository`, `CloudinaryImageStorage` e `ImageValidator` simulados, reloj fijo): crear deja `available = true`, recorta espacios y usa la URL y el `public_id` devueltos; cada regla de data-model.md falla con su campo (nombre 2–100, categoría 2–50, descripción ≤ 300, precio `0.01`–`9999.99` con ≤ 2 decimales: `0`, negativo, `10000` y `1.234` fallan, `0.01` y `9999.99` pasan); sin restaurante → `RestaurantNotFoundException`; restaurante `PENDING` o `REJECTED` → `RestaurantStateException`; `DataIntegrityViolationException` al guardar → `ProductConflictException` de duplicado **y se borra la imagen recién subida**; falla la subida → `ImageUnavailableException` sin guardar; editar con `version` distinta de la vigente → `ProductConflictException`; editar sin imagen conserva la anterior y editar con imagen nueva borra la anterior tras guardar; `ObjectOptimisticLockingFailureException` → conflicto; producto ajeno, retirado o inexistente → `ProductNotFoundException`; `setAvailability` y `withdraw` exigen la versión vigente; retirar fija `active = null` y no borra la imagen; el servicio ignora cualquier `restaurantId` externo (siempre el del token).
- [X] T014 [P] [US1] `BT/catalog/ProductManagementIntegrationTest.java` (extiende `IntegrationTestSupport`, multipart con `MockMultipartFile`): `POST` → 201 con `available = true`, `imageUrl` y `version`, y el registro existe en BD asociado al restaurante del token; segundo `POST` con mismo nombre y categoría (aun con distinta capitalización o tildes) → 409, misma denominación en otra categoría → 201, y tras retirar el primero el nombre se puede reutilizar; **dos `POST` simultáneos idénticos crean una sola fila (SC-005)**; 400 por cada campo inválido y sin imagen, 400 con imagen falsa, 413 con imagen > 2 MB; 400 si el JSON incluye `restaurantId`, `active`, `available` o `version` al crear; `GET` lista solo los propios no retirados (disponibles o no) con paginación; `GET /{id}` y `PUT /{id}` (con y sin `image`) 200 con nueva `version`; `PUT` con `version` desfasada → 409 y no cambia nada; **dos `PUT` simultáneos con la misma versión: uno 200 y otro 409**; `PUT availability` alterna y exige `version`; `DELETE ?version=` → 204 y luego `GET` 404 y la fila queda con `active` nulo; `DELETE` sin `version` → 400; producto de **otro restaurante** (consultar, editar, disponibilidad, retirar) → 404 sin datos ajenos; restaurante sin solicitud → 404; `PENDING` y `REJECTED` → 403; cuenta restaurante `DISABLED` → 403; cliente, repartidor y administrador → 403; sin token y token manipulado → 401; fallo del almacenamiento de imágenes (mock lanza `ImageUnavailableException`) → 503 sin fila nueva.

### Implementation for User Story 1

- [X] T015 [P] [US1] En `BE/catalog/dto/`: `ProductRequest` (record `name`, `category`, `description`, `price`, `version`) con Bean Validation y mensajes en español: `name` `@NotBlank` `@Size(min = 2, max = 100)`; `category` `@NotBlank` `@Size(min = 2, max = 50)`; `description` `@Size(max = 300)` opcional; `price` `@NotNull` `@DecimalMin("0.01")` `@DecimalMax("9999.99")` `@Digits(integer = 4, fraction = 2)`; `version` `Long` opcional (obligatoria al editar, rechazada al crear en el servicio). `AvailabilityRequest` (`available` `@NotNull Boolean`, `version` `@NotNull Long`) y `ProductResponse` (campos de [catalog-api](./contracts/catalog-api.md) con método `from(Product)`).
- [X] T016 [US1] `BE/catalog/service/ImageValidator.java` (`@Component`): `void validate(MultipartFile file)` y `byte[]` del contenido; rechaza vacío, > 2 097 152 bytes, `Content-Type` fuera de `image/jpeg|png|webp` y firma de archivo que no coincida (JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, WebP `RIFF` + 4 bytes + `WEBP`); el error es `RegistrationValidationException("image", mensaje)` (400 con `errors.image`) con mensajes en español.
- [X] T017 [US1] `BE/catalog/service/ProductService.java` (`@Service`, `@Transactional`; depende de T004, T005, T009, T015, T016): `create(UUID ownerId, ProductRequest, MultipartFile image)`, `list(ownerId, page, size)`, `get(ownerId, id)`, `update(ownerId, id, ProductRequest, MultipartFile imageOrNull)`, `setAvailability(ownerId, id, AvailabilityRequest)` y `withdraw(ownerId, id, Long version)`. Resuelve el restaurante con `RestaurantRepository.findByOwnerId` (404 si no hay; `RestaurantStateException` si no es `APPROVED`); busca los productos con `findByIdAndRestaurantIdAndActiveTrue` (404 si no existe, está retirado o es ajeno); revalida los datos en el servicio además del DTO (constitución II); compara `version` con la vigente antes de modificar y guarda con `saveAndFlush`, traduciendo `DataIntegrityViolationException` y `ObjectOptimisticLockingFailureException` a `ProductConflictException`. Orden de la imagen: validar → subir → guardar; si el guardado falla, `delete` de la recién subida; al sustituir, `delete` de la anterior solo tras guardar. `withdraw` no borra la imagen. Lista ordenada por categoría y nombre.
- [X] T018 [US1] `BE/catalog/controller/ProductController.java` (`@RestController`, `@RequestMapping("/api/restaurant/me/products")`): `POST` (`consumes = multipart/form-data`, `@RequestPart("product") @Valid ProductRequest` y `@RequestPart("image") MultipartFile`; 201), `GET` (`page` ≥ 0 y `size` 1–50 por defecto 0 y 20; 400 si no cumplen), `GET /{id}`, `PUT /{id}` (multipart con `image` opcional), `PUT /{id}/availability` (JSON) y `DELETE /{id}?version=` (204; `version` obligatoria). El titular sale de `@AuthenticationPrincipal AccountPrincipal`; ningún parámetro de identidad del restaurante. Depende de T017.
- [X] T019 [P] [US1] En `FE/features/auth/auth-api.ts` hacer que `apiRequest` **no fuerce** `Content-Type: application/json` cuando `init.body` es `FormData` (el navegador fija el límite del multipart); mantener el comportamiento actual para el resto y no romper `auth-validation.test.ts` ni `session-storage.test.ts`.
- [X] T020 [P] [US1] `FE/features/catalog/catalog-api.ts` (parte de gestión): tipos `Product`, `ProductInput`, `PageResponse<T>`; `listMyProducts(token, page)`, `createProduct(token, input, image)`, `updateProduct(token, id, input, imageOrNull)` (ambos con `FormData`: parte `product` como `Blob` JSON con `type: 'application/json'` y parte `image`), `setProductAvailability(token, id, available, version)` y `withdrawProduct(token, id, version)` sobre `/api/restaurant/me/products`, reutilizando `apiRequest` y `ApiError`. `DELETE` responde 204 sin cuerpo: tratarlo sin intentar leer JSON.
- [X] T021 [P] [US1] `FE/features/catalog/catalog-validation.ts` con `validateProductInput(input)` (mismas reglas de T015: nombre 2–100, categoría 2–50, descripción ≤ 300, precio `0.01`–`9999.99` con ≤ 2 decimales) y `validateImageFile(file)` (JPG/PNG/WebP, ≤ 2 MB, no vacío), ambas devolviendo `Record<string, string>`; y `catalog-validation.test.ts` (Vitest) con un caso válido y uno inválido por regla, incluidos los límites exactos.
- [X] T022 [US1] `FE/features/catalog/ProductsPage.tsx`, `FE/features/catalog/ProductForm.tsx` y `FE/features/catalog/catalog.css`: lista paginada de los productos propios con imagen, nombre, categoría, precio en soles («S/ 18.50»), etiqueta «Disponible» / «No disponible»; formulario de alta y edición con vista previa de la imagen, errores por campo (`errors`) y la imagen obligatoria al crear; interruptor de disponibilidad y botón «Retirar» con confirmación; envía siempre la `version` vigente y ante 409 muestra el mensaje y recarga el producto; maneja 401 (limpiar sesión y redirigir a `/iniciar-sesion`), 403 (aviso de que el restaurante debe estar aprobado), 413, 503 y reintento ante error de red. Estilos coherentes con `features/restaurant/restaurant.css`. Depende de T019–T021.
- [X] T023 [US1] En `FE/App.tsx` añadir la ruta `/mi-restaurante/productos` → `ProductsPage`; en `FE/features/restaurant/RestaurantPage.tsx` mostrar, solo cuando `status === 'APPROVED'`, el enlace «Mi catálogo» a esa ruta.

**Checkpoint**: US1 funciona y se prueba sola (MVP: gestión completa del catálogo con imágenes).

---

## Phase 4: User Story 2 - Buscar restaurantes (Priority: P1)

**Goal**: Cualquier persona, sin sesión, busca restaurantes por nombre, categoría y zona; solo aparecen los aprobados con cuenta habilitada, con su situación de atención (RF-09).

**Independent Test**: Con restaurantes en distintos estados, buscar sin sesión por cada criterio y combinados; comprobar que pendientes, rechazados e inhabilitados no aparecen, que pausados y fuera de horario sí con su aviso, y que la respuesta no expone datos sensibles.

### Tests for User Story 2 (obligatorias)

- [X] T024 [P] [US2] `BT/catalog/service/PublicCatalogServiceTest.java` (Mockito, `RestaurantRepository` simulado, `OrderEligibility` con reloj fijo): `search` sin criterios devuelve los visibles; `name` contiene el texto sin distinguir mayúsculas ni tildes («pollería» ↔ «POLLERIA»); `category` y `zone` por igualdad con el mismo criterio; criterios combinados restringen; sin coincidencias → página vacía; orden: `OPEN` primero y dentro de cada grupo por nombre; `PAUSED` y `OUTSIDE_HOURS` aparecen con su atención; paginación (`page`, `size`, `totalItems`, `totalPages`, página fuera de rango vacía); texto con caracteres especiales (`%`, `_`, `'`) se trata como literal; `filters` devuelve categorías y zonas distintas y ordenadas de los visibles.
- [X] T025 [P] [US2] `BT/catalog/PublicSearchIntegrationTest.java`: **sin token** `GET /api/public/restaurants` → 200 solo con aprobados de cuenta habilitada; pendiente, rechazado y cuenta `DISABLED` no aparecen; pausado y fuera de horario (horario cerrado vía `jdbc`) aparecen con `attention = PAUSED` y `OUTSIDE_HOURS`, y uno abierto con `OPEN` y primero; filtros por `name`, `category` y `zone` y combinados; la respuesta **no contiene** `address`, `phone`, `ownerId`, `status` ni datos de cuenta (aserción sobre las claves del JSON); `GET /filters` solo con valores de restaurantes visibles; `page`/`size` inválidos → 400; un token manipulado en la ruta pública → 401; `POST`/`PUT`/`DELETE` sobre `/api/public/restaurants` sin token → 401.

### Implementation for User Story 2

- [X] T026 [US2] En `BE/restaurant/repository/RestaurantRepository.java` añadir `List<Restaurant> findVisible()` y `Optional<Restaurant> findVisibleById(UUID id)` con JPQL: `select distinct r from Restaurant r left join fetch r.schedule where r.status = 'APPROVED' and exists (select 1 from Account a where a.id = r.ownerId and a.status = 'ENABLED')` (con `and r.id = :id` en la segunda). Sin cambios de esquema. Una prueba de repositorio o la de integración T025 debe cubrir el `fetch` del horario.
- [X] T027 [P] [US2] En `BE/catalog/dto/`: `PublicRestaurantResponse` (`id`, `name`, `category`, `description`, `zone`, `attention` como `AttentionStatus`; método `from(Restaurant, AttentionStatus)`) y `FiltersResponse` (`categories`, `zones`). Nunca incluir dirección, teléfono, `ownerId` ni estado.
- [X] T028 [US2] `BE/catalog/service/PublicCatalogService.java` (`@Service`, `@Transactional(readOnly = true)`; depende de T003, T008, T026, T027): `search(name, category, zone, page, size)` — obtiene `findVisible()`, filtra en memoria con `Normalizer` NFD (nombre: contiene; categoría y zona: igualdad; ignorando mayúsculas y tildes; el texto buscado recortado y limitado a 100 caracteres), calcula `attentionStatus` con `OrderEligibility`, ordena (`OPEN` primero, luego por nombre normalizado) y pagina con `PageResponse.of`; `filters()` — categorías y zonas distintas, ordenadas, de los visibles. `size` máximo 50; `page` < 0 o `size` fuera de rango → `RegistrationValidationException`.
- [X] T029 [US2] `BE/catalog/controller/PublicRestaurantController.java` (`@RestController`, `@RequestMapping("/api/public/restaurants")`): `GET` con `name`, `category`, `zone`, `page` y `size` opcionales y `GET /filters`. Solo `GET`; no recibe `@AuthenticationPrincipal`. Depende de T028.
- [X] T030 [P] [US2] En `FE/features/catalog/catalog-api.ts` añadir (sin token, **sin cabecera `Authorization`**) tipos `PublicRestaurant` y `Attention` (`'OPEN' | 'OUTSIDE_HOURS' | 'PAUSED'`), `searchRestaurants({ name, category, zone, page })` y `getFilters()` sobre `/api/public/restaurants`.
- [X] T031 [US2] `FE/features/catalog/RestaurantSearchPage.tsx` (y estilos en `catalog.css`): caja de búsqueda por nombre y selectores de categoría y zona alimentados por `getFilters()`; criterios, página y limpieza reflejados en la URL (`?q=&categoria=&zona=&pagina=`); tarjetas con nombre, categoría, zona, descripción y etiqueta de atención en español («Abierto», «Fuera de horario», «Sin atención por ahora»); aviso de sin resultados con botón «Limpiar búsqueda»; carga, paginación y reintento ante error de red; accesible sin sesión.
- [X] T032 [US2] En `FE/App.tsx` añadir la ruta `/restaurantes` → `RestaurantSearchPage` y en `FE/features/home/components/SiteHeader.tsx` un enlace «Restaurantes» a `/restaurantes` (sin presentar carrito, pedidos ni pagos como implementados).

**Checkpoint**: US1 y US2 funcionan juntas: el restaurante publica productos y cualquier visitante encuentra el restaurante.

---

## Phase 5: User Story 3 - Consultar el catálogo de un restaurante (Priority: P1)

**Goal**: Cualquier persona consulta los productos disponibles de un restaurante visible con precio, categoría, imagen y su situación de atención; nunca ve retirados ni no disponibles (RF-10).

**Independent Test**: Con un restaurante con productos disponibles, no disponibles y retirados, abrir su catálogo sin sesión y comprobar que solo salen los disponibles; un restaurante no visible responde 404; uno pausado o fuera de horario se ve con su aviso.

### Tests for User Story 3 (obligatorias)

- [X] T033 [P] [US3] Ampliar `BT/catalog/service/PublicCatalogServiceTest.java` con `catalog(restaurantId, page, size)`: restaurante visible devuelve `CatalogResponse` con su atención y los productos del repositorio público; no visible, inexistente → `RestaurantNotFoundException` (mismo resultado en ambos); `PAUSED` y `OUTSIDE_HOURS` se devuelven con su atención y sus productos; paginación y orden por categoría y nombre (verificar el `Pageable` enviado); solo se consultan productos activos y disponibles.
- [X] T034 [P] [US3] `BT/catalog/PublicCatalogIntegrationTest.java`: **sin token** `GET /api/public/restaurants/{id}/catalog` → 200 solo con productos `active` y `available` (insertar disponibles, no disponibles y retirados con `jdbc`), con `name`, `description`, `category`, `price` e `imageUrl` y **sin** `available`, `version`, `restaurantId`, `imagePublicId` ni fechas; orden por categoría y nombre y paginación; restaurante sin productos disponibles → `items` vacío; pendiente, rechazado, cuenta `DISABLED` e id inexistente → el mismo 404; pausado y fuera de horario → 200 con `attention` correcto y sus productos; un producto que se marca no disponible o se retira deja de aparecer en la siguiente consulta; no existe ruta pública de producto por id (`GET /api/public/restaurants/{id}/products/{pid}` → 401 o 404 sin datos); `page`/`size` inválidos → 400; id con formato inválido → 400 o 404 sin error 500.

### Implementation for User Story 3

- [X] T035 [P] [US3] En `BE/catalog/dto/`: `PublicProductResponse` (`id`, `name`, `description`, `category`, `price`, `imageUrl`; método `from(Product)`) y `CatalogResponse` (`restaurant` `PublicRestaurantResponse` y `products` `PageResponse<PublicProductResponse>`).
- [X] T036 [US3] En `BE/catalog/service/PublicCatalogService.java` añadir `catalog(UUID restaurantId, int page, int size)`: `findVisibleById` (vacío → `RestaurantNotFoundException`), atención con `OrderEligibility` y `findByRestaurantIdAndActiveTrueAndAvailableTrue` con `PageRequest` ordenado por `category` y `name` (`size` por defecto 24, máximo 50). Depende de T005, T028 y T035.
- [X] T037 [US3] En `BE/catalog/controller/PublicRestaurantController.java` añadir `GET /{id}/catalog?page=&size=` (`UUID` en la ruta; un id mal formado no debe producir 500). Depende de T036.
- [X] T038 [P] [US3] En `FE/features/catalog/catalog-api.ts` añadir `getCatalog(id, page)` sobre `/api/public/restaurants/{id}/catalog` (sin `Authorization`) y los tipos `CatalogResponse` y `PublicProduct`.
- [X] T039 [US3] `FE/features/catalog/RestaurantCatalogPage.tsx` (y estilos en `catalog.css`): encabezado con nombre, categoría, zona, descripción y etiqueta de atención; si no es `OPEN`, un aviso destacado «Fuera de horario» o «Sin atención por ahora» que indica que no recibe pedidos en este momento; productos agrupados por categoría con imagen, nombre, descripción y precio en soles («S/ 18.50»); aviso de catálogo vacío; paginación; 404 → mensaje «Restaurante no encontrado» con enlace a `/restaurantes`; actualización de la vista al recargar. Añadir la ruta `/restaurantes/:id` en `FE/App.tsx` y enlazar cada tarjeta de `RestaurantSearchPage.tsx` a ella. Sin botones de carrito ni pedido.

**Checkpoint**: las tres historias funcionan de forma independiente y juntas: publicar → buscar → consultar.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T040 [P] Registrar la evidencia en `specs/004-catalogo-consulta/validation.md`: tabla RF-04/RF-08/RF-09/RF-10 y RNF-07/RNF-11 (y SC-006 con los datos locales) con estado (pendiente, parcial o verificado), enlaces a las pruebas, resultado de cobertura (líneas de negocio y ramas) y lo aplazado (E2E, carga RNF-01, caché RNF-03, HTTPS, CI/CD). RF-04 sigue **parcial**; RNF-01, RNF-03, RNF-06 y RNF-12 no se declaran cumplidos.
- [X] T041 Ejecutar `./mvnw verify` en `backend/` y `pnpm test:coverage`, `pnpm build` y `pnpm lint` en `frontend/`: todo en verde, ≥ 90 % de líneas de negocio (la lógica de `catalog` no se excluye de JaCoCo; solo `dto` y `SecurityConfig` como antes) y las pruebas de 002 y 003 siguen verdes (en especial `OrderEligibility`).
- [ ] T042 Ejecutar el recorrido manual de [quickstart.md](./quickstart.md) (backend en 8080, frontend en 5173, con las credenciales de Cloudinary que ya están en `backend/.env`) y anotar el resultado en `validation.md`. No ejecutar Playwright. Comprobar en Cloudinary que las imágenes quedan en `mikuna/products`.
- [X] T043 [P] Revisión final de seguridad y alcance: la única ruta pública nueva es `GET /api/public/**` y ninguna respuesta pública expone dirección, teléfono, `ownerId`, estado de aprobación, datos de cuenta, `imagePublicId` ni `version`; el restaurante propietario siempre sale del token; no se aceptan `restaurantId`, `active` ni `available` del cliente al crear; no hay secretos nuevos en el repositorio (`backend/.env` ignorado, `.env.example` sin valores); no se añadió carrito, pedido ni pago.
- [X] T044 [P] En `specs/002-registro-acceso/contracts/auth-api.md` y `spec.md` añadir una nota breve con enlace a `specs/004-catalogo-consulta/contracts/catalog-api.md` indicando que `GET /api/public/**` es la única ampliación de rutas públicas posterior a 002 (sin reescribir su historia).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup y **bloquea** las historias. Dentro de la fase: T004 → T005; T004 y T009 → T011; T009 → T010; T003, T006, T007 y T008 son independientes entre sí.
- **User Stories (Phase 3+)**: dependen de la fase 2.
- **Polish (Phase 6)**: depende de las historias deseadas.

### User Story Dependencies

- **US1 (P1)**: tras la fase 2; sin dependencias de otras historias.
- **US2 (P1)**: tras la fase 2; sus pruebas insertan restaurantes con `jdbc`, así que no necesita US1 en código. Para el recorrido real conviene tener US1.
- **US3 (P1)**: tras la fase 2 y T026–T029 de US2 (comparten `PublicCatalogService`, el controlador público y los DTO de restaurante); sus pruebas insertan productos con `jdbc`.

### Within Each User Story

- Pruebas primero y en rojo; después DTO → servicio → controlador → frontend.
- `PublicCatalogService.java`, `PublicRestaurantController.java`, `catalog-api.ts`, `catalog.css` y `App.tsx` los modifican varias historias: hacerlo en orden (no en paralelo).

### Parallel Opportunities

- Fase 1: T001 y T002.
- Fase 2: T003, T004, T006, T007, T008 y T009 a la vez; luego T005, T010 y T011.
- US1: T012, T013, T014, T015, T019, T020 y T021 en paralelo.
- US2: T024, T025, T027 y T030 en paralelo.
- US3: T033, T034, T035 y T038 en paralelo.
- El backend y el frontend de una misma historia pueden avanzar a la vez.

---

## Parallel Example: User Story 1

```text
Task: "ImageValidatorTest en BT/catalog/service/ImageValidatorTest.java"                      (T012)
Task: "ProductServiceTest en BT/catalog/service/ProductServiceTest.java"                      (T013)
Task: "ProductManagementIntegrationTest en BT/catalog/ProductManagementIntegrationTest.java"  (T014)
Task: "DTO de producto en BE/catalog/dto/"                                                    (T015)
Task: "apiRequest con FormData en FE/features/auth/auth-api.ts"                               (T019)
Task: "catalog-validation.ts y su prueba en FE/features/catalog/"                             (T021)
```

---

## Implementation Strategy

### MVP First (US1)

1. Setup y Foundational (comprobar que las pruebas de 002 y 003 siguen verdes).
2. US1 completa → **validar**: producto con imagen persistido, 409 por duplicado y por versión, 404 ajeno, 401/403.
3. Sin US2 y US3 el cliente aún no ve nada; el MVP demostrable solo cubre RF-08.

### Incremental Delivery

1. Setup + Foundational → base lista.
2. US1 → probar (RF-08).
3. US2 → probar (RF-09): búsqueda pública con atención.
4. US3 → probar (RF-10): catálogo público.
5. Polish: cobertura, evidencia y recorrido manual con Cloudinary real.

---

## Notes

- El cambio en `OrderEligibility` (T003) es un refactor sin cambio de comportamiento declarado en plan.md; las pruebas de 003 deben seguir verdes.
- Las credenciales de Cloudinary ya están en `backend/.env`; no leerlas ni copiarlas a ningún archivo versionado.
- No hacer commit: el responsable del proyecto los realiza; proponer comandos y mensaje al terminar.
- Evitar: tareas vagas, conflictos de archivo entre tareas [P], declarar RF-04 completo, añadir carrito o pedidos.
