# Contract: API de catálogo y consulta

Base: `http://localhost:8080`. JSON UTF-8; errores `ProblemDetail` como en
[auth-api](../../002-registro-acceso/contracts/auth-api.md), con `errors` en las validaciones.
Campos desconocidos en el JSON → 400.

## Consulta pública (sin sesión): `/api/public/restaurants`

Solo `GET`. **No enviar `Authorization`**: un token inválido en estas rutas responde 401.

`PublicRestaurantResponse`:

```json
{ "id": "uuid", "name": "...", "category": "...", "description": "...", "zone": "...",
  "attention": "OPEN|OUTSIDE_HOURS|PAUSED" }
```

`PageResponse<T>`: `{ "items": [T], "page": 0, "size": 20, "totalItems": 0, "totalPages": 0 }`.

| Método y ruta | Acción | Resultados |
| --- | --- | --- |
| `GET /api/public/restaurants?name=&category=&zone=&page=&size=` | Busca restaurantes visibles (RF-09). Criterios opcionales y combinables; `name` contiene el texto sin distinguir mayúsculas ni tildes; `category` y `zone` por igualdad con el mismo criterio. Orden: `OPEN` primero, luego por nombre. | 200 `PageResponse<PublicRestaurantResponse>` (vacía si no hay); 400 `page`/`size` inválidos |
| `GET /api/public/restaurants/filters` | Categorías y zonas existentes entre los restaurantes visibles (FR-009) | 200 `{ "categories": ["..."], "zones": ["..."] }` |
| `GET /api/public/restaurants/{id}/catalog?page=&size=` | Catálogo de un restaurante visible (RF-10) | 200 `CatalogResponse`; 404 si no existe o no es visible |

`CatalogResponse`:

```json
{ "restaurant": { "id": "uuid", "name": "...", "category": "...", "description": "...",
                  "zone": "...", "attention": "OUTSIDE_HOURS" },
  "products": { "items": [ { "id": "uuid", "name": "...", "description": "...", "category": "...",
                             "price": 18.50, "imageUrl": "https://..." } ],
                "page": 0, "size": 24, "totalItems": 1, "totalPages": 1 } }
```

- Solo productos activos y disponibles, ordenados por categoría y nombre.
- No hay ruta pública de producto por id: un producto retirado o no disponible no puede obtenerse
  por ningún medio (FR-011).
- Un restaurante pendiente, rechazado, con cuenta inhabilitada o inexistente da el mismo 404.
- Nunca se devuelven dirección, teléfono, datos de cuenta o titular.

## Gestión del catálogo propio (rol `RESTAURANT`): `/api/restaurant/me/products`

Protegida (`Authorization: Bearer <jwt>`). Sin token válido o cuenta inexistente → 401; cuenta
`DISABLED` → 403; otro rol → 403. Restaurante inexistente → 404; `PENDING` o `REJECTED` → 403.

`ProductRequest` (parte JSON `product`): `{ "name", "category", "description"?, "price",
"version"? }`. `version` es obligatoria al editar y no se admite al crear.

`ProductResponse`:

```json
{ "id": "uuid", "name": "...", "description": "...", "category": "...", "price": 18.50,
  "imageUrl": "https://...", "available": true, "version": 0,
  "createdAt": "ISO-8601", "updatedAt": "ISO-8601" }
```

Las altas y ediciones son `multipart/form-data` con la parte JSON `product` y la parte binaria
`image` (JPG, PNG o WebP, ≤ 2 MB).

| Método y ruta | Acción | Resultados |
| --- | --- | --- |
| `GET /api/restaurant/me/products?page=&size=` | Lista los propios no retirados (disponibles o no) | 200 `PageResponse<ProductResponse>`; 403; 404 |
| `POST /api/restaurant/me/products` | Crea (RF-08); `image` obligatoria | 201 `ProductResponse` (disponible); 400 datos o imagen inválidos; 403; 404; 409 duplicado; 413 imagen > 2 MB; 503 servicio de imágenes |
| `GET /api/restaurant/me/products/{id}` | Consulta uno propio | 200; 403; 404 (inexistente, retirado o ajeno) |
| `PUT /api/restaurant/me/products/{id}` | Edita datos; `image` opcional (si falta se conserva la actual) | 200 (nueva `version`); 400; 403; 404; 409 duplicado o `version` desfasada; 413; 503 |
| `PUT /api/restaurant/me/products/{id}/availability` | `{ "available": true, "version": 3 }` | 200; 400; 403; 404; 409 `version` desfasada |
| `DELETE /api/restaurant/me/products/{id}?version=3` | Retira (lógico, definitivo) | 204; 400 sin `version`; 403; 404; 409 `version` desfasada |

## Matriz de acceso (RNF-07)

| Situación | Resultado |
| --- | --- |
| Rutas `/api/public/**` sin sesión | 200 (solo lectura de datos públicos) |
| Rutas `/api/public/**` con token inválido | 401 (no enviar token) |
| Sin token, manipulado o vencido en gestión | 401 |
| Cuenta `DISABLED` en gestión | 403 |
| Cliente, repartidor o administrador en `/api/restaurant/**` | 403 |
| Restaurante `PENDING` o `REJECTED` gestiona productos | 403 |
| Producto de otro restaurante (consultar, editar, disponibilidad, retirar) | 404 (indistinguible de inexistente) |
| Enviar `restaurantId`, `active`, `available` en el alta o campos extra | 400 |
| Escritura (`POST`/`PUT`/`DELETE`) en `/api/public/**` | 401 (no existe ruta pública; exige autenticación) |
