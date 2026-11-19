# Contrato: API de administración (spec 011)

Todos los endpoints son **protegidos, rol ADMIN** (`/api/admin/**` en `SecurityConfig`). Sin sesión válida → 401;
otro rol → 403 (ProblemDetail). El administrador sale del token. Errores en ProblemDetail (RFC 9457); los de
validación incluyen `errors: { campo: mensaje }`.

**Paginación** (todos los listados): `page` (≥ 0, por defecto 0), `size` (1–100, por defecto 20). Respuesta
`{ items, page, size, totalItems, totalPages }` (`PageResponse`). Fuera de rango → 400 con el campo.

## Cuentas de acceso (usuarios)

| Método y ruta | Descripción | Respuestas |
| --- | --- | --- |
| `GET /api/admin/users?role=&status=&q=&page=&size=` | Lista cuentas de clientes, restaurantes y repartidores (nunca ADMIN). `role` ∈ CUSTOMER/RESTAURANT/COURIER; `status` ∈ ENABLED/DISABLED; `q` busca en nombre o correo. | 200 `PageResponse<AccountSummary>`; 400 valor inválido |
| `GET /api/admin/users/{id}` | Detalle y registro de acciones. | 200 `AccountDetail`; 404 |
| `POST /api/admin/users/{id}/disable` | Bloquea el inicio de sesión (ámbito `ACCESS`). | 200 `AccountDetail`; 404; 409 `ADMIN_ACCOUNT_PROTECTED` |
| `POST /api/admin/users/{id}/enable` | Restablece el acceso. | ídem |

## Restaurantes (vista operativa)

| Método y ruta | Descripción | Respuestas |
| --- | --- | --- |
| `GET /api/admin/restaurants/search?registration=&operation=&q=&page=&size=` | `registration` ∈ PENDING/APPROVED/REJECTED; `operation` ∈ ACTIVE/INACTIVE; `q` nombre del restaurante o correo del titular. | 200 `PageResponse<AccountSummary>`; 400 |
| `GET /api/admin/restaurants/{id}` | Detalle del restaurante y acciones `OPERATION` de su titular. | 200; 404 |
| `POST /api/admin/restaurants/{id}/deactivate` | `operation_enabled = false`: sale de la búsqueda y no recibe pedidos nuevos; los pedidos en curso continúan; el titular conserva el acceso. | 200; 404 |
| `POST /api/admin/restaurants/{id}/activate` | Restablece. | 200; 404 |

`GET /api/admin/restaurants` (lista de solicitudes) y `approve`/`reject` pertenecen a la spec 003 y no cambian.

## Repartidores (vista operativa)

| Método y ruta | Descripción | Respuestas |
| --- | --- | --- |
| `GET /api/admin/couriers?operation=&q=&page=&size=` | Lista de repartidores. | 200 `PageResponse<AccountSummary>`; 400 |
| `GET /api/admin/couriers/{id}` | Detalle, disponibilidad y acciones `OPERATION`. | 200; 404 |
| `POST /api/admin/couriers/{id}/deactivate` | Deja de ser candidato para entregas nuevas; las asignadas o en curso continúan; conserva el acceso. | 200; 404 |
| `POST /api/admin/couriers/{id}/activate` | Restablece. | 200; 404 |

Repetir un cambio ya aplicado responde 200 con el estado actual y **no** registra una acción nueva.

## Pedidos y entregas (solo lectura)

| Método y ruta | Descripción | Respuestas |
| --- | --- | --- |
| `GET /api/admin/orders?status=&page=&size=` | Todos los pedidos; `status` ∈ `OrderStatus`; del más reciente al más antiguo. | 200 `PageResponse<AdminOrderSummary>`; 400 |
| `GET /api/admin/orders/{id}` | Detalle: cliente, restaurante, repartidor, líneas, importes, pago y cambios de estado cronológicos. | 200 `AdminOrderDetail`; 404 |
| `GET /api/admin/deliveries?status=&page=&size=` | Todas las entregas; `status` ∈ READY/ASSIGNED/PICKED_UP/DELIVERED (estado del pedido). | 200 `PageResponse<AdminDeliverySummary>`; 400 |
| `GET /api/admin/deliveries/{id}` | Detalle de entrega con pedido asociado, pago y cambios de estado. | 200 `AdminDeliveryDetail`; 404 |

Ningún endpoint de este contrato modifica pedidos o entregas, y no existe `DELETE` sobre cuentas.
