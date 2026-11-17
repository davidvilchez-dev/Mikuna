# Data Model: Registro, aprobación y datos del restaurante

## Restaurante (`Restaurant` → tabla `restaurantes`)

| Campo | Tipo / columna | Reglas |
| --- | --- | --- |
| id | `UUID`, PK, generado | Lo asigna el servidor. |
| ownerId | `UUID` NOT NULL, `UNIQUE uk_restaurantes_owner` | Id de la cuenta `RESTAURANT`; se toma del token, nunca del cuerpo. |
| name | `varchar(100)` NOT NULL | Recortado, 2–100. |
| category | `varchar(50)` NOT NULL | Texto, 2–50 (tipo de cocina). |
| description | `varchar(300)` NULL | Opcional, ≤ 300. |
| address | `varchar(200)` NOT NULL | 5–200. |
| zone | `varchar(100)` NOT NULL | Distrito o zona, 2–100. |
| phone | `varchar(20)` NOT NULL | `+` opcional y 7–15 dígitos o espacios. |
| status | `varchar(20)` NOT NULL | `PENDING`, `APPROVED` o `REJECTED`; texto plano. |
| submittedAt | `timestamptz` NOT NULL | Fecha del envío o del último reenvío. |
| rejectionReason | `varchar(300)` NULL | Obligatorio al rechazar; se borra al reenviar. |
| resolvedBy | `UUID` NULL | Administrador que resolvió. |
| resolvedAt | `timestamptz` NULL | Fecha de la última resolución. |
| available | `boolean` NOT NULL | `false` al crear y al aprobar; solo lo cambia el titular aprobado. |
| version | `bigint` NOT NULL | `@Version` (edición concurrente). |

## Horario de atención (`OpeningHour` → tabla `horarios_restaurante`)

| Campo | Tipo / columna | Reglas |
| --- | --- | --- |
| restaurante_id | `UUID`, FK | Pertenece a un restaurante. |
| day (columna `day_of_week`) | `varchar(10)` | `MONDAY`…`SUNDAY`; `UNIQUE(restaurante_id, day_of_week)`. |
| opensAt (`opens_at`) | `time` NULL | Con `closesAt`; ambos `NULL` = cerrado. |
| closesAt (`closes_at`) | `time` NULL | Posterior a `opensAt` (sin cruce de medianoche). |

Un restaurante sin horario guardado no está abierto ningún día.

## Estados y transiciones

```text
(sin solicitud) --POST /me--> PENDING --aprobar--> APPROVED
                              PENDING --rechazar(motivo)--> REJECTED --reenviar (titular)--> PENDING
```

- Solo existen esas transiciones; cada una es una actualización condicional sobre el estado de origen.
- `APPROVED` es final en este incremento (suspensión posterior = RF-32, pendiente).
- Datos generales: editables en `PENDING`, `REJECTED` y `APPROVED`; editar no cambia el estado.
- Horario y disponibilidad: solo en `APPROVED`.

## Regla derivada: habilitado para recibir pedidos

`acceptingOrders = status = APPROVED ∧ cuenta.status = ENABLED ∧ available ∧ hora local dentro de la franja del día`.
No se persiste; se calcula en cada consulta.

## Cambios sobre la spec 002

| Elemento | Cambio |
| --- | --- |
| `Account.restaurantApproval` y enum `RestaurantApproval` | Se eliminan; el estado comercial es del `Restaurant`. |
| `AccountResponse` y contrato `auth-api.md` | Se elimina `restaurantApproval`. |
| `AccountPage.tsx`, `auth-api.ts`, `RegisterPage.tsx` | Dejan de usar `restaurantApproval`; el aviso de «pendiente» pasa a `/mi-restaurante`. |
| Pruebas de 002 que lo referencian | `AccountRegistrationServiceTest`, `RegisterIntegrationTest`, `LoginIntegrationTest`, `AuthServiceTest`. |
| Columna `restaurant_approval` en bases existentes | Queda sin uso y nullable; no se elimina. |
