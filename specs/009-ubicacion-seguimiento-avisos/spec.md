# Feature Specification: Ubicación, seguimiento y avisos

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-18

**Status**: Draft

**Input**: Incremento 009 del usuario: RF-26, RF-27, RF-28 y RF-29. Depende de la spec 002 (cuentas y
permisos), 006 (pedido), 007 (estados de preparación) y 008 (asignación y entrega: Asignado, Recogido y
Entregado). Fuera de alcance: cambios de estado y asignación (specs 007 y 008), historial de operaciones
(spec 010), vistas de administración (spec 011), optimización de rutas y tiempo estimado de llegada, y
aplicaciones móviles nativas.

## Clarifications

### Session 2026-11-18

- Q: ¿Desde qué estado el cliente puede ver la ubicación del repartidor? → A: Solo en Recogido (en camino)
  hasta Entregado; el repartidor envía ubicación desde Asignado, pero el cliente no la ve hasta la recogida.
- Q: Si un aviso falla al registrarse o enviarse, ¿se reintenta? → A: Sí, automáticamente un número limitado
  de veces; si sigue fallando se descarta y queda constancia del fallo.
- Q: ¿El repartidor asignado recibe aviso si el cliente cancela el pedido? → A: Sí, recibe un aviso de
  cancelación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El cliente consulta el estado de su pedido (Priority: P1)

Como cliente, quiero consultar en cualquier momento el estado actual de mi pedido y la información de cada
etapa por la que pasó, para saber qué ocurre con él sin llamar al restaurante.

**Why this priority**: Es el valor básico del seguimiento (RF-27) y no depende de la ubicación ni de los avisos.

**Independent Test**: Con un pedido que recorrió varios estados, el cliente dueño lo consulta y ve el estado
actual y cada etapa con su fecha y hora.

**Acceptance Scenarios**:

1. **Given** un cliente con un pedido en cualquier estado, **When** lo consulta, **Then** ve el estado actual
   y las etapas ya cumplidas con su fecha y hora.
2. **Given** un pedido que cambia de estado, **When** el cliente vuelve a consultarlo, **Then** ve el estado
   nuevo.
3. **Given** un pedido de otro cliente, **When** un cliente intenta consultarlo, **Then** se deniega sin
   revelar si existe.
4. **Given** una persona sin sesión, **When** intenta consultar un pedido, **Then** se deniega.

---

### User Story 2 - Avisos ante cambios del pedido y la entrega (Priority: P1)

Como cliente, restaurante o repartidor, quiero recibir un aviso cuando ocurre un cambio relevante del
pedido o la entrega que me concierne, y poder consultarlos después distinguiendo los no leídos, para
enterarme sin estar pendiente de la pantalla.

**Why this priority**: Cubre RF-29 y mantiene informados a todos los participantes.

**Independent Test**: Recorrer un pedido desde su confirmación hasta Entregado y comprobar que cada
participante recibe los avisos que le corresponden, queda registrado cada uno y puede marcarlos como leídos.

**Acceptance Scenarios**:

1. **Given** un cambio relevante del pedido o la entrega (nuevo pedido, aceptado, rechazado, cancelado,
   en preparación, listo para recoger, asignado, recogido, entregado), **When** ocurre, **Then** se registra
   un aviso para cada participante al que corresponda: cliente, restaurante y/o repartidor.
2. **Given** un usuario con avisos, **When** los consulta, **Then** ve solo los suyos, del más reciente al
   más antiguo, con su mensaje, fecha y hora, y distingue los no leídos de los leídos.
3. **Given** un aviso no leído, **When** el usuario lo abre o lo marca como leído, **Then** deja de contar
   como no leído y el contador de no leídos se actualiza.
4. **Given** avisos de otro usuario, **When** alguien intenta consultarlos o marcarlos, **Then** se deniega.
5. **Given** un fallo al registrar o enviar un aviso, **When** se produce un cambio de estado, **Then** el
   cambio igual se completa y no se revierte.
6. **Given** un cambio de estado repetido o reenviado, **When** se procesa, **Then** no se duplican los avisos.

---

### User Story 3 - El repartidor comparte su ubicación durante la entrega (Priority: P2)

Como repartidor con una entrega en curso, quiero que mi ubicación se envíe periódicamente, para que el
cliente sepa dónde estoy.

**Why this priority**: Es la fuente de datos de la ubicación (RF-26) que consume la historia siguiente.

**Independent Test**: Con una entrega Asignada o Recogida, el repartidor envía varias ubicaciones y el
sistema conserva solo la última; al entregar, la ubicación deja de aceptarse.

**Acceptance Scenarios**:

1. **Given** un repartidor con una entrega en curso (Asignada o Recogida), **When** envía su ubicación,
   **Then** el sistema conserva la ubicación vigente (la última recibida) con su fecha y hora, reemplazando la anterior.
2. **Given** un repartidor sin entrega en curso, o con la entrega ya Entregada, **When** envía su
   ubicación, **Then** se rechaza con un mensaje claro y no se guarda.
3. **Given** coordenadas inválidas (fuera de rango o incompletas), **When** se envían, **Then** se rechazan
   sin modificar la ubicación vigente.
4. **Given** una persona sin sesión o con rol distinto de repartidor, **When** envía una ubicación,
   **Then** se deniega sin efecto.
5. **Given** que el repartidor registra la recogida y la entrega, **When** ocurre cada una, **Then** su
   ubicación en ese momento se registra de forma permanente junto con el hecho; el resto de ubicaciones no
   se conserva como historial.

---

### User Story 4 - El cliente ve al repartidor en camino (Priority: P2)

Como cliente cuyo pedido está en camino, quiero ver la ubicación del repartidor y que se actualice sola,
para saber cuánto falta sin recargar la página.

**Why this priority**: Es el seguimiento en vivo (RF-28); depende de las historias 1 y 3.

**Independent Test**: Con un pedido Recogido y un repartidor enviando ubicaciones, el cliente dueño ve la
posición moverse sin recargar; otro cliente no la ve.

**Acceptance Scenarios**:

1. **Given** un pedido del cliente en estado Recogido con ubicación conocida, **When** el cliente abre el
   seguimiento, **Then** ve la ubicación vigente del repartidor y la hora de su última actualización.
2. **Given** el seguimiento abierto, **When** el repartidor envía una nueva ubicación, **Then** el cliente
   ve la posición actualizada sin recargar.
3. **Given** un pedido que aún no fue recogido o que ya fue Entregado, Cancelado o Rechazado, **When** el
   cliente consulta el seguimiento, **Then** no se muestra ninguna ubicación y se explica el motivo.
4. **Given** un pedido de otro cliente, restaurante, administrador o persona sin sesión, **When** intenta
   ver la ubicación del repartidor, **Then** se deniega.
5. **Given** que el repartidor aún no envió ubicación, **When** el cliente abre el seguimiento, **Then** ve
   un mensaje de que la ubicación no está disponible todavía.
6. **Given** una ubicación desactualizada, **When** el cliente la ve, **Then** se indica claramente la hora
   de la última actualización.

---

### Edge Cases

- El repartidor pierde conexión: se conserva la ubicación vigente y el cliente ve su antigüedad.
- Ubicaciones que llegan desordenadas: una ubicación con fecha anterior no reemplaza a una más reciente.
- La entrega se rechaza y se reasigna: la ubicación del repartidor anterior deja de mostrarse al cliente.
- El cliente pierde la conexión del seguimiento en vivo: al reconectar ve el estado y ubicación actuales.
- Un usuario con muchos avisos: la consulta es paginada y el contador de no leídos es correcto.
- Rechazo o cancelación del pedido: se avisa a los participantes afectados y la ubicación no se muestra.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (RF-27): El sistema MUST permitir al cliente consultar el estado actual de cada uno de sus
  pedidos y las etapas cumplidas con fecha y hora, solo del pedido propio. Se accede a cada pedido desde su
  confirmación o desde el aviso que lo menciona; un listado general de pedidos queda fuera de este incremento.
- **FR-002**: El sistema MUST denegar la consulta del pedido a cualquier cliente que no sea el dueño y a
  quien no tenga sesión, sin revelar si el pedido existe.
- **FR-003** (RF-26): El sistema MUST permitir al repartidor enviar su ubicación periódicamente solo mientras
  tiene una entrega en curso (Asignada o Recogida) y conservar únicamente la ubicación vigente (la última recibida) con su
  fecha y hora.
- **FR-004**: El sistema MUST rechazar ubicaciones con coordenadas inválidas, de quien no tiene entrega en
  curso o de cuentas que no sean repartidor, sin modificar la ubicación vigente.
- **FR-005**: El sistema MUST ignorar una ubicación con fecha anterior a la ya conservada.
- **FR-006**: El sistema MUST NOT conservar historial de la ubicación en tiempo real; solo MUST registrar de
  forma permanente la ubicación al recoger y al entregar el pedido, junto con ese hecho.
- **FR-007** (RF-28): El sistema MUST mostrar al cliente dueño la ubicación vigente del repartidor, con la hora
  de su última actualización, únicamente mientras el pedido está Recogido.
- **FR-008**: El sistema MUST actualizar la ubicación mostrada al cliente sin que este recargue la página,
  con una demora máxima razonable respecto del envío.
- **FR-009**: El sistema MUST impedir que cualquier persona distinta del cliente dueño (otro cliente,
  restaurante, otro repartidor, administrador o anónimo) vea la ubicación del repartidor, y MUST dejar de
  mostrarla al terminar la entrega o si el pedido se cancela o la entrega se reasigna.
- **FR-010** (RF-29): El sistema MUST registrar un aviso por cada cambio relevante del pedido o la entrega y
  dirigirlo a los participantes que correspondan (cliente, restaurante, repartidor), según la regla fija
  definida en Assumptions, incluido el aviso de cancelación al repartidor asignado.
- **FR-011**: El sistema MUST permitir a cada usuario consultar sus avisos, ordenados del más reciente al más
  antiguo, distinguir los no leídos, ver cuántos tiene sin leer y marcarlos como leídos.
- **FR-012**: El sistema MUST impedir el acceso o la modificación de avisos ajenos.
- **FR-013**: El sistema MUST completar el cambio de estado aunque el registro o el envío del aviso falle, y
  MUST NOT revertir ni bloquear la operación que lo originó. El sistema MUST reintentar automáticamente un
  aviso fallido un número limitado de veces y, si sigue fallando, descartarlo dejando constancia del fallo.
- **FR-014**: El sistema MUST evitar avisos duplicados cuando un mismo cambio se procesa más de una vez.
- **FR-015**: El sistema MUST mostrar mensajes claros ante acciones no permitidas, ubicaciones no
  disponibles o datos inválidos, sin exponer datos de otros usuarios.
- **FR-016**: Los avisos MUST conservarse mientras exista el pedido asociado, sin borrado por el usuario.

### Key Entities *(include if feature involves data)*

- **Ubicación vigente del repartidor**: repartidor, entrega, coordenadas, fecha y hora de actualización; una
  sola por repartidor con entrega en curso, reemplazada en cada envío.
- **Ubicación de hito**: ubicación permanente asociada a la recogida o a la entrega, con coordenadas y fecha.
- **Aviso**: destinatario, tipo de cambio, pedido asociado, mensaje, fecha y hora, indicador de leído.
- **Pedido y entrega** (specs 006 a 008): fuente de los estados y de los cambios que originan avisos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente consulta el estado de su pedido y ve el estado actual con sus etapas en menos de
  3 segundos.
- **SC-002**: Una ubicación enviada por el repartidor aparece en el seguimiento del cliente en menos de
  10 segundos, sin recargar la página.
- **SC-003**: El 100 % de los intentos de ver la ubicación por personas distintas del cliente dueño, o fuera
  del estado Recogido, se deniega sin mostrar coordenadas.
- **SC-004**: Tras el envío de N ubicaciones, existe una sola vigente y ningún historial de ellas; las únicas
  ubicaciones permanentes son las de recogida y entrega.
- **SC-005**: El 100 % de los cambios relevantes genera sus avisos a los participantes correspondientes, sin
  duplicados.
- **SC-006**: En el 100 % de los casos en que el aviso falla, el cambio de estado se completa igualmente, y
  el aviso se reintenta o queda constancia de su fallo.
- **SC-007**: Los usuarios identifican sus avisos no leídos y el contador es correcto en el 100 % de los
  casos probados.

## Assumptions

- Los avisos se muestran dentro de la plataforma (bandeja de avisos); no se envían por correo, SMS ni push
  nativo.
- Regla de destinatarios: el cliente recibe aceptado, rechazado, cancelado, en preparación, listo para
  recoger, asignado, recogido y entregado; el restaurante recibe nuevo pedido, cancelación por el cliente,
  asignado, recogido y entregado; el repartidor recibe la asignación de una entrega y la cancelación de un
  pedido asignado. Quien realiza la acción no recibe aviso de ella.
- "En curso" significa entrega Asignada o Recogida; el cliente solo ve la ubicación en Recogido (en camino).
- Frecuencia de envío del repartidor del orden de unos pocos segundos a medio minuto; la ubicación proviene
  del dispositivo con consentimiento del repartidor.
- La ubicación se muestra como posición en un mapa o coordenadas; no hay ruta ni tiempo estimado de llegada.
- Reutiliza las cuentas, roles, pedidos, estados y entregas de las specs 002, 006, 007 y 008.
- El historial de operaciones (spec 010) y las vistas de administración (spec 011) quedan fuera; los avisos
  no sustituyen el registro de cambios de estado.
