# Research: Disponibilidad, asignación y entrega (008)

Decisiones puntuales (constitución VII). Tecnologías ya elegidas.

## 1. Garantía de una sola asignación (RNF-05)

- **Decisión**: toda operación sobre una entrega empieza con `OrderRepository.findByIdForUpdate` (ya existe,
  007) y, con el pedido bloqueado, comprueba el estado esperado (`READY`) y ejecuta
  `UPDATE entregas SET courier_id = :c, assigned_at = :t WHERE order_id = :o AND courier_id IS NULL`; solo si
  afecta **1 fila** se cambia el pedido a `ASSIGNED`. Si afecta 0, 409 sin efecto.
- **Razón**: la constitución exige actualización condicional en PostgreSQL; el bloqueo del pedido serializa
  asignación automática, manual, rechazo y reasignación sobre la misma entrega, y el `WHERE courier_id IS NULL`
  mantiene la unicidad aunque otra ruta olvide el bloqueo.
- **Alternativas**: solo `@Version` optimista (reintentos y falsos fallos); bloqueo en memoria (prohibido como
  única protección).

## 2. Una entrega activa por repartidor y selección del candidato

- **Decisión**: el candidato se elige con una consulta nativa sobre `repartidores_disponibilidad` unida a
  `cuentas` (estado `ENABLED`), con `available = true`, sin entrega activa (pedido `ASSIGNED` o `PICKED_UP` con
  su `courier_id`) y sin fila en `rechazos_entrega` para esa entrega; orden `free_since ASC`, `LIMIT 1` y
  `FOR UPDATE OF ca SKIP LOCKED`. La asignación manual bloquea la fila del repartidor elegido y revalida los
  mismos criterios.
- **Razón**: bloquear la fila del repartidor impide que dos pedidos distintos le lleguen a la vez (carrera
  entre entregas, no solo sobre una). `SKIP LOCKED` hace que el segundo pedido pase al siguiente candidato en
  lugar de esperar.
- **Orden de bloqueo fijo**: pedido → repartidor. Activar/desactivar disponibilidad solo toca la fila del
  repartidor y termina antes de despachar, así que no hay ciclos.
- **Alternativas**: índice único parcial sobre `courier_id` de entregas activas (JPA/`ddl-auto=update` no lo
  crea y la «activa» depende del pedido); bloqueo advisory (más difícil de probar).

## 3. «Libre desde»

- **Decisión**: `CourierAvailability.freeSince` se fija al activar la disponibilidad y al registrar una entrega
  (`DELIVERED`); no cambia al rechazar ni al desactivar. La ausencia de fila equivale a no disponible.
- **Razón**: spec (Assumptions) y criterio «el que lleva más tiempo libre»; el rechazo no lo beneficia ni lo
  penaliza porque ya queda excluido de esa entrega.

## 4. Cuándo se dispara la asignación

- **Decisión**: `DeliveryDispatcher` (sin transacción propia) llama a `DeliveryAssignmentService` en **su propia
  transacción corta** tras el commit de: (a) pedido `READY` (listener `AFTER_COMMIT` de `OrderReadyEvent`);
  (b) repartidor que se activa; (c) entrega terminada; (d) rechazo (se reasigna la misma entrega). Los casos
  (b) y (c) asignan la entrega pendiente más antigua (`ready_at ASC`) a ese repartidor; (a) y (d) eligen al
  mejor candidato para esa entrega.
- **Razón**: cumple FR-004 sin barridos ni temporizadores. Si falla, queda pendiente y el siguiente disparo o el
  administrador la resuelven; nunca se pierde porque la fila `entregas` ya existe.
- **Alternativas**: tarea programada periódica (infraestructura/estado extra, innecesaria); asignar dentro de la
  transacción de `markReady` (acopla restaurante y reparto y alarga el bloqueo).

Los servicios de asignación usan `Propagation.REQUIRES_NEW` porque se invocan tras el commit del cambio que
los origina; sin ello se unirían a una transacción ya terminada y sus escrituras no se confirmarían.

## 5. Creación de la entrega sin acoplar `order` a `delivery`

- **Decisión**: `OrderStatusService.markReady` publica `OrderReadyEvent(orderId)` con `ApplicationEventPublisher`;
  `OrderReadyListener` (en `delivery`) lo recibe en la **misma transacción** (`@EventListener`, no `AFTER_COMMIT`)
  y crea la fila `entregas`; el despacho posterior usa `@TransactionalEventListener(AFTER_COMMIT)`.
- **Razón**: la entrega existe si y solo si el pedido quedó `READY`; `order` no importa `delivery` (sin ciclos).

## 6. Estado y registro de cambios

- **Decisión**: el estado visible es el del pedido (`READY`, `ASSIGNED`, `PICKED_UP`, `DELIVERED`); «pendiente de
  asignación» = pedido `READY` con `entregas.courier_id IS NULL`. Se reutiliza `cambios_estado_pedido` con
  `ActorRole` ampliado (`COURIER`, `ADMIN`, `SYSTEM` para la asignación automática). El rechazo registra
  `ASSIGNED → READY` con actor `COURIER` y motivo opcional.
- **Razón**: un único estado y un único registro evitan divergencias (FR-014) sin crear una tabla de eventos.
- **Alternativa**: estado propio de `Delivery` duplicado (riesgo de incoherencia con el pedido).

## 7. Aceptar y recoger

- **Decisión**: una sola operación `pickup` (ASSIGNED → PICKED_UP) que equivale a aceptar y registrar la recogida
  (Assumptions de la spec); `reject` solo en `ASSIGNED`.
- **Razón**: la spec no define estado «Aceptado» de la entrega; añadirlo sería un salto no especificado
  (constitución IV).

## 8. Idempotencia

- **Decisión**: un reenvío ya aplicado encuentra el estado distinto y responde 409 `INVALID_TRANSITION` sin
  duplicar nada (mismo criterio que 007). La asignación condicional impide doble asignación en reintentos.

## 9. Permisos

- **Decisión**: `SecurityConfig` añade `/api/courier/**` → `hasRole("COURIER")`; `/api/admin/**` ya exige ADMIN.
  Entrega que no es del repartidor del token → 404.
- **Razón**: misma convención que `/api/restaurant/**` y `/api/customer/**` (RF-04, RNF-07).
