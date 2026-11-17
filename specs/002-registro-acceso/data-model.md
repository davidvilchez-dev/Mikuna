# Data Model: Registro e inicio de sesión

## Cuenta (`Account` → tabla `cuentas`)

| Campo | Tipo / columna | Reglas |
| --- | --- | --- |
| id | `UUID`, PK, generado | Se asigna en el servidor y nunca se acepta desde el cliente. |
| name | `varchar(100)` NOT NULL | Recortado, no vacío, ≤ 100. |
| email | `varchar(254)` NOT NULL, `UNIQUE uk_cuentas_email` | Se guarda normalizado (`trim` + minúsculas `Locale.ROOT`). Formato válido, ≤ 254. |
| passwordHash | `varchar(255)` NOT NULL | Hash PBKDF2 con prefijo. Nunca se serializa. |
| role | `varchar(20)` NOT NULL, enum `Role` | `CUSTOMER`, `RESTAURANT`, `COURIER` o `ADMIN`. No se modifica después de crear la cuenta. |
| status | `varchar(20)` NOT NULL, enum `AccountStatus` | `ENABLED` o `DISABLED`. Todo registro nuevo empieza en `ENABLED`. |
| restaurantApproval | `varchar(20)` NULL, enum `RestaurantApproval` | Solo para `RESTAURANT`: `PENDING`. `NULL` para los demás roles. |
| createdAt | `timestamptz` NOT NULL | Se asigna al crear. |

**Columnas de enum:** se guardan como texto (`EnumType.STRING`) con
`columnDefinition = "varchar(20)"`. Así Hibernate no genera una restricción `CHECK` con los valores
actuales, que `ddl-auto=update` no puede ampliar cuando RF-06 añada `APPROVED` o `REJECTED`.

**Validaciones de entrada** (registro): la contraseña mide de 12 a 128 caracteres y
`passwordConfirmation` debe coincidir con ella. Se comprueba en el DTO y otra vez en el servicio.

**Invariantes**

- Un correo normalizado corresponde a una sola cuenta con un único rol (U, Clarifications A).
  Lo garantiza la restricción `UNIQUE`, también ante registros concurrentes.
- El registro público solo crea cuentas `CUSTOMER`, `RESTAURANT` o `COURIER`. `ADMIN` solo se
  crea con `AdminAccountInitializer`.
- `restaurantApproval = PENDING` no da ningún permiso comercial. `APPROVED` y `REJECTED` se
  añadirán con RF-06, no en este incremento.

**Estados**

- `status`: se crea en `ENABLED`. No existe ninguna operación que lo cambie en este incremento.
  `DISABLED` existe para cumplir US3-3 y en las pruebas se asigna directamente en la base de
  datos. La gestión de cuentas pertenece a RF-32 y está pendiente.
- `restaurantApproval`: se crea en `PENDING`. Las transiciones se definirán en RF-06.

## Credencial de sesión (JWT, no se persiste)

| Claim | Contenido |
| --- | --- |
| `sub` | id de la cuenta (UUID) |
| `role` | rol en el momento de emitirlo (solo informativo; la autorización usa el rol de la BD) |
| `iat` / `exp` | emisión y vencimiento (`iat` + 2 h) |

Validación en cada petición protegida: firma y `exp` (si fallan → 401), la cuenta existe
(si no → 401) y `status = ENABLED` (si no → 403).
