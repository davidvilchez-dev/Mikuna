# Research: Administración de la plataforma

Decisiones puntuales; no se compararon alternativas generales (constitución VII).

## 1. Separar acceso y operación

- **Decision**: añadir `Account.operation_enabled` (default true). `status` = puede iniciar sesión;
  `operation_enabled` = puede recibir pedidos (restaurante) o ser candidato (repartidor). Para clientes no se usa.
- **Rationale**: la clarificación 1 pide que un restaurante o repartidor desactivado conserve el acceso a su
  historial. Hoy ambas cosas dependen de `status` (`findVisible*`, `CANDIDATE`, `OrderEligibility(ownerEnabled)`).
  Un restaurante es 1:1 con su titular (`uk_restaurantes_owner`) y el repartidor no tiene entidad propia, así que
  la bandera en la cuenta sirve a ambos sin tabla nueva.
- **Alternatives**: bandera en `Restaurant` y otra en `CourierAvailability` (dos lugares, y un repartidor sin fila
  de disponibilidad no tendría dónde guardarla); reutilizar `status` (contradice la clarificación 1).

## 2. Efecto inmediato de la desactivación de acceso

- **Decision**: no añadir lógica de sesiones. `AccountStatusFilter` recarga la cuenta en cada acceso protegido y
  responde 403 si `status` no es ENABLED; `AuthService` ya rechaza el inicio de sesión (FR-005).

## 3. Idempotencia y concurrencia del cambio de estado

- **Decision**: `update Account set status = :to where id = :id and status = :from`. Si afecta 1 fila, se inserta
  `AdminAccountAction` en la misma transacción; si afecta 0 y la cuenta ya está en el destino, se responde 200 sin
  registrar; si no existe, 404. Igual para `operation_enabled`.
- **Rationale**: constitución IV; dos administradores simultáneos producen un solo cambio y un solo registro.

## 4. Protección de administradores

- **Decision**: rechazar con 409 (`ADMIN_ACCOUNT_PROTECTED`) cualquier cambio de acceso sobre una cuenta de rol
  ADMIN, incluida la propia. Las rutas de restaurantes y repartidores solo resuelven cuentas con ese rol, por lo
  que un id de administrador allí responde 404.

## 5. Entregas por estado

- **Decision**: la entrega no tiene estado propio (spec 008: el visible es el del pedido). El listado une
  `entregas` con `pedidos` y filtra por el estado del pedido; solo se aceptan `READY`, `ASSIGNED`, `PICKED_UP` y
  `DELIVERED` (otro valor de `OrderStatus` → 400, porque no existe entrega en ese estado). Una entrega se
  identifica por su `id`; se ordena por `created_at` del pedido e `id` descendentes.
- **Cambios de estado**: los de `cambios_estado_pedido` del pedido (todos, en orden cronológico, con actor y rol).

## 6. Rutas

- **Decision**: rutas nuevas sin tocar contratos previos: `/api/admin/users`, `/api/admin/couriers`,
  `/api/admin/restaurants/search` y `/{id}` (sin colisión con `GET /api/admin/restaurants` de la spec 003, que
  devuelve la lista de solicitudes), `/api/admin/orders`, y `GET /api/admin/deliveries` y `/{id}` (`/pending`,
  `/{orderId}/assign` y `/{orderId}/release` de la spec 008 no colisionan: otro método o ruta literal).

## 7. Búsqueda

- **Decision**: parámetro `q`, coincidencia sin distinguir mayúsculas contenida en nombre o correo (en
  restaurantes: nombre del restaurante o correo del titular); los comodines `%` y `_` escritos por el usuario se
  escapan. Orden por nombre e `id`.

## 8. Paginación

- **Decision**: misma regla que la spec 010 (`page` desde 0, `size` 1–100, por defecto 20; fuera de rango → 400 con
  el campo). Desplazamiento, no cursor.
