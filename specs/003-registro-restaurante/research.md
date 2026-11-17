# Research: Registro, aprobación y datos del restaurante

Decisiones rutinarias (principio VII), sin comparar alternativas en extenso. Origen: **C** =
constitución, **U** = usuario o spec, **P** = este plan.

## 1. Restaurante como entidad propia

- **Decision (U, spec; P):** tabla `restaurantes` con `owner_id` (UUID de la cuenta) `UNIQUE`.
  `owner_id` se guarda como columna simple, sin `@ManyToOne`, para no acoplar `account` y
  `restaurant`. El estado se guarda como `String` (`PENDING`, `APPROVED`, `REJECTED`), igual que en
  002, para evitar `CHECK` que `ddl-auto=update` no amplía.
- **Rationale:** un restaurante por cuenta; el índice único cubre las solicitudes simultáneas.

## 2. Resolución única bajo concurrencia (C-IV)

- **Decision (P):** `@Modifying @Query("update Restaurant r set r.status = :to, … where r.id = :id
  and r.status = :from")`. Si el recuento es 0, otra resolución ya cambió el estado → 409. El
  reenvío de un rechazado usa el mismo patrón (`REJECTED` → `PENDING`).
- **Rationale:** comprobar y luego escribir no basta (C-IV). La condición en la propia sentencia
  deja un único ganador sin bloqueos en memoria.

## 3. Ediciones concurrentes de datos

- **Decision (P):** `@Version` en `Restaurant`; las actualizaciones condicionales incrementan la
  versión. Una edición que choque con una resolución recibe 409.
- **Rationale:** evita que una edición se pierda sin aviso ante una aprobación simultánea.

## 4. Autorización (RF-04, RNF-07)

- **Decision (P):**
  - `SecurityConfig`: `/api/restaurant/**` → `hasRole("RESTAURANT")`, `/api/admin/**` →
    `hasRole("ADMIN")`. El rol ya llega de la BD vía `AccountStatusFilter`, que también aplica
    401/403 por cuenta inexistente o inhabilitada.
  - Estado del restaurante: el servicio lanza `RestaurantStateException` → 403 si se intenta
    cambiar horario o disponibilidad sin estar aprobado, o reenviar sin estar rechazado.
  - Pertenencia: los endpoints del titular no reciben id; usan `AccountPrincipal.id()` como
    `owner_id`. No existe una ruta para tocar un restaurante ajeno.
  - Estado, titular y aprobación en el cuerpo → 400 por el Jackson estricto de 002.
- **Rationale:** las tres comprobaciones de RF-04 quedan en puntos verificables.

## 5. «Habilitado para recibir pedidos» (RF-06)

- **Decision (P):** `OrderEligibility.canReceiveOrders(restaurant, ownerEnabled)` (la hora sale del `Clock`) es true si el
  estado es `APPROVED`, la cuenta está `ENABLED`, `available` es true y la hora local está en la
  franja del día (`opens ≤ hora < closes`). Se devuelve como `acceptingOrders`; el incremento de
  pedidos reutilizará el servicio.
- **Rationale:** una única regla evita que cada pantalla la reimplemente.
- **Reloj y zona:** se usa el `Clock` de 002 y `mikuna.timezone` (por defecto `America/Lima`) en
  `application.properties`, para poder probar con un reloj fijo.

## 6. Horario

- **Decision (P):** `@ElementCollection` de `OpeningHour(day, opensAt, closesAt)` en la tabla
  `horarios_restaurante`, con `UNIQUE(restaurante_id, day)`. `PUT /me/schedule` reemplaza los siete
  días: abierto exige ambas horas y `closes > opens`; cerrado deja ambas en `NULL`. Una franja por
  día, sin cruce de medianoche (spec).
- **Alternatives considered:** guardar el horario como texto o JSON; se descarta porque la regla de
  habilitación necesita consultarlo.

## 7. Frontend

- **Decision (P):** dos páginas bajo `features/restaurant`, mismo patrón que `features/auth`
  (`fetch` con el token de `session-storage`, errores normalizados). `AccountPage` muestra el
  enlace según el rol. La validación del navegador replica la del backend sin sustituirla.

## 8. Fuera de alcance confirmado

Imágenes (Cloudinary), mapas, notificaciones por correo (RF-29), suspensión posterior (RF-32),
catálogo (RF-08) y búsqueda pública (RF-09/RF-10).
