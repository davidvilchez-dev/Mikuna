# Contract: API de carrito, direcciones e importes

Base: `http://localhost:8080`. JSON UTF-8; errores `ProblemDetail` como en
[auth-api](../../002-registro-acceso/contracts/auth-api.md), con `errors` en las validaciones.
Campos desconocidos en el JSON → 400. **Todas las rutas son protegidas** (rol `CUSTOMER`):
`Authorization: Bearer <jwt>`. Sin token válido o cuenta inexistente → 401; cuenta `DISABLED` → 403;
otro rol → 403. Una dirección, línea o carrito ajeno responde 404. Ninguna ruta es pública.

## Direcciones: `/api/customer/addresses`

`AddressRequest`: `{ "label"?, "street", "district", "reference"?, "contactPhone"? }`.

`AddressResponse`:

```json
{ "id": "uuid", "label": "Casa", "street": "Jr. Lima 123", "district": "Ayacucho",
  "reference": "...", "contactPhone": "987654321",
  "createdAt": "ISO-8601", "updatedAt": "ISO-8601" }
```

La dirección seleccionada del pedido se ve en `CartResponse.address`.

| Método y ruta | Acción | Resultados |
| --- | --- | --- |
| `POST /api/customer/addresses` | Registra una dirección (RF-12) | 201 `AddressResponse`; 400 validación o `district` fuera de la zona (`errors.district`); 409 si ya tiene 10 direcciones |
| `GET /api/customer/addresses` | Lista las propias | 200 `[AddressResponse]` |
| `PUT /api/customer/addresses/{id}` | Edita una propia | 200; 400 igual que el alta; 404 |
| `DELETE /api/customer/addresses/{id}` | Elimina una propia; si estaba seleccionada, el carrito queda sin dirección | 204; 404 |
| `PUT /api/customer/cart/address` | Selecciona una propia para el pedido (cuerpo `{ "addressId" }`) | 200 `CartResponse`; 400 si ya no está en zona; 404 |

## Carrito: `/api/customer/cart`

`CartResponse`:

```json
{ "restaurant": { "id": "uuid", "name": "...", "attention": "OPEN" },
  "lines": [ { "id": "uuid", "productId": "uuid", "name": "...", "imageUrl": "https://...",
               "unitPrice": 18.50, "quantity": 2, "lineTotal": 37.00,
               "available": true, "reason": null } ],
  "address": { "id": "uuid", "street": "...", "district": "..." },
  "totals": { "subtotal": 37.00, "deliveryFee": 5.00, "commission": 1.85, "total": 43.85,
              "pending": null },
  "notices": [ "..." ] }
```

- `restaurant` y `address` son `null` si no hay restaurante o dirección seleccionada.
- `available = false` y `reason` (`PRODUCT_UNAVAILABLE`, `PRODUCT_WITHDRAWN`, `RESTAURANT_UNAVAILABLE`)
  marcan líneas que ya no son elegibles; no suman al subtotal y `lineTotal` es `null`.
- `totals.pending`: `null` si todo está calculado; `"ADDRESS"` si falta dirección seleccionada
  (entonces `deliveryFee`, `commission` y `total` son `null`); `"EMPTY"` con carrito sin líneas
  elegibles (todos los importes `0.00`).
- `notices`: avisos en español (línea no disponible, dirección fuera de zona
  retirada de la selección).
- Importes en soles con dos decimales; el total siempre es `subtotal + deliveryFee + commission`.

| Método y ruta | Acción | Resultados |
| --- | --- | --- |
| `GET /api/customer/cart` | Consulta el carrito y los importes (RF-11, RF-13); lo crea vacío si no existe | 200 `CartResponse` |
| `POST /api/customer/cart/lines` | Agrega un producto: `{ "productId", "quantity" }` (RF-11). Si ya está, suma | 200 `CartResponse`; 400 cantidad inválida; 404 producto inexistente o retirado; 409 `CART_OTHER_RESTAURANT` (otro restaurante), `PRODUCT_NOT_ORDERABLE` (no disponible o restaurante no habilitado) o límites de 20 unidades por línea y 30 líneas |
| `PUT /api/customer/cart/lines/{lineId}` | Cambia la cantidad: `{ "quantity" }` (1–20) | 200 `CartResponse`; 400; 404 |
| `DELETE /api/customer/cart/lines/{lineId}` | Elimina la línea; si era la última, el carrito queda sin restaurante | 200 `CartResponse`; 404 |
| `DELETE /api/customer/cart` | Vacía el carrito (conserva la dirección seleccionada) | 200 `CartResponse` |

- **Sin `customerId`, `restaurantId` ni precios en los cuerpos**: el propietario sale del token, el
  restaurante del producto y los precios del catálogo vigente.
- Cada respuesta devuelve el `CartResponse` ya recalculado.

## Errores comunes

| Caso | Estado |
| --- | --- |
| Sin token / token inválido / cuenta inexistente | 401 |
| Cuenta inhabilitada / rol distinto de `CUSTOMER` | 403 |
| Dirección, línea o producto inexistente, ajeno o retirado | 404 |
| Validación (campos, cantidad, zona) | 400 con `errors` |
| Otro restaurante, producto no elegible, límites | 409 con `code` (propiedad `code` del `ProblemDetail`: `CART_OTHER_RESTAURANT`, `PRODUCT_NOT_ORDERABLE`, `CART_LIMIT`; el de direcciones, `ADDRESS_LIMIT`) |
