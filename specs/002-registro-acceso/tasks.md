---

description: "Tareas de implementación: registro e inicio de sesión de Mikuna (RF-01, RF-04 parcial)"
---

# Tasks: Registro e inicio de sesión de Mikuna

**Input**: Documentos de diseño en `specs/002-registro-acceso/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **Obligatorias**. La constitución (V) y la spec exigen JUnit + Mockito, integración
acotada contra PostgreSQL y ≥ 90 % de líneas de lógica de negocio (RNF-11). No se usa Playwright ni
otra herramienta E2E de navegador.

**Organization**: Tareas agrupadas por historia de usuario; cada historia se prueba por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3 según [spec.md](./spec.md)
- Paquete base del backend: `backend/src/main/java/com/mikuna/backend/` (abreviado `BE/`).
  Pruebas: `backend/src/test/java/com/mikuna/backend/` (abreviado `BT/`).

---

## Phase 1: Setup (infraestructura compartida)

**Purpose**: Dependencias y configuración que exigen el plan y research.

- [X] T001 En `backend/pom.xml`: eliminar `spring-boot-starter-flyway`, `flyway-database-postgresql` y `spring-boot-starter-flyway-test` (discrepancia 1 del plan). Añadir `spring-boot-starter-security-oauth2-resource-server` y, en test, `spring-boot-starter-security-oauth2-resource-server-test`. Añadir el plugin `org.jacoco:jacoco-maven-plugin:0.8.14` con los goals `prepare-agent` y `report` (fase `verify`), excluyendo `**/BackendApplication.class`, `**/dto/**` y `**/common/security/SecurityConfig.class` (research §11).
- [X] T002 En `backend/src/main/resources/application.properties` ya están `spring.config.import` de `backend/.env` y el datasource con `${DB_URL}`, `${DB_USERNAME}` y `${DB_PASSWORD}`; conservarlos. Añadir: `mikuna.security.jwt.secret=${MIKUNA_JWT_SECRET}`, `mikuna.security.jwt.expiration=${MIKUNA_JWT_EXPIRATION:PT2H}`, `spring.jackson.deserialization.fail-on-unknown-properties=true`, `spring.mvc.problemdetails.enabled=true`, `management.endpoints.web.exposure.include=health` y `spring.jpa.open-in-view=false`.
- [X] T003 [P] Crear `backend/src/test/resources/application.properties`. Sustituye por completo al archivo principal en las pruebas, así que debe repetir `spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]`. Además: datasource `jdbc:postgresql://localhost:5432/mikuna_test_db` con `${DB_USERNAME:postgres}` y `${DB_PASSWORD}`, `spring.jpa.hibernate.ddl-auto=create-drop`, `mikuna.security.jwt.secret=test-secret-solo-para-pruebas-locales-32b+` (fijo, para no depender del secreto real) y `mikuna.security.jwt.expiration=PT2H`.
- [X] T004 [P] En `frontend/`, ejecutar `pnpm add react-router` y `pnpm add -D vitest @vitest/coverage-v8 jsdom`. Añadir a `frontend/package.json` los scripts `"test": "vitest run"` y `"test:coverage": "vitest run --coverage"`. No tocar los scripts `test:e2e*` (discrepancia 2 del plan).
- [X] T005 [P] En `frontend/vite.config.ts`, añadir `server.proxy` con `'/api'` → `http://localhost:8080` y el bloque `test` de Vitest: `environment: 'jsdom'`, `include: ['src/**/*.test.ts']`, coverage `v8` con `include: ['src/features/auth/auth-validation.ts', 'src/features/auth/session-storage.ts']` y `thresholds.lines: 90`. Excluir `tests/e2e/**` para que Vitest no recoja Playwright.

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: Entidad, persistencia, seguridad base, errores y enrutado. Bloquea US1–US3.

- [X] T006 [P] Crear los enums en `BE/account/model/`: `Role.java` (`CUSTOMER, RESTAURANT, COURIER, ADMIN`), `AccountStatus.java` (`ENABLED, DISABLED`) y `RestaurantApproval.java` (solo `PENDING`; APPROVED y REJECTED se añadirán con RF-06).
- [X] T007 Crear la entidad JPA `BE/account/model/Account.java` sobre la tabla `cuentas`, según [data-model.md](./data-model.md):
  - `id` `UUID` PK generado.
  - `name` `varchar(100)` NOT NULL.
  - `email` `varchar(254)` NOT NULL con `@Table(uniqueConstraints = @UniqueConstraint(name = "uk_cuentas_email", columnNames = "email"))`.
  - `passwordHash` `varchar(255)` NOT NULL.
  - `role` `varchar(20)` NOT NULL.
  - `status` `varchar(20)` NOT NULL.
  - `restaurantApproval` `varchar(20)` NULL.
  - Las tres columnas de enum se guardan como `String` (`@Column(length = 20)`) y los getters devuelven el enum. Hibernate 7 genera un `CHECK` con `@Enumerated`, con conversores y con `columnDefinition`, y `ddl-auto=update` no puede ampliarlo (data-model.md; comprobado durante la implementación).
  - `createdAt` `timestamptz` NOT NULL, asignado en `@PrePersist`.
  - Constructor protegido para JPA y un método de fábrica `Account.create(name, email, passwordHash, role)` que fija `status = ENABLED` y `restaurantApproval = PENDING` solo si `role == RESTAURANT`. Sin setter de `role` (depende de T006).
- [X] T008 [P] Crear `BE/account/repository/AccountRepository.java` (`JpaRepository<Account, UUID>`) con `Optional<Account> findByEmail(String email)` y `boolean existsByEmail(String email)`.
- [X] T009 [P] Crear `BE/account/service/EmailNormalizer.java`: un método estático `normalize(String)` que devuelve `trim().toLowerCase(Locale.ROOT)` y `null` si recibe `null` (research §5).
- [X] T010 [P] Crear `BE/common/exception/` con:
  - `EmailAlreadyRegisteredException`, `InvalidCredentialsException`, `AccountDisabledException` y `RegistrationValidationException(String field, String message)`, todas sin datos sensibles en el mensaje.
  - `GlobalExceptionHandler` (`@RestControllerAdvice`) que devuelve `ProblemDetail`:
    - `MethodArgumentNotValidException` → 400 con la propiedad `errors` (campo → mensaje).
    - `HttpMessageNotReadableException` (campo desconocido o enum inválido) → 400 «Solicitud inválida».
    - `RegistrationValidationException` → 400 con `errors: {field: message}`.
    - `EmailAlreadyRegisteredException` → 409 «El correo ya está registrado». No hay mapeo global de `DataIntegrityViolationException`: solo la traduce el servicio de registro (T020).
    - `InvalidCredentialsException` → 401 «Correo o contraseña incorrectos».
    - `AccountDisabledException` → 403 «La cuenta no está habilitada».
    - `Exception` → 500 genérico, sin traza en el cuerpo.
- [X] T011 Crear `BE/common/security/SecurityConfig.java`:
  - `SecurityFilterChain` sin estado (`SessionCreationPolicy.STATELESS`), con CSRF, formLogin y httpBasic deshabilitados.
  - `permitAll` para `POST /api/auth/register`, `POST /api/auth/login` y `GET /actuator/health`; `authenticated()` para el resto.
  - `oauth2ResourceServer(jwt)`, con `authenticationEntryPoint` y `accessDeniedHandler` que escriben `ProblemDetail` 401/403.
  - Beans:
    - `PasswordEncoder` = `DelegatingPasswordEncoder` con el id `pbkdf2@SpringSecurity_v5_8` → `Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (research §4).
    - `JwtEncoder` (`NimbusJwtEncoder` con `ImmutableSecret`) y `JwtDecoder` (`NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`), ambos a partir de `mikuna.security.jwt.secret`. Lanzar `IllegalStateException` al arrancar si el secreto mide menos de 32 bytes.
- [X] T012 [P] Crear `frontend/src/features/auth/auth-api.ts`:
  - Tipos `Role`, `AccountStatus`, `AccountResponse`, `LoginResponse` y `ApiError` (`status`, `detail` y `errors?: Record<string,string>`).
  - Una función interna `request()` sobre `fetch('/api/auth/...')`:
    - Un `ProblemDetail` (`application/problem+json`) se convierte en `ApiError`.
    - Una respuesta de error que no sea `application/problem+json` (por ejemplo, el 500/502 en texto plano del proxy de Vite) se convierte en `ApiError{status, detail:'No se pudo completar la operación, inténtalo de nuevo'}`, sin intentar leerla como JSON.
    - Un fallo de red se convierte en el mismo mensaje con `status: 0`.
- [X] T013 [P] Crear `frontend/src/features/auth/session-storage.ts` con `saveSession({token, expiresAt})`, `getSession()` (devuelve `null` y borra si `expiresAt <= Date.now()` o si el JSON no es válido), `clearSession()` y `hasActiveSession()`. Usar `sessionStorage` con la clave `mikuna.session` (research §10).
- [X] T014 [P] Crear `frontend/src/features/auth/session-storage.test.ts` (Vitest): guarda y recupera; descarta y borra una sesión vencida; descarta un JSON corrupto; `clearSession` vacía.
- [X] T015 Envolver la app en `<BrowserRouter>` en `frontend/src/main.tsx`. En `frontend/src/App.tsx`, declarar `<Routes>`: `/` → `HomePage`; `/registro`, `/iniciar-sesion` y `/mi-cuenta` → páginas provisionales que se sustituyen en US1 y US2; `*` → redirección a `/`.

**Checkpoint**: `.\mvnw compile` y `pnpm build` pasan; la tabla `cuentas` se crea al arrancar el backend.

---

## Phase 3: User Story 1 - Crear una cuenta desde el inicio (Priority: P1) 🎯 MVP

**Goal**: Registrar cuentas de cliente, restaurante y repartidor desde el inicio, guardadas en
PostgreSQL (RF-01).

**Independent Test**: Desde el inicio, registrar los tres tipos; repetir un correo (en mayúsculas o
con otro rol) da 409; reiniciar el backend y comprobar en la BD que las cuentas siguen ahí
(quickstart filas 1–4 y 9).

### Tests for User Story 1

- [X] T016 [P] [US1] Crear `BT/account/service/AccountRegistrationServiceTest.java` (Mockito):
  - Normaliza el correo antes de guardar.
  - Guarda el hash, nunca la contraseña en claro.
  - `RESTAURANT` queda con `restaurantApproval=PENDING` y `CUSTOMER`/`COURIER` con `null`.
  - Si las contraseñas no coinciden → excepción.
  - Si `existsByEmail` es true → `EmailAlreadyRegisteredException`.
  - Si `save` lanza `DataIntegrityViolationException` → `EmailAlreadyRegisteredException`.
  - Registrar `ADMIN` por la vía pública → rechazado.
- [X] T017 [P] [US1] Crear `BT/account/service/EmailNormalizerTest.java`: recorta los espacios, pasa a minúsculas y devuelve `null` con `null`.
- [X] T018 [P] [US1] Crear `BT/auth/RegisterIntegrationTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc`, base `mikuna_test_db`; limpiar el repositorio en `@BeforeEach`). Casos:
  - 201 para los tres roles; el cuerpo no contiene `password` ni `passwordHash`.
  - 400 por nombre vacío o de 101 caracteres, correo inválido o de 255 caracteres, contraseña de 11 o de 129 caracteres, y confirmación distinta.
  - 400 con `role: "ADMIN"`, con un campo extra `status` o con `restaurantApproval`.
  - 409 al repetir el correo en mayúsculas o con espacios, y al repetirlo con otro rol. En ambos casos sigue habiendo una sola fila y el rol no cambia.
  - Concurrencia: 2 hilos que registran el mismo correo a la vez con `CountDownLatch` → exactamente un 201, un 409 y `count()==1` (SC-004).

### Implementation for User Story 1

- [X] T019 [P] [US1] Crear los records en `BE/auth/dto/`:
  - `RegisterRequest(name, email, password, passwordConfirmation, role)`, con estas validaciones:
    - `@NotBlank @Size(max=100) name`.
    - `@NotBlank @Size(max=254) @Email(regexp=".+@.+\\..+") email`.
    - `@NotNull @Size(min=12,max=128) password`.
    - `@NotNull passwordConfirmation`.
    - `@NotNull Role role`.
  - `AccountResponse(UUID id, String name, String email, Role role, AccountStatus status, RestaurantApproval restaurantApproval)`, con el método estático `from(Account)`.
- [X] T020 [US1] Crear `BE/account/service/AccountRegistrationService.java` (`@Transactional`), con dos métodos:
  - `registerPublic(name, email, password, confirmation, role)`:
    - Todas las validaciones del servicio lanzan `RegistrationValidationException` con su campo (400 con `errors`). Primera: `role == ADMIN` o `null` → campo `role`.
    - Recorta `name` y valida que no esté vacío y tenga ≤ 100 caracteres.
    - Normaliza el correo con `EmailNormalizer` y valida que tenga ≤ 254.
    - Valida que la contraseña tenga 12–128 caracteres y coincida con la confirmación. *(Cambio posterior, 2026-11-17: mínimo suspendido a 1 por decisión del usuario.)*
    - Si `existsByEmail` → `EmailAlreadyRegisteredException`.
    - Crea la cuenta con `Account.create` y el hash de `PasswordEncoder`, y hace `saveAndFlush`. Si salta `DataIntegrityViolationException` → `EmailAlreadyRegisteredException`.
  - `createAdmin(name, email, password)`: package-private o público con la misma validación, solo para T045.

  Depende de T007–T010.
- [X] T021 [US1] Crear `BE/auth/controller/AuthController.java` (`@RestController @RequestMapping("/api/auth")`) con `POST /register` (`@Valid @RequestBody RegisterRequest`) → `ResponseEntity.status(201).body(AccountResponse.from(...))`.
- [X] T022 [P] [US1] Crear `frontend/src/features/auth/auth-validation.ts`, con las mismas reglas que el backend:
  - `validateRegistration({name,email,password,passwordConfirmation,role})` → `Record<campo, mensaje>` en español: nombre obligatorio y ≤ 100; correo con formato `.+@.+\..+` y ≤ 254; contraseña de 12–128; confirmación coincidente; rol en `CUSTOMER|RESTAURANT|COURIER`.
  - `validateLogin({email,password})`.
  - `roleFromQuery(tipo)`: `cliente` → CUSTOMER, `restaurante` → RESTAURANT, `repartidor` → COURIER; cualquier otro valor → CUSTOMER.
- [X] T023 [P] [US1] Crear `frontend/src/features/auth/auth-validation.test.ts` (Vitest) con cada límite (100/101, 254/255, 11/12/128/129), confirmación distinta, rol inválido y `roleFromQuery` con los valores válidos y uno desconocido.
- [X] T024 [US1] Añadir `register(data): Promise<AccountResponse>` en `frontend/src/features/auth/auth-api.ts`.
- [X] T025 [US1] Crear `frontend/src/features/auth/RegisterPage.tsx` y `frontend/src/features/auth/auth.css`, según [contracts/ui-routes.md](./contracts/ui-routes.md):
  - Selector visible de tipo (Cliente, Restaurante, Repartidor) preseleccionado con `roleFromQuery(searchParams.get('tipo'))`.
  - Campos nombre, correo, contraseña y confirmación con `<label>`, y errores por campo con `aria-describedby`.
  - Validación local antes del envío y botón deshabilitado mientras se envía.
  - Muestra los `errors` y `detail` del backend.
  - Si el registro es correcto, mensaje de confirmación con un `<Link to="/iniciar-sesion">`; para RESTAURANT, el aviso «Tu habilitación comercial está pendiente de aprobación».
  - `<Link to="/">` para volver al inicio.
  - Estética coherente con las clases `mikuna-*` de Tailwind del inicio.
- [X] T026 [US1] Registrar la ruta `/registro` → `RegisterPage` en `frontend/src/App.tsx`.
- [X] T027 [US1] Conectar los puntos de entrada de registro (las secciones del inicio no cambian):
  - En `frontend/src/features/home/home-types.ts`, ampliar `ActionItem` con `behavior: 'route'` y `to?: string`.
  - En `frontend/src/features/home/home-content.ts`, `crear-cuenta` pasa a `{behavior:'route', to:'/registro'}`.
  - `frontend/src/features/home/components/HeroSection.tsx` renderiza `<Link>` cuando `behavior==='route'`.
  - En `frontend/src/features/home/components/SiteFooter.tsx`, «Crear cuenta» pasa a `<Link to="/registro">`.
  - En `frontend/src/features/public-pages/ForRestaurantsPage.tsx`, «Registrar mi restaurante» pasa a `<Link to="/registro?tipo=restaurante">`.
  - En `frontend/src/features/public-pages/CourierPage.tsx`, «Quiero ser repartidor» y «Quiero sumarme» pasan a `<Link to="/registro?tipo=repartidor">`.
  - Se conservan las clases CSS existentes.

**Checkpoint**: US1 funciona por sí sola: T016–T018 y T023 en verde; registro manual de los tres tipos.

---

## Phase 4: User Story 2 - Iniciar y cerrar sesión (Priority: P1)

**Goal**: Login de los cuatro roles con un token JWT que vence, vista de la sesión propia y cierre
de sesión (RF-01).

**Independent Test**: Con cuentas existentes (y un ADMIN insertado en la BD de pruebas), iniciar
sesión, ver nombre, rol y estado en `/mi-cuenta`, y cerrar sesión (quickstart filas 5–7).

### Tests for User Story 2

- [X] T028 [P] [US2] Crear `BT/auth/service/TokenServiceTest.java`: el token emitido contiene `sub`=id, `role` y `exp` = ahora + `PT2H` (con un `Clock` fijo); `JwtDecoder` lo acepta; un token alterado se rechaza.
- [X] T029 [P] [US2] Crear `BT/auth/service/AuthServiceTest.java` (Mockito):
  - Un login correcto normaliza el correo y devuelve token y cuenta.
  - Correo inexistente → `InvalidCredentialsException`, y aun así se llama a `passwordEncoder.matches` con el hash ficticio (research §8).
  - Contraseña incorrecta → `InvalidCredentialsException`.
  - Cuenta `DISABLED` con la contraseña correcta → `AccountDisabledException`.
  - `RESTAURANT` `PENDING` puede iniciar sesión (US2-2).
- [X] T030 [P] [US2] Crear `BT/auth/LoginIntegrationTest.java` (MockMvc + BD de pruebas):
  - 200 para los cuatro roles (ADMIN insertado con el repositorio). La respuesta tiene `accessToken`, `tokenType=Bearer` y `expiresAt`, y no contiene la contraseña.
  - 401 con el mismo `detail` para un correo inexistente y para una contraseña incorrecta.
  - 403 para una cuenta DISABLED.
  - `GET /api/auth/me` con el token devuelve 200 con la cuenta del titular.

### Implementation for User Story 2

- [X] T031 [P] [US2] Crear en `BE/auth/dto/` los records `LoginRequest(@NotBlank String email, @NotBlank String password)` y `LoginResponse(String accessToken, String tokenType, Instant expiresAt, AccountResponse account)`.
- [X] T032 [US2] Crear `BE/auth/service/TokenService.java`: `issue(Account)` → `IssuedToken(token, expiresAt)`, usando `JwtEncoder`, `JwsHeader` HS256, claims `sub`, `role`, `iat` y `exp`. La expiración se lee de `mikuna.security.jwt.expiration` (`Duration`) y se usa un `Clock` inyectable (declarar el bean `Clock.systemUTC()` en `SecurityConfig`).
- [X] T033 [US2] Crear `BE/auth/service/AuthService.java`:
  - `login(email, password)`:
    - Normaliza el correo y llama a `findByEmail`.
    - Si no existe, hace `matches` contra un hash ficticio precalculado y lanza `InvalidCredentialsException`.
    - Si la contraseña no coincide → `InvalidCredentialsException`.
    - Si la cuenta está `DISABLED` → `AccountDisabledException`.
    - Si todo es correcto, devuelve un `LoginResponse`.
  - `currentAccount(UUID id)` → `AccountResponse`.
- [X] T034 [US2] Añadir a `BE/auth/controller/AuthController.java`:
  - `POST /login` → `AuthService.login`.
  - `GET /me`, que obtiene el id del titular autenticado (`@AuthenticationPrincipal Jwt` → `UUID.fromString(jwt.getSubject())`; en T044 se sustituye por `AccountPrincipal`). Sin parámetros de identidad.
- [X] T035 [US2] Añadir `login(email,password): Promise<LoginResponse>` y `fetchMe(token): Promise<AccountResponse>` en `frontend/src/features/auth/auth-api.ts`.
- [X] T036 [US2] Crear `frontend/src/features/auth/LoginPage.tsx`: correo y contraseña, `validateLogin` y botón deshabilitado mientras se envía. Si es correcto, `saveSession({token: accessToken, expiresAt: Date.parse(expiresAt)})` y `navigate('/mi-cuenta')`. Si falla, muestra el `detail` del backend. Incluir un enlace a `/registro` y `/`.
- [X] T037 [US2] Crear `frontend/src/features/auth/AccountPage.tsx`:
  - Sin `getSession()` → `<Navigate to="/iniciar-sesion" replace>`.
  - Con sesión, llama a `fetchMe`:
    - 200: muestra nombre, correo, rol en español (Cliente, Restaurante, Repartidor, Administrador), estado y, si es restaurante, «Aprobación comercial pendiente».
    - 401: `clearSession` y redirección al login.
    - 403: `clearSession` y el mensaje «La cuenta no está habilitada».
    - Error de red: mensaje de reintento.
  - Botón «Cerrar sesión» → `clearSession()` y `navigate('/')`.
- [X] T038 [US2] Registrar las rutas `/iniciar-sesion` y `/mi-cuenta` en `frontend/src/App.tsx` y eliminar las páginas provisionales de T015.
- [X] T039 [US2] Conectar los puntos de entrada de acceso:
  - En `frontend/src/features/home/components/SiteHeader.tsx`, añadir antes de «Pedir ahora» un `<Link>` con el texto «Mi cuenta» → `/mi-cuenta` si `hasActiveSession()`, o «Iniciar sesión» → `/iniciar-sesion` si no. Debe cerrar el menú móvil, igual que `followSection`.
  - En `ForRestaurantsPage.tsx` y `CourierPage.tsx`, «Ya tengo cuenta» pasa a `<Link to="/iniciar-sesion">`.
  - «Pedir ahora», «Empezar a pedir» y las categorías siguen en «próximamente» (SC-005).

**Checkpoint**: US1 y US2 funcionan; T028–T030 en verde.

---

## Phase 5: User Story 3 - Mantener los límites de acceso (Priority: P1)

**Goal**: Rol y estado confiables en cada acceso protegido, 401/403 correctos y alta local de
administradores (RF-01 administradores, RF-04 parcial, RNF-07).

**Independent Test**: Crear un ADMIN con el perfil `admin-setup` e iniciar sesión con él. Con un
token manipulado o vencido, `/me` devuelve 401; con una cuenta deshabilitada que conserva su token,
403 (quickstart §2 y pruebas de integración).

### Tests for User Story 3

- [X] T040 [P] [US3] Crear `BT/auth/AccessControlIntegrationTest.java` (MockMvc + BD de pruebas). Casos:
  - `/api/auth/me` sin token → 401.
  - Token con la firma alterada → 401.
  - Token vencido (emitido con un `TokenService` cuyo `Clock` está 3 h atrás) → 401.
  - Token de una cuenta borrada → 401.
  - Token válido de una cuenta que después pasa a `DISABLED` en la BD → 403.
  - Token válido + `?id=<otra>` o la cabecera `X-Role: ADMIN` → 200 con los datos del titular solamente.
  - Token firmado con otro secreto y `role=ADMIN` → 401.
  - `GET /api/otra-ruta` sin token → 401.
  - `GET /actuator/health` sin token → 200.
- [X] T041 [P] [US3] Crear `BT/account/AdminAccountInitializerTest.java` (Mockito):
  - Con nombre, correo y contraseña válidos, llama a `createAdmin`.
  - Con la contraseña corta, un valor ausente o el correo existente → lanza una excepción y no guarda.
  - Nunca registra la contraseña en el log.
- [X] T042 [P] [US3] Crear `BT/common/security/AccountStatusFilterTest.java` (Mockito):
  - Sin autenticación JWT → continúa sin cambios.
  - Cuenta inexistente → 401.
  - DISABLED → 403.
  - ENABLED → la autenticación queda como `AccountPrincipal` con `ROLE_<rol en BD>`, aunque el claim `role` del token sea otro.

### Implementation for User Story 3

- [X] T043 [US3] Crear `BE/common/security/AccountPrincipal.java` (record `id`, `email`, `role`) y `BE/common/security/AccountStatusFilter.java` (`OncePerRequestFilter`):
  - Si el `SecurityContext` tiene un `JwtAuthenticationToken`, carga la cuenta con `AccountRepository.findById(UUID.fromString(sub))`.
  - Si no existe → 401 `ProblemDetail`.
  - Si está `DISABLED` → 403 `ProblemDetail`.
  - Si está habilitada, sustituye la autenticación por un `UsernamePasswordAuthenticationToken(principal, null, ROLE_<role en BD>)`.
  - Registrarlo en `SecurityConfig` con `addFilterAfter(..., BearerTokenAuthenticationFilter.class)` (research §3).
- [X] T044 [US3] En `BE/auth/controller/AuthController.java`, `GET /me` pasa a usar `@AuthenticationPrincipal AccountPrincipal` en lugar de `Jwt`. Actualizar T030 si hace falta.
- [X] T045 [US3] Crear `BE/account/AdminAccountInitializer.java` (`@Component @Profile("admin-setup")`, `ApplicationRunner`):
  - Lee `MIKUNA_ADMIN_NAME`, `MIKUNA_ADMIN_EMAIL` y `MIKUNA_ADMIN_PASSWORD` con `Environment`.
  - Llama a `AccountRegistrationService.createAdmin`.
  - Registra en el log «Administrador creado: <correo normalizado>», sin la contraseña.
  - En caso de error, registra el motivo y termina con un código distinto de cero (`SpringApplication.exit`).
  - Nunca crea una cuenta con valores por defecto (research §7).

**Checkpoint**: las tres historias funcionan; T040–T042 en verde.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T046 Ejecutar `.\mvnw verify` en `backend/` y revisar `backend/target/site/jacoco/index.html`: ≥ 90 % de líneas en `account/service`, `auth/service`, `common/security/AccountStatusFilter` y `common/exception`. Añadir pruebas si no se llega. No excluir lógica de negocio.
- [X] T047 [P] Ejecutar `pnpm test:coverage`, `pnpm lint` y `pnpm build` en `frontend/` y corregir los fallos. No ejecutar `test:e2e`.
- [X] T048 [P] Revisar `backend/src/main/resources/application.properties` y los logs del arranque: no deben aparecer el secreto JWT, contraseñas ni hashes, y ningún DTO de respuesta debe exponer `passwordHash` (constitución III).
- [X] T049 Seguir manualmente [quickstart.md](./quickstart.md) §2 y §3 (filas 1–10) y anotar el resultado en `specs/002-registro-acceso/validation.md`, separando lo verificado de lo pendiente:
  - RF-01: verificado en local.
  - RF-04: parcial.
  - RNF-07: verificado en los accesos implementados.
  - RNF-11: con el porcentaje medido.
  - RNF-06 y la E2E: aplazados.
  - RF-05/RF-06: pendientes.
- [ ] T050 [P] En `specs/002-registro-acceso/checklists/requirements.md`, cambiar la referencia `$speckit-plan` por `/speckit-plan`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** → **Foundational (2)** → bloquea US1, US2 y US3.
- **US1 (3)**: depende solo de la fase 2.
- **US2 (4)**: depende de la fase 2. Comparte `AuthController.java` y `auth-api.ts` con US1, así
  que si se trabaja en paralelo hay que coordinar las ediciones de esos archivos. Sus pruebas crean
  las cuentas con el repositorio, sin usar el endpoint de US1.
- **US3 (5)**: T043–T044 requieren `/me` (T034). T045 requiere `createAdmin` (T020).
- **Polish (6)**: después de las historias que se vayan a entregar.

### Within Each User Story

Pruebas primero (deben fallar) → DTO/modelo → servicio → controlador → API del frontend →
pantallas → puntos de entrada.

### Parallel Opportunities

- Fase 1: T003, T004 y T005 a la vez, después de T001 y T002.
- Fase 2: T006, T008, T009, T010, T012, T013 y T014 a la vez; T007 depende de T006; T011 de T010.
- US1: T016, T017, T018, T019, T022 y T023 a la vez.
- US2: T028, T029, T030 y T031 a la vez.
- US3: T040, T041 y T042 a la vez.

## Parallel Example: User Story 1

```text
Task: "AccountRegistrationServiceTest en BT/account/service/AccountRegistrationServiceTest.java"
Task: "RegisterIntegrationTest en BT/auth/RegisterIntegrationTest.java"
Task: "DTOs RegisterRequest/AccountResponse en BE/auth/dto/"
Task: "auth-validation.ts + auth-validation.test.ts en frontend/src/features/auth/"
```

## Implementation Strategy

### MVP First (User Story 1)

1. Fases 1 y 2.
2. Fase 3 (US1) → **parar y validar**: registro de los tres tipos, guardado en BD, 409 concurrente.

### Incremental Delivery

1. US1 → registro funcional.
2. US2 → acceso y sesión propia de los cuatro roles.
3. US3 → filtro de estado, 401/403 completos y alta de administradores. Las tres son P1, así que
   **RF-01 no se declara cumplido hasta terminar US3** (spec US3: «RF-01 no puede aceptarse si
   permite suplantación»).
4. Polish → cobertura, validación manual y `validation.md`.

## Notes

- Requisitos locales (ya preparados el 2026-11-17): `backend/.env` con `DB_*` y `MIKUNA_JWT_SECRET`
  (no versionado; la plantilla está en `backend/.env.example`), y las bases `mikuna_db` y
  `mikuna_test_db` creadas en PostgreSQL 16 local.
- No crear migraciones, contenedores, CI/CD ni TLS. Playwright sigue instalado para uso manual del
  usuario, pero no se configura, amplía ni ejecuta (constitución 3.0.0).
- Hacer commit al terminar cada fase o grupo lógico.
