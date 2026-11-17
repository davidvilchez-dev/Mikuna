# Research: Catálogo y consulta de restaurantes y productos

Decisiones rutinarias (principio VII), sin comparar alternativas en extenso. Origen: **C** =
constitución, **U** = usuario o spec, **P** = este plan.

## 1. Producto como entidad propia

- **Decision (U, spec; P):** tabla `productos` con `restaurant_id` (UUID) como columna simple, sin
  `@ManyToOne`, igual que `owner_id` en 003. Estado de retiro por la columna `active` (ver §3). El
  precio es `BigDecimal` con `numeric(8,2)`.
- **Rationale:** acoplamiento mínimo entre `catalog` y `restaurant`; el retiro es lógico para no
  romper referencias futuras de pedidos (spec FR-005).

## 2. Búsqueda de restaurantes (RF-09)

- **Decision (P):**
  - Una consulta JPQL trae los restaurantes **visibles** (`APPROVED` y cuenta `ENABLED`), con su
    horario en la misma consulta (`left join fetch`) para evitar N+1.
  - El servicio filtra en memoria: nombre (contiene, sin distinguir mayúsculas ni tildes, con
    `Normalizer` NFD), categoría y zona (igualdad sin distinguir mayúsculas ni tildes). Calcula la
    situación de atención, ordena (primero `OPEN`, luego el resto, y por nombre) y pagina.
  - Las opciones de filtro salen de la misma lista (categorías y zonas distintas).
- **Rationale:** el orden «los que atienden primero» depende del horario y la hora actual, que no se
  guardan; no puede resolverse en SQL con paginación. El filtrado sin tildes con SQL exigiría la
  extensión `unaccent` o funciones de texto específicas, y la base la crea el usuario a mano
  (constitución: sin infraestructura adicional). A escala local (cientos de restaurantes) es
  suficiente; optimizar es trabajo de la spec de carga (RNF-01/03 aplazados).
- **Alternativa descartada:** `unaccent` / columna normalizada: añade extensiones o cambia 003.

## 3. Unicidad de productos activos y retiro lógico (constitución IV)

- **Decision (P):** columnas `name_key` y `category_key` (nombre y categoría recortados, en
  minúsculas y sin tildes) y `active` (`Boolean`, `true` mientras está activo, `NULL` al retirarlo).
  Restricción `UNIQUE (restaurant_id, name_key, category_key, active)`. PostgreSQL trata los `NULL`
  como distintos, de modo que los retirados no bloquean un nombre y los activos sí. Un
  `DataIntegrityViolationException` al guardar se traduce a 409 «Ya existe un producto con ese
  nombre en esa categoría».
- **Rationale:** `ddl-auto=update` no crea índices parciales; esta forma usa solo `@UniqueConstraint`.
  Comprobar-y-escribir no basta ante envíos simultáneos.
- **Retiro:** `active = NULL` y `withdrawn_at`; es definitivo (la spec no pide restaurar).

## 4. Ediciones concurrentes (constitución IV)

- **Decision (P):** `@Version` en `Product`. Las operaciones de edición, disponibilidad y retiro
  reciben la `version` que el cliente vio; si no coincide con la vigente → 409 «El producto cambió;
  recarga e inténtalo de nuevo». El `@Version` de JPA cubre además la carrera entre dos peticiones
  con la misma versión (`ObjectOptimisticLockingFailureException` → 409).
- **Rationale:** la spec lo exige (aclaración 4): no se sobrescribe un cambio ajeno.

## 5. Autorización (RF-04, RNF-07)

- **Decision (P):**
  - Gestión: `/api/restaurant/me/products/**` queda cubierto por la regla existente
    `/api/restaurant/** → hasRole("RESTAURANT")`; la cuenta inhabilitada ya responde 403 en
    `AccountStatusFilter`.
  - Estado del restaurante: `ProductService` resuelve `findByOwnerId(token)`; sin restaurante → 404;
    estado ≠ `APPROVED` → `RestaurantStateException` (403).
  - Pertenencia: los productos se buscan siempre con `id` **y** `restaurant_id` propio; uno ajeno es
    indistinguible de uno inexistente (404).
  - Públicas: `GET /api/public/**` en `permitAll`. No existe ninguna ruta pública de escritura. Las
    respuestas públicas usan DTO propios.
- **Nota para el frontend:** un token inválido en una ruta pública produce 401 (el filtro de bearer
  lo valida si llega). Por eso las llamadas públicas **no envían** `Authorization`.

## 6. Situación de atención (spec FR-008)

- **Decision (P):** `OrderEligibility.attentionStatus(restaurant)`:
  `PAUSED` si `available = false`; si no, `OPEN` si la hora local está en la franja del día y
  `OUTSIDE_HOURS` en caso contrario. `canReceiveOrders` pasa a ser
  `ownerEnabled ∧ status = APPROVED ∧ attentionStatus = OPEN`.
- **Rationale:** una sola fuente de verdad que el incremento de pedidos reutilizará. Pausa tiene
  prioridad sobre horario (si el titular pausó, «Sin atención por ahora» aunque esté en horario).

## 6b. Textos de atención

- **Decision (P):** el backend devuelve el código (`OPEN`, `OUTSIDE_HOURS`, `PAUSED`); la interfaz
  muestra «Abierto», «Fuera de horario» y «Sin atención por ahora».

## 7. Imágenes con Cloudinary (RF-08; constitución: integración permitida)

- **Decision (U, P):**
  - Subida multipart (`image`) junto con los datos (`product`, JSON). Validación en el servidor:
    ≤ 2 MB (`spring.servlet.multipart.max-file-size=2MB`), tipo declarado en
    `image/jpeg|png|webp` **y** firma del archivo (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP
    `RIFF…WEBP`).
  - `CloudinaryImageStorage` usa `RestClient` contra
    `https://api.cloudinary.com/v1_1/{cloud}/image/upload` con subida **firmada** (parámetros
    ordenados + `api_secret`, SHA-1) y `…/image/destroy` para borrar. Devuelve `secure_url` y
    `public_id`. Detalles exactos de firma: se verifican contra la documentación oficial al
    implementar.
  - Orden de la operación: subir → guardar en BD → si falla el guardado, borrar la imagen recién
    subida (mejor esfuerzo). Al sustituir la imagen, tras guardar se borra la anterior (mejor
    esfuerzo, el fallo solo se registra). Al retirar un producto no se borra la imagen.
  - Sin configuración (propiedades vacías) la aplicación arranca; subir una imagen responde 503
    «El servicio de imágenes no está disponible» y no se guarda nada.
  - Configuración: `backend/.env` ya contiene `cloudinary.cloud-name`, `cloudinary.api-key` y
    `cloudinary.api-secret`. Como `application.properties` lo importa con `spring.config.import`
    como archivo de propiedades, esas claves se leen tal cual en una clase
    `@ConfigurationProperties("cloudinary")`. No hace falta añadirlas a `application.properties`,
    salvo `cloudinary.folder=mikuna/products` (valor no secreto). Se documentan en `.env.example`
    sin valores reales, con los mismos nombres.
- **Rationale:** sin SDK se evita una dependencia y su árbol; el conjunto de llamadas es de dos
  endpoints. Las pruebas sustituyen la clase con un mock (sin red).
- **Alternativa descartada:** SDK oficial de Cloudinary (dependencia extra para dos llamadas).

## 8. Paginación

- **Decision (P):** `page` (desde 0) y `size` (por defecto 20, máximo 50) en la búsqueda, el catálogo
  público (por defecto 24) y la lista de gestión. Respuesta `PageResponse` con `items`, `page`,
  `size`, `totalItems`, `totalPages`. Los productos se ordenan por categoría y nombre para
  agruparlos en la interfaz.
- **Rationale:** la spec pide páginas ante muchos resultados.

## 9. Validación de datos (FR-002)

- **Decision (P):** Bean Validation en `ProductRequest`: nombre 2–100, categoría 2–50, descripción
  ≤ 300 (opcional), precio `0.01`–`9999.99` con ≤ 2 decimales. Texto recortado antes de validar y
  guardar. Reglas espejo en `catalog-validation.ts` para el formulario; el backend manda.
- **Moneda:** soles peruanos (S/); sin conversión ni símbolo en el dato (solo número).

## 10. Frontend

- **Decision (P):** `apiRequest` omite el `Content-Type` JSON cuando el cuerpo es `FormData`, para que
  el navegador fije el límite del multipart. Las páginas públicas llaman sin token. La búsqueda
  mantiene los criterios en la URL (`?q=&categoria=&zona=&pagina=`) para poder recargar o compartir.
- **Rationale:** cambio mínimo en `apiRequest` y navegación comprensible.
