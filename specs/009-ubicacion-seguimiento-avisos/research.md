# Research: Ubicación, seguimiento y avisos

Decisiones para el incremento 009; sin comparativas generales (constitución VII).

## 1. Actualización sin recargar: sondeo de 5 s

- **Decision**: el cliente consulta `GET /api/customer/orders/{id}/tracking` cada 5 s mientras la pestaña esté
  visible; la campana de avisos sondea el contador cada 15 s.
- **Rationale**: SC-002 pide < 10 s. El token JWT va en cabecera y `EventSource` no la admite; WebSocket/SSE
  exigiría infraestructura nueva (constitución I y VII). El sondeo reutiliza la seguridad existente.
- **Alternatives**: SSE con token en la URL (filtra el token), WebSocket (STOMP, dependencia y configuración
  nuevas). Descartadas.

## 2. Ubicación vigente: una fila por repartidor, el más reciente gana

- **Decision**: tabla `ubicaciones_repartidor` con `courier_id` como clave; el envío hace
  `INSERT ... ON CONFLICT (courier_id) DO UPDATE ... WHERE recorded_at < EXCLUDED.recorded_at`. Se guarda también
  `order_id` de la entrega en curso.
- **Rationale**: FR-003/005/006: sin historial, orden garantizado en PostgreSQL aunque lleguen peticiones
  desordenadas (constitución IV).
- **Alternatives**: leer-y-escribir en Java (carrera), historial con purga (viola FR-006).

## 3. Mapa: coordenadas y enlace, sin proveedor externo

- **Decision**: se muestran latitud/longitud, la hora de actualización y un enlace «Abrir en mapa» (enlace
  normal a una URL de mapas por coordenadas). No se incrusta ningún mapa ni se llama a una API de mapas.
- **Rationale**: la constitución III solo permite Cloudinary, correo y Stripe; incrustar teselas es una
  integración nueva. La spec exige «ver la ubicación», no un mapa propio.
- **Alternatives**: Leaflet/OpenStreetMap o Google Maps (integración externa no permitida sin enmienda).

## 4. Avisos: evento tras el commit, nueva transacción, 3 intentos

- **Decision**: `OrderStatusChangedEvent`/`OrderPlacedEvent` se consumen en un
  `@TransactionalEventListener(AFTER_COMMIT)` que abre una transacción nueva y, por cada destinatario, intenta
  guardar el aviso hasta 3 veces; si falla, registra un `WARN` (sin datos personales) y continúa.
- **Rationale**: FR-013: el aviso nunca bloquea ni revierte el cambio; se ejecuta solo si el cambio se confirmó.
- **Alternatives**: aviso dentro de la misma transacción (un fallo revertiría el estado), cola/outbox (infraestructura
  anticipada).

## 5. No duplicados

- **Decision**: `source_key` = id del `OrderStatusChange` (o `PLACED:{orderId}`) y restricción única
  `(source_key, recipient_id)`; un duplicado se ignora.
- **Rationale**: FR-014. Un mismo pedido puede pasar dos veces por «Asignado» (reasignación) y debe generar dos avisos
  distintos: por eso la clave es el cambio, no el estado.

## 6. Regla de destinatarios (fija, en código)

| Cambio | Cliente | Restaurante | Repartidor |
| --- | --- | --- | --- |
| Pedido creado | — | ✅ | — |
| Aceptado / En preparación / Listo / Rechazado | ✅ | — | — |
| Cancelado | ✅ si no lo originó | ✅ | ✅ si hay repartidor asignado |
| Asignado | ✅ | ✅ | ✅ |
| Recogido / Entregado | ✅ | ✅ | — |
| Asignado → Listo (rechazo del repartidor) | — | — | — |

Quien origina la acción no recibe su propio aviso (por su rol y `actorId`). El rechazo de una asignación no
genera aviso: el pedido solo se reasigna y el siguiente «Asignado» sí lo hace.

## 7. Hito y borrado de la ubicación vigente

- **Decision**: `DeliveryMilestoneListener` (`@EventListener` síncrono, misma transacción) en `PICKED_UP` y
  `DELIVERED` copia la ubicación vigente a `hitos_entrega`; en `DELIVERED`, `CANCELLED` y `READY` (liberación)
  borra la vigente por `order_id`. Además la consulta del cliente exige estado `PICKED_UP` y que la ubicación
  pertenezca al repartidor asignado, de modo que un resto nunca se muestra.
- **Rationale**: FR-006 y FR-009 con atomicidad respecto al cambio de estado.

## 8. Envío desde el navegador

- **Decision**: la interfaz del repartidor usa la geolocalización del navegador y envía como máximo una
  ubicación cada 10 s mientras haya entrega Asignada/Recogida; el servidor valida rango (lat −90..90,
  lon −180..180), marca temporal no futura (tolerancia 1 min) y entrega en curso.
- **Rationale**: aplicación móvil nativa fuera de alcance; consentimiento del repartidor mediante el permiso del
  navegador.
- **Nota**: se confía en el reloj del dispositivo para `recordedAt`, solo con tope futuro de 1 minuto; un reloj
  muy atrasado se mostrará como ubicación antigua.
