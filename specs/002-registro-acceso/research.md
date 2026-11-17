# Research: Registro e inicio de sesión

Son decisiones rutinarias según el principio VII: no se compararon alternativas en extenso. Origen
de cada una: **C** = constitución, **U** = usuario o spec, **P** = este plan.

## 1. Tokens de sesión

- **Decision (P, basada en C-III):** JWT HS256 firmado por el backend con `NimbusJwtEncoder`.
  Se valida con el resource server de Spring Security
  (`spring-boot-starter-security-oauth2-resource-server`, nombre comprobado en el BOM de Boot
  4.1.2). Claims: `sub` = id de la cuenta, `role` (informativo), `iat` y `exp`. Vigencia de 2 h
  (`mikuna.security.jwt.expiration=PT2H`). Se envía en `Authorization: Bearer`.
- **Rationale:** cumple la regla de tokens sin sesiones en memoria y no requiere un servidor de
  autorización aparte. Al no usar cookies, no se necesita CSRF.
- **Secreto (U, 2026-11-17):** los valores locales (`DB_*`, `MIKUNA_JWT_SECRET`,
  `MIKUNA_JWT_EXPIRATION`) están en `backend/.env`. `application.properties` lo importa con
  `spring.config.import=optional:file:.env[.properties]` y define
  `mikuna.security.jwt.secret=${MIKUNA_JWT_SECRET}`; la configuración sigue centralizada allí (C).
  El arranque falla si el secreto mide menos de 32 bytes. `.env` está en `.gitignore` y solo se
  versiona `backend/.env.example` (C-III).
- **Alternatives considered:** sesión HTTP con cookie (la descarta C-III).

## 2. Cierre de sesión

- **Decision (P):** solo en el cliente: se borra el token de `sessionStorage`. No hay endpoint de
  logout.
- **Rationale:** el token no tiene estado y la spec pide cerrar la sesión «en el navegador». Lo
  que limita el riesgo es la vigencia corta y que el estado de la cuenta se revisa en cada
  petición.
- **Pendiente:** la revocación en el servidor (lista negra o refresh tokens) queda fuera del
  incremento.

## 3. Rol y estado confiables (RF-04, RNF-07)

- **Decision (P):** `AccountStatusFilter` se ejecuta después de `BearerTokenAuthenticationFilter`
  y carga la cuenta por `sub`:
  - Si la cuenta no existe → 401.
  - Si su estado es `DISABLED` → 403.
  - Si todo es correcto, sustituye la autenticación por un `AccountPrincipal` con la autoridad
    `ROLE_<rol en BD>`.
- **Rationale:** el rol y el estado nunca se toman del token ni del cliente, y la comprobación se
  aplica a cualquier endpoint protegido futuro sin repetirla.

## 4. Hash de contraseñas

- **Decision (P):** `Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()`, envuelto en
  `DelegatingPasswordEncoder` con el prefijo `{pbkdf2@SpringSecurity_v5_8}`.
- **Rationale:** es adaptativo y con sal (C-III), viene incluido en Spring Security y admite las
  contraseñas de hasta 128 caracteres de la spec. BCrypt rechaza o trunca lo que pase de 72 bytes.
- **Alternatives considered:** BCrypt (por el límite de 72 bytes); Argon2 (requiere añadir
  BouncyCastle).

## 5. Unicidad del correo ante concurrencia (C-IV, SC-004)

- **Decision (P):** el correo se normaliza con `trim().toLowerCase(Locale.ROOT)` en
  `EmailNormalizer` antes de validar, guardar y buscar. La restricción de PostgreSQL
  `uk_cuentas_email` es la garantía real. La comprobación previa `existsByEmail` solo sirve para
  dar un mensaje rápido. `DataIntegrityViolationException` se traduce a 409.
- **Rationale:** comprobar y luego escribir no puede ser la única protección (C-IV).

## 6. Rechazo de campos privilegiados (US1-4)

- **Decision (P):** `spring.jackson.deserialization.fail-on-unknown-properties=true`, de modo que
  cualquier campo `status`, `restaurantApproval`, `id`… devuelve 400. `role` solo admite
  `CUSTOMER`, `RESTAURANT` y `COURIER`; `ADMIN` devuelve 400.
- **Rationale:** el rechazo es explícito, como pide la spec, y no se ignora en silencio.

## 7. Alta de administradores (U, 2026-11-13)

- **Decision (P):** `AdminAccountInitializer` es un `ApplicationRunner` que solo se activa con el
  perfil `admin-setup`. Lee `MIKUNA_ADMIN_NAME`, `MIKUNA_ADMIN_EMAIL` y `MIKUNA_ADMIN_PASSWORD`
  desde variables de entorno, aplica las mismas validaciones y crea la cuenta `ADMIN` con estado
  `ENABLED`. Falla si el correo ya existe. Al terminar cierra la aplicación
  (`--spring.main.web-application-type=none`).
- **Rationale:** es un procedimiento local controlado por el responsable. La contraseña no queda
  en el repositorio ni en los argumentos de línea de comandos.

## 8. Errores de la API

- **Decision (P):** formato `ProblemDetail` (RFC 9457) generado por `GlobalExceptionHandler`. Las
  validaciones devuelven un mapa `errors` por campo. Un login fallido siempre responde
  `401 "Correo o contraseña incorrectos"`. Si el correo no existe, se compara la contraseña con un
  hash ficticio para que el tiempo de respuesta no revele si existe.
- **Rationale:** no expone si un correo existe ni detalles internos (US2-3).

## 9. Enrutado y proxy del frontend

- **Decision (P):** `react-router` en modo librería (`BrowserRouter`). Las secciones del inicio
  siguen usando anclas `#`. El proxy de desarrollo de Vite reenvía `/api` a `localhost:8080`.
- **Rationale:** permite navegación directa, recarga y botón atrás (casos límite) sin configurar
  CORS. El fallback SPA de Vite sirve `index.html` en cualquier ruta.

## 10. Almacenamiento de la sesión en el navegador

- **Decision (P):** `sessionStorage` con `{ token, expiresAt }`. Una sesión vencida se descarta
  al cargar. Si `/me` devuelve 401 o 403, se borra la sesión.
- **Rationale:** dura lo mismo que la pestaña y una sesión vencida nunca aparece como válida.

## 11. Pruebas y cobertura (C-V, RNF-11)

- **Decision (P):**
  - Las pruebas de integración usan la base local `mikuna_test_db`, que el usuario crea; se
    configura en `src/test/resources/application.properties`. No se usan Testcontainers, porque
    los contenedores están aplazados (C).
  - Cobertura del backend con JaCoCo 0.8.14 (admite Java 25). Se excluyen `BackendApplication`,
    los DTO (records sin lógica) y la configuración.
  - En el frontend, Vitest con `@vitest/coverage-v8` mide solo `auth-validation.ts` y
    `session-storage.ts`.
- **Rationale:** se necesita PostgreSQL real para probar la restricción `UNIQUE` concurrente.
