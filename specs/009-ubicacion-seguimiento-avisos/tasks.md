---

description: "Tareas de implementación: ubicación, seguimiento y avisos (RF-26, RF-27, RF-28, RF-29, RF-04)"
---

# Tasks: Ubicación, seguimiento y avisos

**Input**: Documentos de diseño en `specs/009-ubicacion-seguimiento-avisos/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/tracking-api.md](./contracts/tracking-api.md),
[quickstart.md](./quickstart.md)

**Tests**: **Obligatorias** (constitución V y RNF-11): JUnit + Mockito, integración contra PostgreSQL local
(`mikuna_test_db`) y ≥ 90 % de líneas de lógica de negocio. Vitest para la lógica del frontend. Sin Playwright ni
otra herramienta E2E; sin Stripe (este incremento no toca el pago).

**Organization**: Tareas agrupadas por historia. US1 (estado del pedido) y US2 (avisos) son P1 y el MVP; US3
(envío de ubicación) y US4 (cliente ve al repartidor) son P2; US4 depende de US1 y US3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (otros archivos, sin dependencias pendientes)
- **[Story]**: US1–US4 según [spec.md](./spec.md)
- Rutas abreviadas: `BE/` = `backend/src/main/java/com/mikuna/backend/`; `BT/` =
  `backend/src/test/java/com/mikuna/backend/`; `FE/` = `frontend/src/`.
- Nunca abrir, copiar ni imprimir `backend/.env`.
- Las pruebas de integración extienden `IntegrationTestSupport` / `OrderStatusTestSupport` (`BT/support/`) y usan
  `TestAccounts`, `TestOrders` y `TestRestaurants`; revisar sus utilidades antes de crear otras.

---

## Phase 1: Setup

Sin tareas: no hay dependencias nuevas ni configuración (plan: «ninguna nueva»).

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: eventos del pedido, permisos y errores comunes.

- [X] T001 [P] Crear `BE/order/model/OrderStatusChangedEvent.java` (record: `changeId` UUID, `orderId` UUID, `from` y `to` `OrderStatus`, `actorId` UUID nulo, `actorRole` `ActorRole`) y `BE/order/model/OrderPlacedEvent.java` (record: `orderId`).
- [X] T002 En `BE/order/service/OrderStatusService.java`, método `changeStatus`, publicar con `ApplicationEventPublisher` un `OrderStatusChangedEvent` tras guardar el `OrderStatusChange` (usar el id ya asignado del registro guardado), dentro de la misma transacción. No cambiar firmas ni comportamiento existente.
- [X] T003 En `BE/order/service/CheckoutTransaction.java` publicar `OrderPlacedEvent` solo cuando el pedido se crea (confirmación nueva); un reenvío idempotente (`created=false`) NO publica nada.
- [X] T004 [P] Crear `BE/common/exception/LocationRejectedException.java` (409, `code=LOCATION_REJECTED`, mensaje «No tienes una entrega en curso») y registrar su handler en `BE/common/exception/GlobalExceptionHandler.java`; `OrderNotFoundException` (404) ya existe.
- [X] T005 En `BE/common/security/SecurityConfig.java` añadir `.requestMatchers("/api/notifications/**").hasAnyRole("CUSTOMER", "RESTAURANT", "COURIER")` antes de la regla genérica (el administrador recibe 403); las reglas `/api/courier/**` y `/api/customer/**` ya existen.
- [X] T006 Verificar que `order` no importa nada de `delivery` ni de `notification` (sin ciclos, constitución I): solo publica eventos.
- [X] T007 [P] Prueba unitaria en `BT/order/service/OrderStatusEventTest.java` (Mockito): `changeStatus` publica un `OrderStatusChangedEvent` con `changeId`, estados y actor correctos; y en `BT/order/OrderPlacedEventIntegrationTest.java`: confirmar un pedido publica `OrderPlacedEvent` una vez y repetir la misma `Idempotency-Key` no publica otro.

**Checkpoint**: eventos publicados, permisos y errores listos.

---

## Phase 3: User Story 1 — El cliente consulta el estado de su pedido (P1) 🎯 MVP

**Goal**: el cliente dueño ve estado actual y etapas cumplidas con fecha y hora (RF-27).

**Independent Test**: recorrer un pedido por varios estados y consultar `GET /api/customer/orders/{id}/tracking`:
estado actual y una etapa por cada cambio, en orden; pedido ajeno 404; sin sesión 401; otro rol 403.

- [X] T008 [P] [US1] Crear `BE/order/dto/TrackingResponse.java` según [contracts/tracking-api.md](./contracts/tracking-api.md): `orderId`, `status`, `stages` (lista de `status` y `at`, sin actores), `courierLocation` (`latitude`, `longitude`, `updatedAt`, nulo) y `locationUnavailableReason` (`NOT_PICKED_UP` | `FINISHED` | `NO_LOCATION_YET`, nulo). En esta historia `courierLocation` siempre es nulo.
- [X] T009 [US1] Crear `BE/order/service/TrackingService.java`: dado `customerId` y `orderId`, buscar el pedido con `OrderRepository` y comprobar que sea del cliente (ajeno o inexistente → `OrderNotFoundException`); armar las etapas desde `OrderStatusChangeRepository` ordenadas por `changed_at` (cada cambio aporta su estado nuevo y fecha; la etapa inicial `PENDING` usa `createdAt` del pedido), sin exponer `actorId` ni rol. Usar `@Transactional(readOnly = true)`.
- [X] T010 [US1] En `BE/order/controller/OrderController.java` añadir `GET /{id}/tracking` que delega en `TrackingService` con `principal.id()` (cliente del token). Solo HTTP, sin reglas.
- [X] T011 [P] [US1] Pruebas unitarias en `BT/order/service/TrackingServiceTest.java` (Mockito): etapas en orden, etapa inicial, pedido ajeno → `OrderNotFoundException`, sin actores en la respuesta.
- [X] T012 [US1] Pruebas de integración en `BT/order/TrackingIntegrationTest.java`: estado y etapas tras aceptar, preparar y marcar listo; cancelado y rechazado; 404 pedido de otro cliente e inexistente; 401 sin sesión; 403 con rol restaurante, repartidor y administrador.
- [X] T013 [P] [US1] Crear `FE/features/checkout/tracking-api.ts` (`getTracking(orderId)` con el token de sesión, como `checkout-api.ts`) y `FE/features/checkout/tracking-logic.ts` (etiquetas en español de cada estado y formato de fecha de cada etapa); prueba Vitest en `FE/features/checkout/tracking-logic.test.ts`.
- [X] T014 [US1] En `FE/features/checkout/OrderPage.tsx` mostrar el estado actual y la lista de etapas con fecha y hora desde `getTracking`, y repetir la consulta cada 5 s mientras la pestaña esté visible (detener el sondeo cuando el pedido sea final: Entregado, Rechazado o Cancelado). Manejar 404 con un mensaje claro.

**Checkpoint**: US1 funciona sola; el cliente consulta el estado de su pedido.

---

## Phase 4: User Story 2 — Avisos ante cambios del pedido y la entrega (P1) 🎯 MVP

**Goal**: cada cambio relevante registra avisos para los participantes que correspondan; el usuario los consulta,
distingue no leídos y los marca (RF-29).

**Independent Test**: recorrer un pedido de Pendiente a Entregado y comprobar los avisos de cada rol según la
tabla de [research.md](./research.md) §6, sin duplicados; consulta, contador y lectura solo de los propios; el
cambio de estado se completa aunque falle el guardado del aviso.

- [X] T015 [P] [US2] Crear `BE/notification/model/NotificationType.java` (enum: `NEW_ORDER, ACCEPTED, REJECTED, CANCELLED, PREPARING, READY, ASSIGNED, PICKED_UP, DELIVERED`) y `BE/notification/model/Notification.java` (tabla `avisos`): `id` UUID generado, `recipientId` UUID no nulo, `orderId` UUID no nulo, `type` enum no nulo, `message` varchar(200) no nulo, `sourceKey` varchar(80) no nulo, `createdAt` Instant no nulo, `readAt` Instant nulo (nulo = no leído); **único `(source_key, recipient_id)`**; índices `(recipient_id, created_at DESC)` y `(recipient_id, read_at)`.
- [X] T016 [US2] Crear `BE/notification/repository/NotificationRepository.java`: página de avisos por `recipientId` ordenados por `createdAt` descendente (con variante solo no leídos), conteo de no leídos por `recipientId`, búsqueda por `id` y `recipientId`, y `UPDATE` que marca leídos los no leídos de un `recipientId` (`readAt IS NULL`).
- [X] T017 [P] [US2] Crear `BE/notification/dto/NotificationResponse.java` (`id`, `orderId`, `type`, `message`, `createdAt`, `read`), `BE/notification/dto/UnreadCountResponse.java` (`unread`) y devolver la página con el record existente `BE/catalog/dto/PageResponse.java` (`items`, `page`, `size`, `totalItems`, `totalPages`), sin crear uno propio.
- [X] T018 [US2] Crear `BE/notification/service/NotificationRecipients.java` con la regla fija de destinatarios de [research.md](./research.md) §6: de un `OrderStatusChangedEvent` o `OrderPlacedEvent` devuelve pares (destinatario, tipo, mensaje en español) para cliente (`Order.customerId`), restaurante (`Restaurant.ownerId` del `restaurantId` del pedido) y repartidor (`Delivery.courierId` si no es nulo). Excluir a quien originó la acción (`actorId`). `PENDING→ACCEPTED/REJECTED/PREPARING/READY` solo cliente; `CANCELLED` cliente (si no lo originó), restaurante y repartidor asignado; `ASSIGNED` los tres; `PICKED_UP` y `DELIVERED` cliente y restaurante; `ASSIGNED→READY` (rechazo del repartidor) ninguno; `OrderPlacedEvent` solo restaurante.
- [X] T019 [US2] Crear `BE/notification/service/NotificationListener.java`: métodos `@TransactionalEventListener(phase = AFTER_COMMIT)` para ambos eventos que, para cada destinatario de `NotificationRecipients`, llaman a un guardado en transacción nueva (`REQUIRES_NEW`) con `sourceKey` = id del cambio (o `PLACED:{orderId}`); hasta 3 intentos por aviso; un duplicado por la restricción única se ignora sin contar como fallo; si tras los intentos sigue fallando, `WARN` con tipo, orden y destinatario (sin datos personales) y continuar. Nunca propagar excepciones.
- [X] T020 [US2] Crear `BE/notification/service/NotificationService.java`: `list(accountId, unreadOnly, page, size)` (tamaño máximo 50), `unreadCount(accountId)`, `markRead(accountId, id)` (idempotente; ajeno o inexistente → `OrderNotFoundException` para dar 404) y `markAllRead(accountId)`, con transacciones.
- [X] T021 [US2] Crear `BE/notification/controller/NotificationController.java` en `/api/notifications` con `GET` (parámetros `unreadOnly`, `page`, `size`), `GET /unread-count`, `POST /{id}/read` (204) y `POST /read-all` (204) según [contracts/tracking-api.md](./contracts/tracking-api.md); el usuario sale de `@AuthenticationPrincipal AccountPrincipal`.
- [X] T022 [P] [US2] Pruebas unitarias en `BT/notification/service/NotificationRecipientsTest.java`: cada fila de la tabla de destinatarios, exclusión de quien origina la acción, repartidor solo si hay asignado, rechazo de asignación sin avisos.
- [X] T023 [P] [US2] Pruebas unitarias en `BT/notification/service/NotificationListenerTest.java` (Mockito): reintenta hasta 3 veces y luego registra y sigue; un duplicado se ignora; ninguna excepción sale del listener.
- [X] T024 [US2] Pruebas de integración en `BT/notification/NotificationIntegrationTest.java`: pedido nuevo avisa al restaurante; aceptar, preparar y listo avisan al cliente; asignar avisa a los tres; recoger y entregar a cliente y restaurante; reasignación genera un segundo aviso «Asignado»; reenviar la misma confirmación no duplica; el cambio de estado se completa aunque el guardado del aviso falle (forzar el error en el repositorio con un bean de prueba); consulta ordenada, paginada, solo no leídos y contador; marcar leído baja el contador; avisos ajenos 404; no existe ningún endpoint para borrar avisos (FR-016); 401 sin sesión; 403 administrador.
- [X] T025 [P] [US2] Crear `FE/features/notifications/notifications-api.ts` (listar, contador, marcar leído, marcar todos) y `FE/features/notifications/useUnreadCount.ts` (sondeo del contador cada 15 s con la pestaña visible); prueba Vitest en `FE/features/notifications/useUnreadCount.test.ts`.
- [X] T026 [US2] Crear `FE/features/notifications/NotificationsPage.tsx` en `/avisos` (lista paginada del más reciente al más antiguo, no leídos resaltados, filtro «solo no leídos», abrir un aviso lo marca leído y enlaza a su pedido, botón «Marcar todos como leídos») y registrar la ruta en `FE/App.tsx` para cliente, restaurante y repartidor.
- [X] T027 [US2] Mostrar en la cabecera de sesión (componente donde ya vive el menú de cuenta; localizarlo en `FE/features/auth/` o `FE/features/home/components/SiteHeader.tsx`) una campana con el contador de `useUnreadCount` y enlace a `/avisos`, solo para los tres roles destinatarios.

**Checkpoint**: US1 y US2 completas (MVP): estado del pedido y avisos funcionando.

---

## Phase 5: User Story 3 — El repartidor comparte su ubicación (P2)

**Goal**: con una entrega en curso, el repartidor envía su ubicación y el sistema conserva solo la última;
recogida y entrega dejan un registro permanente (RF-26).

**Independent Test**: con una entrega Asignada, enviar varias ubicaciones y comprobar una sola fila vigente; la
más antigua no reemplaza a la nueva; tras recoger y entregar hay un hito de cada tipo y ninguna ubicación vigente.

- [X] T028 [P] [US3] Crear `BE/delivery/model/CourierLocation.java` (tabla `ubicaciones_repartidor`): `courierId` UUID **PK**, `orderId` UUID no nulo, `latitude` double no nulo (−90 a 90), `longitude` double no nulo (−180 a 180), `recordedAt` Instant no nulo.
- [X] T029 [P] [US3] Crear `BE/delivery/model/MilestoneType.java` (`PICKUP, DELIVERY`) y `BE/delivery/model/DeliveryMilestone.java` (tabla `hitos_entrega`): `id` UUID generado, `orderId` no nulo, `type` no nulo, `latitude` y `longitude` double no nulos, `recordedAt` no nulo, `createdAt` no nulo; **único `(order_id, type)`**; solo inserción.
- [X] T030 [US3] Crear `BE/delivery/repository/CourierLocationRepository.java` con un `upsert` nativo `INSERT ... ON CONFLICT (courier_id) DO UPDATE SET order_id, latitude, longitude, recorded_at ... WHERE ubicaciones_repartidor.recorded_at < EXCLUDED.recorded_at`, `findByCourierId`, y `deleteByOrderId`; y `BE/delivery/repository/DeliveryMilestoneRepository.java`.
- [X] T031 [P] [US3] Crear `BE/delivery/dto/LocationRequest.java` con `@NotNull` y `@DecimalMin/@DecimalMax` para `latitude` (−90 a 90) y `longitude` (−180 a 180) y `@NotNull Instant recordedAt`.
- [X] T032 [US3] Crear `BE/delivery/service/LocationService.java`: `update(courierId, request)` valida que `recordedAt` no sea posterior a ahora + 1 min (si lo es, `RegistrationValidationException` en el campo `recordedAt`), busca la entrega en curso del repartidor (`Delivery.courierId` con pedido `ASSIGNED` o `PICKED_UP`; si no hay, `LocationRejectedException`) y hace el `upsert` con el `orderId` de esa entrega; una ubicación más antigua se ignora sin error.
- [X] T033 [US3] En `BE/delivery/controller/CourierController.java` añadir `PUT /api/courier/location` (204) que delega en `LocationService` con el repartidor del token.
- [X] T034 [US3] Crear `BE/delivery/service/DeliveryMilestoneListener.java` con `@EventListener` síncrono (misma transacción) sobre `OrderStatusChangedEvent`: en `to = PICKED_UP` y `to = DELIVERED` copiar la ubicación vigente del repartidor de la entrega a `hitos_entrega` (`PICKUP`/`DELIVERY`; si no hay ubicación vigente no crea el hito y no falla); en `DELIVERED`, `CANCELLED` y en `ASSIGNED→READY` borrar la vigente por `orderId` con `deleteByOrderId`.
- [X] T035 [P] [US3] Pruebas unitarias en `BT/delivery/service/LocationServiceTest.java` (Mockito): coordenadas fuera de rango, `recordedAt` futuro, sin entrega en curso → `LocationRejectedException`, entrega `ASSIGNED` y `PICKED_UP` válidas; y `BT/delivery/service/DeliveryMilestoneListenerTest.java`: hito al recoger y al entregar, sin hito sin ubicación, borrado de la vigente.
- [X] T036 [US3] Pruebas de integración en `BT/delivery/LocationIntegrationTest.java`: varias ubicaciones dejan una sola fila; una más antigua no reemplaza a la nueva; 409 sin entrega en curso o con la entrega `DELIVERED`; 400 coordenadas inválidas sin modificar la fila; 401 sin sesión y 403 con otro rol; tras recoger y entregar existen exactamente dos hitos y ninguna fila en `ubicaciones_repartidor`; al reasignar (rechazo) la vigente desaparece.
- [X] T037 [P] [US3] Crear `FE/features/delivery/location-sender.ts` (envío con `navigator.geolocation.watchPosition`, como máximo una ubicación cada 10 s, solo con entrega Asignada o Recogida, se detiene al terminar; errores de permiso o red se informan sin romper la pantalla) y añadir `sendLocation` a `FE/features/delivery/courier-api.ts`; prueba Vitest en `FE/features/delivery/location-sender.test.ts` (limitación a 10 s y detención).
- [X] T038 [US3] En `FE/features/delivery/CourierPage.tsx` iniciar el envío de ubicación mientras haya una entrega en curso y mostrar si se está compartiendo, el permiso denegado o la última hora enviada.

**Checkpoint**: US3 completa: ubicación vigente, hitos permanentes y sin historial.

---

## Phase 6: User Story 4 — El cliente ve al repartidor en camino (P2)

**Goal**: con el pedido Recogido, el cliente dueño ve la ubicación vigente y su hora, que se actualiza sin recargar;
nadie más la ve (RF-28).

**Independent Test**: con un pedido Recogido y ubicaciones nuevas, el cliente dueño ve coordenadas renovadas en
< 10 s; con pedido Asignado, Entregado o Cancelado, o con otro usuario, no hay coordenadas.

- [X] T039 [US4] Ampliar `BE/order/service/TrackingService.java` para rellenar `courierLocation` solo si el pedido es del cliente del token **y** su estado es `PICKED_UP` **y** existe una ubicación vigente del repartidor asignado a esa entrega (`ubicaciones_repartidor.courier_id = entregas.courier_id` y `order_id` del pedido), devolviendo `latitude`, `longitude` y `recordedAt` como `updatedAt`; en otro caso `courierLocation` nulo y `locationUnavailableReason` = `NOT_PICKED_UP` (aún no recogido, incluido Asignado), `FINISHED` (Entregado, Cancelado o Rechazado) o `NO_LOCATION_YET` (Recogido sin ubicación). Para no crear una dependencia de `order` hacia `delivery`, definir en `order` una interfaz mínima `CourierLocationLookup` (`Optional<…> latestFor(UUID orderId)`) que implementa `delivery` en `BE/delivery/service/CourierLocationLookupImpl.java`.
- [X] T040 [P] [US4] Pruebas unitarias en `BT/order/service/TrackingLocationTest.java` (Mockito): cada estado del pedido produce la ubicación o el motivo esperado; sin ubicación en Recogido → `NO_LOCATION_YET`; ubicación de un repartidor distinto del asignado no se devuelve.
- [X] T041 [US4] Pruebas de integración en `BT/order/TrackingLocationIntegrationTest.java`: Asignado → sin coordenadas (`NOT_PICKED_UP`); Recogido con ubicación → coordenadas y hora; nueva ubicación → valores actualizados; Entregado → `FINISHED` sin coordenadas; reasignación tras rechazo → no muestra la ubicación del repartidor anterior; cliente ajeno 404, restaurante y administrador 403, anónimo 401, sin coordenadas en ningún caso.
- [X] T042 [P] [US4] En `FE/features/checkout/tracking-logic.ts` añadir el texto de cada `locationUnavailableReason` en español, el formato de antigüedad («actualizada hace N s/min») y la URL del enlace «Abrir en mapa» por coordenadas; ampliar `FE/features/checkout/tracking-logic.test.ts`.
- [X] T043 [US4] En `FE/features/checkout/OrderPage.tsx` mostrar, con el pedido Recogido, latitud, longitud, antigüedad y el enlace «Abrir en mapa» (enlace normal, sin incrustar mapa ni llamar a servicios externos); mostrar el motivo cuando no hay ubicación; la renovación ya la hace el sondeo de T014. Al reconectar tras un fallo de red debe recuperar el estado actual.

**Checkpoint**: US4 completa: seguimiento en vivo con privacidad verificada.

---

## Phase 7: Polish & Cross-Cutting

- [X] T044 Ejecutar `mvnw verify` en `backend/` y comprobar el reporte JaCoCo de lógica de negocio (`order`, `delivery`, `notification`) ≥ 90 % de líneas; no excluir reglas para alcanzarlo (constitución V). Si baja, añadir pruebas, no exclusiones.
- [X] T045 Ejecutar `npm test` y `npm run build` en `frontend/` y corregir fallos de tipos o pruebas.
- [X] T046 Recorrer el [quickstart.md](./quickstart.md) a mano con las cuentas de prueba y anotar el resultado en `specs/009-ubicacion-seguimiento-avisos/validation.md` (cumplimiento por RF-26 a RF-29: pendiente, parcial o verificado; RNF-01 y RNF-06 siguen aplazados, constitución VI).
- [X] T047 Revisar que no se registran ni devuelven coordenadas de otros usuarios en logs ni respuestas de error, y que los avisos no incluyen datos personales de otras partes (nombre, teléfono, dirección completa del cliente) en el mensaje.

---

## Dependencies & Execution Order

- **Foundational (T001–T007)** bloquea todo.
- **US1 (T008–T014)** y **US2 (T015–T027)** son independientes entre sí y se pueden hacer en paralelo tras la fase 2.
- **US3 (T028–T038)** es independiente de US1/US2 (solo necesita el evento de T001/T002 para el hito).
- **US4 (T039–T043)** depende de US1 (T009) y US3 (T028–T034).
- **Polish (T044–T047)** al final.

## Parallel Examples

- Tras T001–T007: T008, T015, T028 y T029 en paralelo (archivos distintos).
- Dentro de US2: T015, T017, T022, T023 y T025 en paralelo; T018→T019→T020→T021 en orden.
- Dentro de US3: T028, T029, T031, T035 y T037 en paralelo; T030→T032→T033 en orden.

## Implementation Strategy

1. **MVP**: Foundational + US1 + US2 → el cliente conoce el estado y todos reciben avisos.
2. **Incremento 2**: US3 → el repartidor comparte ubicación y quedan los hitos.
3. **Incremento 3**: US4 → el cliente ve al repartidor en camino.
4. Validar cada historia con su prueba independiente antes de pasar a la siguiente; cerrar con la fase 7.
