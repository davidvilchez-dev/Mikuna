# Research: Historiales y calificaciones

Decisiones rutinarias resueltas con las convenciones de 002–009. Sin comparaciones de alternativas externas
(constitución VII). Origen de cada decisión: spec/clarificación del usuario o convención del proyecto.

## 1. Fuente de los datos del historial

- **Decisión**: leer `Order`, `OrderLine` y `Payment` tal como están; no copiar datos a una tabla de historial.
- **Razón**: el pedido ya fija al confirmar nombre del restaurante, nombres y precios de las líneas, importes y
  dirección (`updatable = false`), así que no cambian con el catálogo ni cuando se retira un producto (FR-005).
  Una tabla duplicada sería infraestructura anticipada.
- **Alternativa descartada**: tabla `historial` desnormalizada; añade escrituras y riesgo de desincronización.

## 2. Paginación y orden

- **Decisión**: página/tamaño con `Pageable`; orden fijo `createdAt desc, id desc` (el `id` desempata para que el
  orden sea total). Tamaño por defecto 20, máximo 100; una página fuera de rango devuelve lista vacía.
- **Razón**: convención de Spring Data, suficiente para 1.000 operaciones por usuario (SC-001). Índices
  `(customer_id, created_at)` (ya existe), `(restaurant_id, created_at)` y, para entregas, `(courier_id)` ya
  existente.
- **Compromiso**: un pedido nuevo desplaza un elemento a las páginas siguientes; un cursor se descarta por
  complejidad innecesaria.

## 3. Filtros

- **Decisión**: parámetros opcionales `status`, `from` y `to`. `from`/`to` son fechas (`yyyy-MM-dd`) sobre
  `createdAt`, inclusivas, interpretadas en la zona horaria de la plataforma (`America/Lima`); `from > to` → 400.
  `status` inválido → 400.
- **Razón**: fecha de creación es la única fecha común a los tres historiales y no cambia (supuesto de la spec).

## 4. Historial del repartidor

- **Decisión**: unir `Delivery` (con `courier_id` = repartidor del token) con `Order` en estado `ASSIGNED`,
  `PICKED_UP` o `DELIVERED`. El orden y el filtro de fechas usan `Order.createdAt`.
- **Razón**: clarificación 1 (incluye entregas en curso). `Delivery.release()` borra `courier_id` al rechazar o
  liberar, así que no aparecen entregas que ya no están a su cargo.

## 5. Estado del pago y nombre del cliente

- **Decisión**: el estado del pago se obtiene de `Payment` por `order_id` en una sola consulta por página (mapa
  por ids), no una consulta por fila. El nombre del cliente (solo restaurante y repartidor, FR-016) sale de
  `Account.name` con otra consulta por lote.
- **Razón**: evita N+1 con 20–100 filas. El cliente no necesita su propio nombre.

## 6. Calificación única

- **Decisión**: tabla `calificaciones` con unicidad `order_id`. Flujo: buscar el pedido por `id` y
  `customer_id` (si no existe → 404 `OrderNotFoundException`), exigir `DELIVERED` (si no → 409), validar 1–5
  (si no → 400), insertar. Si la inserción viola la unicidad → 409 `RATING_ALREADY_EXISTS`, sin modificar la
  original.
- **Razón**: la restricción en PostgreSQL es la garantía ante solicitudes simultáneas (constitución IV);
  comprobar antes de escribir no es la única protección. `DELIVERED` es final, así que no hay carrera con el
  estado.
- **Validación**: el valor se recibe como número JSON y se rechaza si no es entero (`3.5`), no está en 1–5 o falta.

## 7. Datos visibles según el rol

- **Decisión**: el cliente ve productos, importes y pago; el restaurante ve pedido, estado, productos, importes,
  pago y calificación, y solo el **nombre** del cliente. El repartidor ve estado, productos (nombre y cantidad),
  dirección de entrega, costo de envío y calificación, más el nombre del cliente; no ve comisión, subtotal, total,
  precios ni pago (clarificaciones 2 y 3, FR-016, FR-017). El teléfono del cliente no se muestra a nadie más.
- **Razón**: mínimo necesario por rol. La dirección de entrega es la que el repartidor ya usó para entregar.
- **Acceso de cuentas no habilitadas**: el historial del restaurante resuelve su establecimiento con
  `RestaurantRepository.findByOwnerId` sin comprobar si está habilitado para pedidos, y el del repartidor no
  consulta su disponibilidad. El bloqueo de cuenta ya impide iniciar sesión (RF-04).

## 8. Frontend

- **Decisión**: una página `/historial` con la misma lista para los tres roles; el rol del token elige el
  endpoint. Calificación con cinco botones (1–5) en `OrderPage` solo si el pedido está `DELIVERED` y sin
  calificar. Vitest cubre la lógica de filtros y paginación, no la presentación.
