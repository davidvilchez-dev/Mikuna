---

description: "Tareas de implementación: registro, aprobación y datos del restaurante (RF-05, RF-06, RF-07, RF-04 parcial)"
---

# Tasks: Registro, aprobación y datos del restaurante

**Input**: Documentos de diseño en `specs/003-registro-restaurante/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/restaurant-api.md](./contracts/restaurant-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra
PostgreSQL local (`mikuna_test_db`) y ≥ 90 % de líneas de lógica de negocio. Vitest para la lógica
del frontend. No se usa Playwright ni otra herramienta E2E de navegador.

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

- [X] T001 En `backend/src/main/resources/application.properties` añadir `mikuna.timezone=${MIKUNA_TIMEZONE:America/Lima}` (research §5). En `backend/src/test/resources/application.properties` añadir `mikuna.timezone=America/Lima` (ese archivo sustituye al principal en las pruebas).
- [X] T002 [P] En `frontend/vite.config.ts` ampliar `test.coverage.include` con `src/features/restaurant/restaurant-validation.ts` (mantener `thresholds.lines: 90`).

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: Adaptar 002 (retirar `restaurantApproval`) y crear el modelo, la seguridad y los errores que usan las tres historias.

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta terminar esta fase.

### Adaptación de la spec 002 (data-model.md, «Cambios sobre la spec 002»)

- [X] T003 En `BE/account/model/Account.java` eliminar el campo `restaurantApproval`, su getter, su asignación en el constructor y el import; ajustar el comentario de clase. Borrar `BE/account/model/RestaurantApproval.java`. En `BE/auth/dto/AccountResponse.java` eliminar el componente `restaurantApproval` y su uso en `from`.
- [X] T004 Actualizar las pruebas de 002 que referencian `restaurantApproval`: `BT/account/service/AccountRegistrationServiceTest.java` (líneas ~12, 70, 78, 167: quitar el import y las aserciones), `BT/auth/RegisterIntegrationTest.java` (línea ~71: quitar la aserción; línea ~107: el campo `restaurantApproval` enviado debe seguir dando 400 por campo desconocido), `BT/auth/LoginIntegrationTest.java` (línea ~87) y `BT/auth/service/AuthServiceTest.java` (líneas ~12, 109).
- [X] T005 [P] En `FE/features/auth/auth-api.ts` quitar `restaurantApproval` de `AccountResponse`. En `FE/features/auth/AccountPage.tsx` eliminar la fila «Aprobación comercial» y el aviso `auth-notice` de `restaurantApproval === 'PENDING'`. Revisar `FE/features/auth/RegisterPage.tsx` y quitar cualquier uso (texto de «pendiente» puede mantenerse, pero sin leer el campo); ajustar `FE/features/auth/auth-validation.test.ts` y `session-storage.test.ts` si lo construyen.
- [X] T006 [P] En `specs/002-registro-acceso/contracts/auth-api.md`, `data-model.md` y `spec.md` marcar `restaurantApproval` como retirado por la spec 003 (nota breve con enlace a `specs/003-registro-restaurante/data-model.md`); no reescribir la historia de 002.

### Modelo, seguridad y errores

- [X] T007 [P] Crear en `BE/restaurant/model/`: `RestaurantStatus` (enum `PENDING`, `APPROVED`, `REJECTED`) y `OpeningHour` (`@Embeddable` con `day` `java.time.DayOfWeek` guardado como `String` de `varchar(10)`, `opensAt` `LocalTime` NULL, `closesAt` `LocalTime` NULL; ambos NULL = cerrado).
- [X] T008 Crear `BE/restaurant/model/Restaurant.java` (`@Entity`, tabla `restaurantes`, depende de T007) con los campos y restricciones de data-model.md: `id` UUID PK generado; `ownerId` UUID NOT NULL con `UNIQUE uk_restaurantes_owner` (columna simple, sin `@ManyToOne`); `name` `varchar(100)` NOT NULL; `category` `varchar(50)` NOT NULL; `description` `varchar(300)` NULL; `address` `varchar(200)` NOT NULL; `zone` `varchar(100)` NOT NULL; `phone` `varchar(20)` NOT NULL; `status` `varchar(20)` NOT NULL guardado como `String` (sin `@Enumerated`, para evitar `CHECK`), getter que devuelve `RestaurantStatus`; `submittedAt` `timestamptz` NOT NULL; `rejectionReason` `varchar(300)` NULL; `resolvedBy` UUID NULL; `resolvedAt` `timestamptz` NULL; `available` `boolean` NOT NULL (false al crear); `@Version` `version`; `@ElementCollection(fetch = EAGER)` `@CollectionTable(name = "horarios_restaurante", uniqueConstraints = UNIQUE(restaurante_id, day))` de `OpeningHour`. Fábrica `Restaurant.request(ownerId, name, category, description, address, zone, phone, now)` en `PENDING`; método `updateGeneralData(...)`.
- [X] T009 Crear `BE/restaurant/repository/RestaurantRepository.java` (`JpaRepository<Restaurant, UUID>`, depende de T008): `Optional<Restaurant> findByOwnerId(UUID)`, `List<Restaurant> findByStatusOrderBySubmittedAtAsc(String)`, y dos `@Modifying(clearAutomatically = true, flushAutomatically = true)` con `@Query` JPQL que devuelven `int` (filas afectadas) y suman `r.version = r.version + 1`: `resolve(id, from, to, reason, adminId, now)` (`update … set status=:to, rejectionReason=:reason, resolvedBy=:adminId, resolvedAt=:now, available=false where id=:id and status=:from`) y `resubmit(id, ownerId, now)` (`set status='PENDING', submittedAt=:now, rejectionReason=null, resolvedBy=null, resolvedAt=null where id=:id and ownerId=:ownerId and status='REJECTED'`).
- [X] T010 [P] En `BE/common/exception/` crear `RestaurantNotFoundException` (→ 404, «Aún no tienes una solicitud de restaurante» / «Solicitud no encontrada»), `RestaurantStateException` (→ 403, mensaje con el estado que lo impide) y `RestaurantConflictException` (→ 409, «La solicitud ya fue resuelta o modificada»), y registrar los tres en `GlobalExceptionHandler.java` con el mismo `ProblemDetail`. Mapear también `ObjectOptimisticLockingFailureException` y `DataIntegrityViolationException` de `uk_restaurantes_owner` a `RestaurantConflictException` donde se lancen (en el servicio, no en el handler).
- [X] T011 En `BE/common/security/SecurityConfig.java`, antes de `.anyRequest().authenticated()`, añadir `.requestMatchers("/api/restaurant/**").hasRole("RESTAURANT")` y `.requestMatchers("/api/admin/**").hasRole("ADMIN")` (research §4). Ninguna ruta nueva es pública.
- [X] T012 [P] Crear `BE/restaurant/service/OrderEligibility.java` (`@Component`, usa el `Clock` de `SecurityConfig` y `@Value("${mikuna.timezone}")`) con `boolean canReceiveOrders(Restaurant restaurant, boolean ownerEnabled)`: true solo si `status = APPROVED`, `ownerEnabled`, `available` y la hora local está en la franja del día (`opensAt ≤ hora < closesAt`); un día cerrado o sin horario guardado es false (research §5). Prueba `BT/restaurant/service/OrderEligibilityTest.java` con reloj fijo: pendiente, rechazado, cuenta inhabilitada, no disponible, fuera de horario, día cerrado, sin horario, límites exactos de apertura y cierre, y caso verdadero.
- [X] T013 [P] Crear en `BE/restaurant/dto/`: `OpeningHourDto` (`day`, `opensAt`, `closesAt`) y `RestaurantResponse` (campos de contracts/restaurant-api.md: `id`, `name`, `category`, `description`, `address`, `zone`, `phone`, `status`, `submittedAt`, `rejectionReason`, `resolvedAt`, `available`, `schedule`, `acceptingOrders`; método `from(Restaurant, boolean acceptingOrders)`; el horario se devuelve en orden `MONDAY`…`SUNDAY`). Nunca incluye `ownerId` ni `resolvedBy`.
- [X] T014 En `BT/support/IntegrationTestSupport.java`: en `cleanDatabase()` borrar primero `delete from horarios_restaurante` y `delete from restaurantes` (vía `jdbc`) y después `accounts.deleteAll()`; añadir helpers `restaurantOwner(String email)` (`registrationService.registerPublic("Dueño", email, PASSWORD, PASSWORD, Role.RESTAURANT)`) y `validRestaurantJson()` con datos válidos. En `BT/support/TestAccounts.java` añadir el helper de unidad `restaurant(UUID ownerId, RestaurantStatus status)` si hace falta.

**Checkpoint**: 002 compila y sus pruebas siguen verdes sin `restaurantApproval`; modelo, repositorio, seguridad por rol y regla de habilitación listos.

---

## Phase 3: User Story 1 - Solicitar el registro del restaurante (Priority: P1) 🎯 MVP

**Goal**: El titular envía, consulta y corrige su solicitud; si fue rechazada, la reenvía (RF-05).

**Independent Test**: Con una cuenta de restaurante enviar la solicitud, ver el estado `PENDING`, reiniciar la aplicación y comprobar que persiste; un segundo envío da 409; otros roles dan 403 y sin token 401.

### Tests for User Story 1 (obligatorias)

- [X] T015 [P] [US1] `BT/restaurant/service/RestaurantServiceTest.java` (JUnit + Mockito, `RestaurantRepository` y `AccountRepository` simulados): crear solicitud válida deja `PENDING`, `available=false` y `submittedAt` con el reloj; recorta espacios; cada regla de data-model.md falla con su campo (nombre 2–100, categoría 2–50, descripción ≤ 300, dirección 5–200, zona 2–100, teléfono `+` opcional y 7–15 dígitos o espacios); segunda solicitud del mismo titular → `RestaurantConflictException` (también cuando el guardado lanza `DataIntegrityViolationException`); `getMine` sin solicitud → `RestaurantNotFoundException`; `updateMine` conserva el estado; `resubmit` con 0 filas afectadas → conflicto o `RestaurantStateException` según corresponda (403 si el estado no es `REJECTED`, 409 si cambió entre la lectura y la escritura).
- [X] T016 [P] [US1] `BT/restaurant/RestaurantOwnerIntegrationTest.java` (extiende `IntegrationTestSupport`): `POST /api/restaurant/me` → 201 y `GET` → 200 con `status=PENDING`; persistencia (el registro existe en BD); segundo `POST` → 409; 400 por cada campo inválido; 400 si el cuerpo incluye `status`, `ownerId` o `approved`; `PUT /me` edita y conserva el estado; `GET`/`PUT` sin solicitud → 404; `POST /me/resubmit` sobre `PENDING` → 403 y sobre `REJECTED` (preparado por `jdbc`) → 200 `PENDING` sin motivo; sin token → 401; token manipulado → 401; cliente, repartidor y administrador → 403; cuenta restaurante `DISABLED` → 403; dos `POST` simultáneos de la misma cuenta crean una sola fila (SC-005).

### Implementation for User Story 1

- [X] T017 [P] [US1] `BE/restaurant/dto/RestaurantRequest.java` (record, campos `name`, `category`, `description`, `address`, `zone`, `phone`) con Bean Validation y mensajes en español: `name` `@NotBlank` `@Size(min = 2, max = 100)`; `category` `@NotBlank` `@Size(min = 2, max = 50)`; `description` `@Size(max = 300)` opcional; `address` `@NotBlank` `@Size(min = 5, max = 200)`; `zone` `@NotBlank` `@Size(min = 2, max = 100)`; `phone` `@NotBlank` `@Pattern(regexp = "^\\+?[0-9 ]{7,15}$")`.
- [X] T018 [US1] `BE/restaurant/service/RestaurantService.java` (`@Service`, `@Transactional`): `create(UUID ownerId, RestaurantRequest)` (recorta, guarda con `saveAndFlush`; `DataIntegrityViolationException` → `RestaurantConflictException`), `getMine(ownerId)` (404 si no existe), `updateMine(ownerId, request)` (`ObjectOptimisticLockingFailureException` → conflicto), `resubmit(ownerId)` (si el estado no es `REJECTED` → `RestaurantStateException`; usa `repository.resubmit`, 0 filas → `RestaurantConflictException`). Revalida los datos en el servicio además de en el DTO (constitución II). Todas devuelven `RestaurantResponse` con `acceptingOrders` calculado con `OrderEligibility` y el estado de la cuenta propia leído de `AccountRepository`. Depende de T008, T009, T012, T013, T017.
- [X] T019 [US1] `BE/restaurant/controller/RestaurantController.java` (`@RestController`, `@RequestMapping("/api/restaurant/me")`): `POST` (201), `GET`, `PUT`, `POST /resubmit`; el titular se obtiene de `@AuthenticationPrincipal AccountPrincipal`, sin parámetros de identidad. Depende de T018.
- [X] T020 [P] [US1] `FE/features/restaurant/restaurant-api.ts`: tipos `RestaurantStatus`, `OpeningHour`, `RestaurantResponse` y `RestaurantInput`; funciones `getMyRestaurant`, `submitRestaurant`, `updateMyRestaurant` y `resubmitMyRestaurant` sobre `/api/restaurant/me` con el token de `session-storage`, reutilizando `ApiError` y `GENERIC_ERROR` de `features/auth/auth-api.ts` (extraer a un `request` compartido si hace falta, sin duplicar el manejo de `application/problem+json`).
- [X] T021 [P] [US1] `FE/features/restaurant/restaurant-validation.ts` con `validateRestaurantInput(input)` que replica las reglas de T017 y devuelve `Record<string, string>`, y `FE/features/restaurant/restaurant-validation.test.ts` (Vitest) con un caso válido y uno inválido por regla, incluidos los límites.
- [X] T022 [US1] `FE/features/restaurant/RestaurantPage.tsx` y `FE/features/restaurant/restaurant.css`: si no hay solicitud (404) muestra el formulario de solicitud; si existe, muestra los datos, el estado con etiqueta en español (Pendiente, Aprobado, Rechazado), el motivo de rechazo y el aviso «aún no puedes recibir pedidos» cuando `acceptingOrders` es false; permite editar los datos; con `REJECTED` ofrece «Corregir y reenviar» (guarda y llama a `resubmit`). Maneja carga, errores de campo (`errors`), 401 (limpiar sesión y redirigir a `/iniciar-sesion`), 403 y reintento ante error de red. Estilos coherentes con `features/auth/auth.css`.
- [X] T023 [US1] En `FE/App.tsx` añadir la ruta `/mi-restaurante` → `RestaurantPage`; en `FE/features/auth/AccountPage.tsx` mostrar, solo si `account.role === 'RESTAURANT'`, un enlace «Mi restaurante» a `/mi-restaurante` (depende de T005 y T022).

**Checkpoint**: US1 funciona y se prueba sola (MVP: solicitud, consulta, corrección y reenvío).

---

## Phase 4: User Story 2 - Revisar y resolver solicitudes (Priority: P1)

**Goal**: El administrador lista las pendientes y las aprueba o rechaza con motivo; solo prospera una resolución (RF-06).

**Independent Test**: Con varias solicitudes pendientes, aprobar una y rechazar otra; comprobar el estado persistido, que una segunda resolución da 409 y que otros roles no acceden.

### Tests for User Story 2 (obligatorias)

- [X] T024 [P] [US2] `BT/restaurant/service/RestaurantReviewServiceTest.java` (Mockito): `list(PENDING)` ordena por `submittedAt` ascendente y añade nombre y correo del titular; estado desconocido → error de validación; `approve` llama a `resolve(PENDING→APPROVED)` con el id y la fecha del reloj y deja `available=false`; `reject` exige motivo no vacío de hasta 300 («El motivo es obligatorio y admite hasta 300 caracteres»); 0 filas afectadas → `RestaurantConflictException`; id inexistente → `RestaurantNotFoundException`.
- [X] T025 [P] [US2] `BT/restaurant/RestaurantAdminIntegrationTest.java`: lista solo pendientes por antigüedad y vacía → `[]`; `approve` → 200 `APPROVED` con `resolvedAt` y `resolvedBy` (comprobar en BD), titular ve `APPROVED` y `available=false`; `reject` sin motivo → 400, con motivo → 200 y el titular ve `rejectionReason`; resolver una ya resuelta → 409; **dos `approve`/`reject` simultáneos de la misma solicitud: exactamente uno 200 y otro 409, estado final único** (SC-005); tras `resubmit` del titular reaparece en la lista; id desconocido → 404; sin token → 401; cliente, repartidor y restaurante → 403; administrador `DISABLED` → 403; cuerpo con campos desconocidos → 400.

### Implementation for User Story 2

- [X] T026 [P] [US2] `BE/restaurant/dto/RejectRequest.java` (`reason` con `@NotBlank` y `@Size(max = 300)`) y `BE/restaurant/dto/AdminRestaurantResponse.java` (campos de `RestaurantResponse` más `ownerName` y `ownerEmail`).
- [X] T027 [US2] `BE/restaurant/service/RestaurantReviewService.java` (`@Service`, `@Transactional`): `list(RestaurantStatus status)` (por defecto `PENDING`; une el titular con `AccountRepository`), `approve(adminId, restaurantId)` y `reject(adminId, restaurantId, reason)` mediante `repository.resolve` (data-model.md: solo `PENDING→APPROVED` y `PENDING→REJECTED`); 0 filas → distinguir 404 (no existe) de 409 (ya no está pendiente). Depende de T009, T026.
- [X] T028 [US2] `BE/restaurant/controller/RestaurantAdminController.java` (`@RequestMapping("/api/admin/restaurants")`): `GET ?status=`, `POST /{id}/approve`, `POST /{id}/reject`; el administrador sale del `AccountPrincipal`. Un valor de `status` inválido → 400. Depende de T027.
- [X] T029 [P] [US2] En `FE/features/restaurant/restaurant-api.ts` añadir `listRestaurants(status)`, `approveRestaurant(id)` y `rejectRestaurant(id, reason)` y el tipo `AdminRestaurant`; en `restaurant-validation.ts` añadir `validateRejectReason` (obligatorio, ≤ 300) y casos en `restaurant-validation.test.ts`.
- [X] T030 [US2] `FE/features/restaurant/AdminRequestsPage.tsx`: lista de pendientes (más antigua primero) con nombre, categoría, zona, dirección, teléfono, titular y fecha; aviso de lista vacía; botón «Aprobar» y «Rechazar» (pide motivo, valida ≤ 300); tras resolver retira la tarjeta y muestra el resultado; ante 409 informa que otra persona ya la resolvió y recarga; maneja 401/403 como `RestaurantPage`. Añadir la ruta `/admin/solicitudes` en `FE/App.tsx` y, en `AccountPage.tsx`, el enlace «Solicitudes de restaurantes» solo si `role === 'ADMIN'`.

**Checkpoint**: US1 y US2 funcionan juntas: solicitud → revisión → resultado visible para el titular.

---

## Phase 5: User Story 3 - Administrar datos, horarios y disponibilidad (Priority: P2)

**Goal**: El restaurante aprobado define su horario semanal y pausa o reanuda su disponibilidad; la habilitación para pedidos se refleja (RF-07, RF-06).

**Independent Test**: Con un restaurante aprobado, guardar el horario, activar y pausar la disponibilidad y comprobar `acceptingOrders`; un pendiente o rechazado recibe 403.

### Tests for User Story 3 (obligatorias)

- [X] T031 [P] [US3] Ampliar `BT/restaurant/service/RestaurantServiceTest.java` con `updateSchedule` (siete días sin repetir; abierto exige ambas horas con `closesAt > opensAt`; cerrado exige ambas nulas; días repetidos o faltantes y `closesAt ≤ opensAt` fallan; un estado distinto de `APPROVED` → `RestaurantStateException`) y `setAvailability` (solo `APPROVED`; el valor se guarda y `acceptingOrders` cambia con el reloj fijo).
- [X] T032 [P] [US3] `BT/restaurant/RestaurantScheduleIntegrationTest.java`: restaurante aprobado (aprobar con `jdbc`) guarda el horario y `GET /me` lo devuelve en orden; horario inválido → 400 y se conserva el anterior; `PUT availability` activa y pausa; recién aprobado empieza con `available=false`; `PENDING` y `REJECTED` → 403 en `schedule` y `availability`; editar datos de un aprobado conserva `APPROVED`; cuenta inhabilitada → 403 y `acceptingOrders=false` en la base para ese titular; roles ajenos 403 y sin token 401.

### Implementation for User Story 3

- [X] T033 [P] [US3] `BE/restaurant/dto/ScheduleRequest.java` (`days`: lista de `OpeningHourDto` con `@NotNull` y `@Size(min = 7, max = 7)`) y `BE/restaurant/dto/AvailabilityRequest.java` (`available` `@NotNull Boolean`).
- [X] T034 [US3] En `BE/restaurant/service/RestaurantService.java` añadir `updateSchedule(ownerId, ScheduleRequest)` y `setAvailability(ownerId, AvailabilityRequest)`: exigen `APPROVED` (`RestaurantStateException` → 403), validan en el servicio los siete días distintos, `HH:mm` y `closesAt > opensAt` (cerrado: ambas nulas), reemplazan el horario completo y devuelven `RestaurantResponse`. Depende de T033.
- [X] T035 [US3] En `BE/restaurant/controller/RestaurantController.java` añadir `PUT /schedule` y `PUT /availability`. Depende de T034.
- [X] T036 [P] [US3] En `FE/features/restaurant/restaurant-api.ts` añadir `updateSchedule(days)` y `setAvailability(available)`; en `restaurant-validation.ts` añadir `validateSchedule(days)` (siete días, abierto con ambas horas, cierre posterior a apertura) con casos en `restaurant-validation.test.ts`.
- [X] T037 [US3] En `FE/features/restaurant/RestaurantPage.tsx` añadir, solo cuando `status === 'APPROVED'`, el editor del horario semanal (un renglón por día con apertura, cierre y casilla «Cerrado») y el interruptor de disponibilidad; mostrar `acceptingOrders` («Recibiendo pedidos» / «No recibes pedidos ahora» con la razón más probable); mostrar los mismos bloques como no editables con el aviso correspondiente para `PENDING` y `REJECTED`.

**Checkpoint**: las tres historias funcionan de forma independiente y juntas.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T038 [P] Registrar la evidencia en `specs/003-registro-restaurante/validation.md`: tabla RF-04/RF-05/RF-06/RF-07 y RNF-07/RNF-11 con estado (pendiente, parcial o verificado), enlaces a las pruebas, resultado de cobertura (líneas de negocio y ramas) y lo aplazado (E2E, carga, HTTPS, CI/CD). RF-04 sigue **parcial**; RNF-06/08/12 no se declaran cumplidos.
- [X] T039 Ejecutar `./mvnw verify` en `backend/` y `pnpm test:coverage` en `frontend/`: todo en verde, ≥ 90 % de líneas de negocio (la lógica de `restaurant` no se excluye de JaCoCo; sólo `dto` y `SecurityConfig` como en 002) y sin referencias residuales a `restaurantApproval` (`grep` en `backend/src`, `frontend/src` y specs vigentes).
- [ ] T040 Ejecutar el recorrido manual de [quickstart.md](./quickstart.md) (backend en 8080, frontend en 5173) y anotar el resultado en `validation.md`. No ejecutar Playwright.
- [X] T041 [P] Revisión final de seguridad y alcance: ningún endpoint nuevo es público, ninguna respuesta expone `ownerId` ni `resolvedBy`, no se aceptan `status`/`ownerId`/`approved` del cliente, no hay secretos nuevos; el inicio y `/mi-cuenta` siguen operativos sin sesión según corresponde.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup y **bloquea** las historias. Dentro de la fase: T003 → T004 y T005 (T006 es libre); T007 → T008 → T009; T010, T011 y T012 dependen de T008; T013 depende de T008 y T012; T014 de T008 y T003.
- **User Stories (Phase 3+)**: dependen de la fase 2.
- **Polish (Phase 6)**: depende de las historias deseadas.

### User Story Dependencies

- **US1 (P1)**: tras la fase 2; sin dependencias de otras historias.
- **US2 (P1)**: tras la fase 2; sus pruebas de integración usan `resubmit` de US1 solo en un caso, por lo que conviene terminar US1 antes.
- **US3 (P2)**: tras la fase 2; sus pruebas preparan restaurantes aprobados con `jdbc`, pero el flujo real requiere US2 para aprobar.

### Within Each User Story

- Pruebas primero y en rojo; después DTO → servicio → controlador → frontend.
- `RestaurantService.java` y `RestaurantController.java` los modifican US1 y US3: hacerlo en orden (no en paralelo). Lo mismo con `RestaurantPage.tsx`, `restaurant-api.ts` y `restaurant-validation.ts` entre historias.

### Parallel Opportunities

- Fase 1: T001 y T002.
- Fase 2: T005 y T006 con el resto tras T003; T007, T010 y T012–T013 según dependencias.
- US1: T015, T016, T017, T020 y T021 en paralelo.
- US2: T024, T025, T026 y T029 en paralelo.
- US3: T031, T032, T033 y T036 en paralelo.
- El backend y el frontend de una misma historia pueden avanzar a la vez.

---

## Parallel Example: User Story 1

```text
Task: "RestaurantServiceTest en BT/restaurant/service/RestaurantServiceTest.java"            (T015)
Task: "RestaurantOwnerIntegrationTest en BT/restaurant/RestaurantOwnerIntegrationTest.java"  (T016)
Task: "RestaurantRequest en BE/restaurant/dto/RestaurantRequest.java"                        (T017)
Task: "restaurant-api.ts en FE/features/restaurant/restaurant-api.ts"                        (T020)
Task: "restaurant-validation.ts y su prueba en FE/features/restaurant/"                      (T021)
```

---

## Implementation Strategy

### MVP First (US1)

1. Setup y Foundational (adaptar 002 y comprobar que sus pruebas siguen verdes).
2. US1 completa → **validar**: solicitud pendiente persistida, 409 al repetir, 401/403.
3. Sin US2 no hay aprobación; el MVP demostrable solo cubre RF-05.

### Incremental Delivery

1. Setup + Foundational → base lista.
2. US1 → probar (RF-05).
3. US2 → probar (RF-06): flujo completo de aprobación.
4. US3 → probar (RF-07): horarios y disponibilidad, con `acceptingOrders`.
5. Polish: cobertura, evidencia y recorrido manual.

---

## Notes

- Los cambios de 002 (T003–T006) son una adaptación necesaria declarada en plan.md; no ampliar más allá.
- La columna `restaurant_approval` de bases locales existentes no se elimina.
- No hacer commit: el responsable del proyecto los realiza; proponer comandos y mensaje al terminar.
- Evitar: tareas vagas, conflictos de archivo entre tareas [P], declarar RF-04 completo.
