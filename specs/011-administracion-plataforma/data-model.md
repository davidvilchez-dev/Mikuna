# Data Model: Administración de la plataforma

## Cambio a `cuentas` (`Account`)

| Campo | Tipo | Regla |
| --- | --- | --- |
| `operation_enabled` | boolean not null default true | Solo relevante para roles RESTAURANT y COURIER. `false` = restaurante fuera de búsqueda y sin pedidos nuevos / repartidor fuera de candidatos. No afecta al inicio de sesión. |

`status` (ENABLED/DISABLED) conserva su significado: acceso. `canOperate()` = `status == ENABLED &&
operation_enabled`; la usan `OrderEligibility` (vía `ownerEnabled`) y las consultas de visibilidad y candidatura.

## Tabla nueva `acciones_admin_cuenta` (`AdminAccountAction`)

Solo inserción; sin actualización ni borrado.

| Campo | Tipo | Regla |
| --- | --- | --- |
| `id` | UUID | Generado. |
| `account_id` | UUID not null | Cuenta afectada (para un restaurante, la de su titular). |
| `scope` | texto (`ACCESS` / `OPERATION`) | Qué bandera cambió. Texto plano, sin `@Enumerated` con CHECK. |
| `action` | texto (`DEACTIVATED` / `ACTIVATED`) | Cambio efectivo. |
| `admin_id` | UUID not null | Administrador responsable, tomado del token. |
| `created_at` | Instant not null | Fecha de la acción. |

Índice `ix_acciones_cuenta_fecha (account_id, created_at)`. Una fila por cambio efectivo; las solicitudes que no
cambian nada no insertan (FR-010).

## Índices añadidos en tablas existentes

- `pedidos (status, created_at)` para el listado administrativo filtrado por estado.

## Lecturas (sin cambios de esquema)

`cuentas`, `restaurantes`, `pedidos` y sus líneas, `pagos`, `cambios_estado_pedido`, `entregas` y
`repartidores_disponibilidad` se leen tal cual. Nada se modifica en pedidos, entregas ni pagos (FR-018).

## Vistas de lectura (DTO)

- **AccountSummary**: id, nombre, correo, rol, `accessEnabled`, `operationEnabled` (solo restaurante/repartidor),
  fecha de registro.
- **AccountDetail**: lo anterior + (restaurante) datos del restaurante y estado de registro de la spec 003 +
  (repartidor) disponibilidad + `actions[]` (acción, ámbito, fecha, administrador responsable con su nombre).
- **AdminOrderSummary / Detail**: pedido, estado, cliente (nombre, correo), restaurante, repartidor si lo hay,
  líneas con precios fijados, importes, estado y monto del pago, `statusChanges[]`.
- **AdminDeliverySummary / Detail**: entrega, estado (el del pedido), repartidor, pedido asociado con estado de
  pago, `statusChanges[]`.

Ninguna vista contiene `password_hash`.
