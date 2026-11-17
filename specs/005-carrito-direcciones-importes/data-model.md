# Data Model: Carrito, direcciones y cálculo de importes

## Dirección (`Address` → tabla `direcciones`)

| Campo | Tipo / columna | Reglas |
| --- | --- | --- |
| id | `UUID`, PK, generado | Lo asigna el servidor. |
| customerId | `UUID` NOT NULL (`customer_id`) | Cliente propietario; se toma del token, nunca del cuerpo. |
| label | `varchar(50)` NULL | Opcional (p. ej. «Casa»), ≤ 50. |
| street | `varchar(150)` NOT NULL | Calle y número, recortado, 5–150. |
| district | `varchar(100)` NOT NULL | Debe figurar en la lista de distritos cubiertos, 2–100. |
| reference | `varchar(200)` NULL | Opcional, ≤ 200. |
| contactPhone | `varchar(20)` NULL (`contact_phone`) | Opcional; 7–15 dígitos si se envía. |
| createdAt | `timestamptz` NOT NULL (`created_at`) | `@PrePersist`. |
| updatedAt | `timestamptz` NOT NULL (`updated_at`) | Actualizado en cada cambio. |

**Índices**: `idx_direcciones_cliente (customer_id)`. Límite de 10 direcciones por cliente (servicio).

## Carrito (`Cart` → tabla `carritos`)

| Campo | Tipo / columna | Reglas |
| --- | --- | --- |
| id | `UUID`, PK, generado | |
| customerId | `UUID` NOT NULL (`customer_id`) | `UNIQUE`: un carrito por cliente; del token. |
| restaurantId | `UUID` NULL (`restaurant_id`) | Restaurante de las líneas; `NULL` si el carrito está vacío. |
| selectedAddressId | `UUID` NULL (`selected_address_id`) | Dirección propia en zona, o `NULL`. |
| createdAt / updatedAt | `timestamptz` NOT NULL | |

## Línea de carrito (`CartLine` → tabla `lineas_carrito`)

| Campo | Tipo / columna | Reglas |
| --- | --- | --- |
| id | `UUID`, PK, generado | |
| cart | `@ManyToOne Cart` (`cart_id`) NOT NULL | |
| productId | `UUID` NOT NULL (`product_id`) | Producto del restaurante del carrito. |
| quantity | `int` NOT NULL | 1–20. |
| addedAt | `timestamptz` NOT NULL (`added_at`) | Orden estable de las líneas. |

**Restricciones**: `UNIQUE uk_lineas_carrito_producto (cart_id, product_id)`; máximo 30 líneas por
carrito (servicio). El precio **no** se guarda: se lee del producto al consultar.

## Estados y transiciones del carrito

```text
(vacío, restaurant NULL) --agregar producto elegible--> CON LÍNEAS (restaurant = el del producto)
CON LÍNEAS --agregar otro producto del mismo restaurante / cambiar cantidad / eliminar línea--> CON LÍNEAS
CON LÍNEAS --eliminar la última línea o vaciar--> VACÍO (restaurant = NULL)
VACÍO/CON LÍNEAS --seleccionar dirección válida--> selectedAddressId = id
selectedAddressId --eliminar esa dirección o dejar de estar en zona--> NULL
```

- Agregar un producto de otro restaurante con líneas existentes se rechaza (409); solo tras vaciar.
- Toda mutación bloquea la fila del carrito (research §2).

## Reglas derivadas (no se persisten)

| Regla | Definición |
| --- | --- |
| Línea elegible | Producto `active ∧ available` y restaurante que `canReceiveOrders` (003). |
| Subtotal de línea | `precio vigente × cantidad`, `numeric(10,2)`, solo para líneas elegibles. |
| Subtotal | Σ subtotales de líneas elegibles. |
| Envío | Tarifa fija configurada si hay dirección seleccionada y en zona; si no, pendiente (`null`). |
| Comisión | `subtotal × porcentaje / 100`, `HALF_UP` a 2 decimales; pendiente si el envío lo está. |
| Total | `subtotal + envío + comisión`; pendiente si el envío lo está. Carrito sin líneas elegibles → `0.00`. |

## Configuración (no es una tabla)

| Clave | Contenido |
| --- | --- |
| `mikuna.delivery.districts` | Lista de distritos cubiertos, separados por comas. |
| `mikuna.delivery.fee` | Tarifa fija en soles (≥ 0, 2 decimales). |
| `mikuna.delivery.commission-rate` | Porcentaje de comisión sobre el subtotal (0–100). |

## Entidades que se reutilizan

| Elemento | Uso |
| --- | --- |
| `Product` (004) | Precio, nombre, imagen, `active` y `available`. Sin cambios de esquema. |
| `Restaurant` (003) | Nombre del restaurante del carrito y `OrderEligibility`. Sin cambios. |
| `Account` (002) | Rol `CUSTOMER` y estado habilitado (ya vía `AccountStatusFilter`). |

## Datos que no se exponen

`customerId` de otros clientes, direcciones o carritos ajenos, `imagePublicId` de productos y la
versión interna. Un recurso ajeno se responde como inexistente.
