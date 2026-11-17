# Data Model: Catálogo y consulta de restaurantes y productos

## Producto (`Product` → tabla `productos`)

| Campo | Tipo / columna | Reglas |
| --- | --- | --- |
| id | `UUID`, PK, generado | Lo asigna el servidor. |
| restaurantId | `UUID` NOT NULL (`restaurant_id`) | Id del restaurante propietario; se toma del token, nunca del cuerpo. |
| name | `varchar(100)` NOT NULL | Recortado, 2–100. |
| nameKey | `varchar(100)` NOT NULL (`name_key`) | Derivado: nombre en minúsculas y sin tildes; solo para unicidad. |
| description | `varchar(300)` NULL | Opcional, ≤ 300. |
| category | `varchar(50)` NOT NULL | Texto libre del restaurante, 2–50. |
| categoryKey | `varchar(50)` NOT NULL (`category_key`) | Derivado como `nameKey`. |
| price | `numeric(8,2)` NOT NULL | `0.01`–`9999.99`, ≤ 2 decimales. |
| imageUrl | `varchar(500)` NOT NULL (`image_url`) | `secure_url` de Cloudinary. |
| imagePublicId | `varchar(200)` NOT NULL (`image_public_id`) | Para borrar o sustituir la imagen. |
| available | `boolean` NOT NULL | `true` al crear; lo cambia el restaurante. |
| active | `Boolean` NULL | `true` mientras el producto no está retirado; `NULL` al retirarlo. |
| withdrawnAt | `timestamptz` NULL (`withdrawn_at`) | Fecha del retiro. |
| createdAt | `timestamptz` NOT NULL (`created_at`) | Asignado en `@PrePersist`. |
| updatedAt | `timestamptz` NOT NULL (`updated_at`) | Actualizado en cada cambio. |
| version | `bigint` NOT NULL | `@Version` (edición concurrente). |

**Restricciones e índices**

- `UNIQUE uk_productos_activo (restaurant_id, name_key, category_key, active)`: un nombre por
  categoría entre los productos activos de un restaurante. Al retirar (`active = NULL`) se libera.
- Índice `idx_productos_restaurante (restaurant_id, active, available)` para el catálogo.

## Estados y transiciones

```text
(nuevo) --crear--> ACTIVO+DISPONIBLE <--disponible/no disponible--> ACTIVO+NO DISPONIBLE
ACTIVO (cualquiera) --retirar--> RETIRADO (final; active = NULL)
```

- Solo un restaurante `APPROVED` con cuenta habilitada crea, edita, cambia disponibilidad o retira.
- Un producto retirado no se lista, no se edita y responde 404.
- Edición, disponibilidad y retiro exigen la `version` vigente; si no coincide → 409.

## Reglas derivadas (no se persisten)

| Regla | Definición |
| --- | --- |
| Restaurante visible | `status = APPROVED ∧ cuenta.status = ENABLED` (la consulta usa `Account` por `ownerId`). |
| Situación de atención | `PAUSED` si `available = false`; si no, `OPEN` si la hora local cae en la franja del día; si no, `OUTSIDE_HOURS`. |
| Habilitado para recibir pedidos | `visible ∧ situación = OPEN` (misma regla de 003, ahora vía `attentionStatus`). |
| Producto visible al público | `active = true ∧ available = true`, de un restaurante visible. |

## Entidades de 002 y 003 que se reutilizan

| Elemento | Uso |
| --- | --- |
| `Restaurant` (003) | Nombre, categoría, zona, descripción, estado, disponibilidad y horario. Sin cambios de esquema. |
| `OpeningHour` (003) | Horario por día para la situación de atención. |
| `Account` (002) | Solo su estado `ENABLED` para la visibilidad. |
| `OrderEligibility` (003) | Se le añade `attentionStatus()`; `canReceiveOrders` conserva su resultado. |

## Datos que no se exponen públicamente

Dirección, teléfono, `ownerId`, estado de aprobación, motivo de rechazo, correo o nombre del titular,
`imagePublicId`, `version` y la disponibilidad interna del producto.
