# Validación: ubicación, seguimiento y avisos (009)

**Fecha**: 2026-11-18. Distingue lo verificado automáticamente de lo pendiente (constitución VI).

## Verificado

| Requisito | Evidencia |
| --- | --- |
| RF-26 ubicación del repartidor | `LocationIntegrationTest` (una sola fila vigente, una más antigua no reemplaza a la nueva, 409 sin entrega en curso o ya entregada, 400 coordenadas o hora inválidas sin modificar la vigente, 401/403, dos hitos y ninguna vigente tras recoger y entregar, sin hito si no hubo ubicación, borrado al rechazar), `LocationServiceTest`, `DeliveryMilestoneListenerTest` |
| RF-27 estado del pedido | `TrackingIntegrationTest` (estado y etapas en orden, rechazado y cancelado, pedido ajeno o inexistente 404, 401/403, sin actores en la respuesta), `TrackingServiceTest` |
| RF-28 ubicación visible al cliente | `TrackingLocationIntegrationTest` (Asignado sin coordenadas, Recogido con coordenadas y hora y actualización, `NO_LOCATION_YET`, Entregado `FINISHED`, tras un rechazo no se muestra la del repartidor anterior, otro cliente 404 y restaurante, repartidor, administrador 403, anónimo 401), `TrackingLocationTest` |
| RF-29 avisos | `NotificationIntegrationTest` (pedido nuevo, flujo completo hasta Entregado por rol, reasignación con segundo «Asignado», cancelación, rechazo, paginación y orden, leído y contador, avisos ajenos 404, sin borrado, 401/403, **el cambio de estado se completa aunque falle el guardado del aviso**), `NotificationRecipientsTest`, `NotificationListenerTest` (3 intentos, duplicado ignorado, sin propagar errores), `OrderPlacedEventIntegrationTest` (un evento por pedido nuevo; el reenvío idempotente no publica otro), `OrderStatusEventTest` |
| FR-006 sin historial de ubicación | La tabla `ubicaciones_repartidor` tiene una fila por repartidor; solo `hitos_entrega` es permanente (una por recogida y una por entrega) |
| FR-009 privacidad | Solo el cliente dueño y solo en `PICKED_UP` (ver RF-28) |
| FR-013 / FR-014 | Fallo simulado del guardado y reenvío de la misma confirmación (ver RF-29) |
| RNF-07 (alcance del incremento) | 401 sin token y 403 con otro rol en `PUT /api/courier/location`, `GET …/tracking` y `/api/notifications/**` (administrador) |
| RNF-11 cobertura | Backend (`mvnw verify`): 591 pruebas, 0 fallos; líneas `order` 98,5 % (336/341), `delivery` 96,4 % (292/303), `notification` 96,1 % (124/129). Frontend: 87 pruebas; líneas 98,7 % (umbral 90 %) |

Compilación del backend, `npm run build` y ESLint del frontend sin errores.

## Corregido durante la implementación

- La eliminación masiva de la ubicación vigente con `clearAutomatically` desacoplaba el pedido en curso y rompía la
  entrega (`LazyInitializationException` en `complete`); se quitó esa opción.
- Los avisos se leían con el contexto de persistencia del commit de origen, que conservaba la entrega sin repartidor
  tras la asignación masiva; la resolución de destinatarios usa ahora una transacción nueva de solo lectura.

## Validación manual (T046)

- **Recorrido del [quickstart](./quickstart.md) en el navegador** (geolocalización del repartidor, sondeo en
  `OrderPage`, campana de avisos y página `/avisos`): probado a mano por el responsable del proyecto, que confirmó
  el resultado el 2026-11-18 (declaración del usuario; sin prueba de navegador automatizada, aplazada por la
  constitución).

## Pendiente (no declarado como cumplido)

- Historial de operaciones (spec 010) y vistas de administración (spec 011).
- RNF-01/RNF-10 (carga y monitoreo), RNF-06/RNF-08 (despliegue) y CI/CD siguen aplazados.
- **Límite conocido**: un aviso se pierde si el proceso cae entre el commit del cambio y su escritura (compromiso de
  la spec); el cambio de estado nunca depende del aviso.
