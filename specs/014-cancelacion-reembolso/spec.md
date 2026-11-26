# Feature Specification: Cancelación administrativa con reembolso y cobro del efectivo al entregar

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-25

**Status**: Draft

**Input**: Incremento 014 del usuario. Hoy el cobro con tarjeta se hace cuando el restaurante acepta y no
existen reembolsos: si después el pedido no puede completarse (el restaurante no puede prepararlo, ningún
repartidor lo toma, todos lo rechazan o hay un incidente), el pedido queda atascado y el cliente ya pagó.
Se quiere (1) que el administrador pueda cancelar un pedido aceptado y aún no entregado, con motivo
obligatorio y reembolso total si se pagó con tarjeta y ya se cobró; y (2) que el efectivo se registre como
cobrado al entregarse el pedido, no al aceptarlo. Depende de la spec 006 (pago y transacciones), 007
(estados del pedido y registro de cambios), 008 (entrega y disponibilidad del repartidor), 009 (avisos),
010 (historiales) y 011 (consulta administrativa de pedidos). Requiere la enmienda 5.0.0 de la
constitución, aprobada por el usuario el 2026-11-25.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El administrador cancela un pedido aceptado y el cliente recupera su dinero (Priority: P1)

Como administrador, quiero cancelar un pedido que ya fue aceptado pero no puede completarse, indicando el
motivo, para que el cliente reciba de vuelta todo lo que pagó y el pedido no quede atascado.

**Why this priority**: Es el núcleo del incremento. Sin él, un pedido aceptado que no puede entregarse deja
al cliente sin comida y sin su dinero, y no hay forma de resolverlo desde la plataforma.

**Independent Test**: Con un pedido pagado con tarjeta y en estado Aceptado (o cualquier estado posterior
hasta Recogido), el administrador lo cancela con un motivo; se comprueba que el pedido queda Cancelado, que
el pago queda Reembolsado por el total y que hay constancia del reembolso y del cambio de estado con el
administrador como responsable.

**Acceptance Scenarios**:

1. **Given** un pedido con tarjeta ya cobrado en estado Aceptado, En preparación, Listo, Asignado o
   Recogido, **When** el administrador lo cancela indicando un motivo, **Then** se reembolsa el total
   cobrado, el pedido pasa a Cancelado y el pago queda Reembolsado.
2. **Given** la cancelación anterior, **When** se consulta el pedido, **Then** el cambio de estado registra
   al administrador como responsable, el motivo indicado y el momento, y existe una transacción de
   reembolso asociada al pedido con el importe y su resultado.
3. **Given** un pedido en cualquiera de esos estados, **When** el administrador intenta cancelarlo sin
   motivo, **Then** se rechaza y se le pide el motivo, sin cambios en el pedido ni en el pago.
4. **Given** un pedido Entregado, Pendiente, Rechazado o ya Cancelado, **When** el administrador intenta
   cancelarlo, **Then** se rechaza con un mensaje que explica que ese pedido no se puede cancelar desde
   aquí, sin cambios.
5. **Given** un pedido Pendiente, **When** se revisan las acciones del administrador, **Then** la
   cancelación administrativa no se ofrece (un Pendiente lo resuelven el restaurante, el cliente o el
   vencimiento automático).

---

### User Story 2 - Un fallo de la pasarela no deja el pedido cancelado sin reembolso (Priority: P1)

Como plataforma, necesito que si el reembolso no puede hacerse, el pedido no quede Cancelado, para no
decirle al cliente que se le devolvió un dinero que no se devolvió.

**Why this priority**: Es una garantía de integridad del dinero. Sin ella la cancelación no es confiable.

**Independent Test**: Simular que la pasarela no responde o rechaza el reembolso; comprobar que el pedido
sigue en su estado anterior, que el pago sigue Cobrado, que queda constancia del intento fallido y que el
administrador ve un mensaje para reintentar.

**Acceptance Scenarios**:

1. **Given** un pedido con tarjeta cobrado, **When** el administrador lo cancela y la pasarela no responde
   o rechaza el reembolso, **Then** el pedido conserva su estado, el pago sigue Cobrado, se registra la
   transacción de reembolso fallida y el administrador ve que no se pudo reembolsar y puede reintentar.
2. **Given** un reintento tras un fallo, **When** la pasarela responde bien, **Then** el pedido queda
   Cancelado y Reembolsado una sola vez.
3. **Given** que el reembolso sí se hizo en la pasarela pero la respuesta se perdió, **When** el
   administrador reintenta, **Then** el cliente no recibe un segundo reembolso.

---

### User Story 3 - El efectivo se cobra al entregar (Priority: P1)

Como plataforma, necesito que un pedido pagado en efectivo figure como cobrado solo cuando el repartidor lo
entrega, porque es en ese momento cuando el cliente paga, para que los registros de pago reflejen la
realidad y un pedido en efectivo cancelado no figure nunca como cobrado.

**Why this priority**: Hoy el efectivo figura como cobrado al aceptar, aunque nadie haya recibido dinero.
Corregirlo es condición para que la cancelación administrativa de pedidos en efectivo sea coherente.

**Independent Test**: Seguir un pedido en efectivo desde Pendiente hasta Entregado y comprobar que su pago
figura pendiente de cobro hasta la entrega y cobrado desde ese momento; cancelar otro antes de entregarse y
comprobar que queda sin cobro.

**Acceptance Scenarios**:

1. **Given** un pedido en efectivo que el restaurante acepta, **When** se consulta su pago, **Then** figura
   como pendiente de cobro, no como cobrado.
2. **Given** un pedido en efectivo Recogido, **When** el repartidor lo marca como Entregado, **Then** su
   pago queda registrado como cobrado por el total, con una transacción de cobro asociada al pedido.
3. **Given** un pedido en efectivo aceptado y no entregado, **When** el administrador lo cancela con
   motivo, **Then** el pedido queda Cancelado, el pago queda sin cobro y no se intenta ninguna operación
   con la pasarela ni ningún reembolso.
4. **Given** un pedido en efectivo, **When** el cliente lo cancela, el restaurante lo rechaza o vence
   estando Pendiente, **Then** el resultado no cambia respecto de hoy: sin cobro.

---

### User Story 4 - Repartidor liberado y todos avisados (Priority: P2)

Como repartidor con un pedido asignado o recogido, quiero enterarme de que se canceló y quedar libre para
otra entrega. Como cliente y como restaurante, quiero saber que el pedido se canceló y por qué.

**Why this priority**: Evita que el repartidor siga trabajando en un pedido que ya no existe y que el
restaurante lo siga preparando, pero la cancelación y el reembolso funcionan sin esta parte.

**Independent Test**: Cancelar un pedido Asignado y comprobar que la entrega queda sin repartidor, que el
repartidor vuelve a estar libre y que cliente, restaurante y repartidor reciben un aviso con el motivo.

**Acceptance Scenarios**:

1. **Given** un pedido Asignado o Recogido, **When** el administrador lo cancela, **Then** la entrega deja
   de estar activa (conserva quién la tenía como registro), el repartidor queda libre para nuevas
   asignaciones y su lista de entregas activas ya no muestra el pedido.
2. **Given** un pedido Listo sin repartidor, **When** se cancela, **Then** deja de estar entre las entregas
   pendientes de asignar.
3. **Given** cualquier cancelación administrativa, **When** se completa, **Then** el cliente, el
   restaurante y, si lo había, el repartidor reciben exactamente un aviso cada uno, con el motivo; el
   aviso del cliente indica si se le reembolsó el pago.
4. **Given** un repartidor liberado por una cancelación, **When** hay entregas pendientes de asignar,
   **Then** puede recibir una de ellas como cualquier repartidor libre.

---

### User Story 5 - Cliente y administrador ven el reembolso (Priority: P2)

Como cliente, quiero ver en mi pedido y en mi historial que fue cancelado y que se me devolvió el dinero.
Como administrador, quiero ver el reembolso en el detalle del pedido de mi panel.

**Why this priority**: Da confianza y permite atender consultas, pero el dinero ya se devolvió sin esta
vista.

**Independent Test**: Tras cancelar con reembolso, abrir la página del pedido del cliente, su historial y el
detalle del pedido en el panel del administrador, y comprobar que los tres muestran Cancelado y
Reembolsado, con el motivo donde corresponda.

**Acceptance Scenarios**:

1. **Given** un pedido cancelado con reembolso, **When** el cliente abre la página del pedido, **Then** ve
   que fue cancelado por la plataforma, el motivo y que el pago fue reembolsado por el total.
2. **Given** el mismo pedido, **When** el cliente consulta su historial, **Then** el pedido aparece
   Cancelado y con el pago Reembolsado.
3. **Given** el mismo pedido, **When** el administrador abre su detalle, **Then** ve el cambio de estado
   con su motivo y responsable, y la transacción de reembolso con importe, momento y resultado.
4. **Given** un pedido en efectivo, **When** el cliente lo consulta antes de entregarse, **Then** ve que
   pagará al recibirlo; después de entregado, que fue pagado en efectivo.

---

### Edge Cases

- **Cancelación simultánea con un avance del pedido**: si el administrador cancela justo cuando el
  restaurante marca Listo o el repartidor marca Recogido o Entregado, solo una acción prevalece; si gana la
  entrega, el pedido queda Entregado y la cancelación se rechaza; nunca queda Entregado y Reembolsado.
- **Dos cancelaciones del administrador a la vez** (doble clic, dos pestañas o dos administradores): se
  reembolsa una sola vez y se registra un único cambio de estado.
- **Pedido con tarjeta aceptado cuyo cobro no llegó a hacerse** (la reserva había vencido al aceptar): ya
  quedó Cancelado por el sistema en la spec 007; no aplica esta cancelación.
- **Pedido en efectivo entregado antes de este incremento**: su pago ya figura como cobrado desde la
  aceptación; se conserva tal cual y no se reprocesa.
- **Pedido en efectivo aceptado antes de este incremento y aún no entregado**: su pago ya figura como
  cobrado; si se cancela, no se intenta reembolso por tratarse de efectivo y el pago queda sin cobro.
- **Restaurante o repartidor con la cuenta deshabilitada**: no impide cancelar ni reembolsar; los avisos se
  registran igual.
- **Reembolso que la pasarela acepta pero tarda en completarse**: para la plataforma el pago queda
  Reembolsado en cuanto la pasarela acepta el reembolso; la acreditación en la tarjeta depende del banco.
- **Motivo demasiado largo o con solo espacios**: se rechaza como motivo vacío o se pide acortarlo, con el
  mismo límite que los demás motivos de cambio de estado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El administrador MUST poder cancelar un pedido que esté Aceptado, En preparación, Listo,
  Asignado o Recogido. Ningún otro rol MUST poder hacerlo desde esos estados.
- **FR-002**: La cancelación administrativa MUST exigir un motivo no vacío, con la misma longitud máxima
  que los demás motivos de cambio de estado.
- **FR-003**: Un intento de cancelar un pedido Pendiente, Rechazado, Cancelado o Entregado MUST rechazarse
  con un mensaje claro y MUST NOT producir efectos.
- **FR-004**: Si el pedido se pagó con tarjeta y el pago está Cobrado, el sistema MUST reembolsar el total
  cobrado antes de dar el pedido por Cancelado; el pago MUST quedar Reembolsado.
- **FR-005**: Si el reembolso falla o la pasarela no responde, el pedido MUST conservar su estado, el pago
  MUST seguir Cobrado, MUST quedar registrada la transacción de reembolso fallida y el administrador MUST
  ver un mensaje que le permita reintentar.
- **FR-006**: Los reintentos de una misma cancelación MUST NOT producir más de un reembolso, aunque la
  pasarela haya procesado un intento anterior cuya respuesta se perdió.
- **FR-007**: Si el pedido se pagó en efectivo, la cancelación MUST NOT llamar a la pasarela ni reembolsar;
  el pago MUST quedar sin cobro.
- **FR-008**: La cancelación MUST registrar el cambio de estado con el estado anterior, Cancelado, el
  administrador como responsable, el motivo y el momento, igual que los demás cambios de estado.
- **FR-009**: El reembolso MUST registrarse como una transacción de pago asociada al pedido, con tipo
  reembolso, importe, momento, resultado y la referencia de la pasarela.
- **FR-010**: La cancelación, el avance del pedido por restaurante o repartidor y otras cancelaciones del
  mismo pedido MUST serializarse para que solo una acción prevalezca; las demás MUST recibir un mensaje de
  que el pedido ya cambió y MUST NOT producir efectos.
- **FR-011**: Al cancelar un pedido con entrega, la entrega MUST dejar de estar activa y de figurar entre
  las pendientes de asignar, conservando quién la tenía como registro, y el repartidor que la tenía MUST
  quedar libre para nuevas asignaciones.
- **FR-012**: El sistema MUST avisar una vez al cliente, una vez al restaurante y una vez al repartidor (si
  lo había) de la cancelación, incluyendo el motivo; el aviso al cliente MUST indicar si se le reembolsó el
  pago.
- **FR-013**: Un pedido en efectivo MUST figurar como pendiente de cobro desde que se confirma hasta su
  entrega y MUST registrarse como cobrado, con su transacción de cobro, en el momento en que el repartidor
  lo marca Entregado.
- **FR-014**: Aceptar un pedido en efectivo MUST NOT registrarlo como cobrado.
- **FR-015**: El cliente MUST ver en la página de su pedido y en su historial que fue cancelado por la
  plataforma, el motivo y si el pago fue reembolsado.
- **FR-016**: El administrador MUST ver en el detalle de un pedido su estado de pago y sus transacciones,
  incluido el reembolso, y MUST tener la acción de cancelar disponible solo en los estados de FR-001.
- **FR-017**: Las reglas existentes MUST NOT cambiar: el cliente solo cancela mientras el pedido está
  Pendiente; el restaurante solo rechaza estando Pendiente; el vencimiento automático sigue igual.
- **FR-018**: Los pagos en efectivo registrados como cobrados antes de este incremento MUST conservarse sin
  reprocesarse.

### Key Entities

- **Pedido**: gana la transición a Cancelado desde Aceptado, En preparación, Listo, Asignado y Recogido,
  solo por el administrador y con motivo. Entregado sigue siendo final.
- **Pago**: gana el estado **Reembolsado** (tarjeta cobrada y devuelta por completo). En efectivo, el pago
  queda pendiente de cobro hasta la entrega, Cobrado al entregarse y sin cobro si se cancela antes.
- **Transacción de pago**: gana el tipo **reembolso**; registra importe, momento, resultado (correcto o
  fallido con su código) y referencia de la pasarela. En efectivo, la entrega genera una transacción de
  cobro.
- **Cambio de estado del pedido**: sin cambios de forma; el administrador ya existe como responsable
  posible.
- **Entrega y disponibilidad del repartidor**: la entrega deja de estar activa (conserva al repartidor como
  registro) y este vuelve a estar libre.
- **Aviso**: reutiliza el tipo de aviso de cancelación existente, ahora también hacia el repartidor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador cancela un pedido aceptado, con motivo, en menos de 1 minuto desde que abre
  su detalle.
- **SC-002**: El 100 % de los pedidos con tarjeta cancelados por el administrador terminan Reembolsados por
  el total exacto cobrado; ninguno queda Cancelado con el pago Cobrado.
- **SC-003**: En 100 repeticiones de cancelación simultánea (dos cancelaciones, o cancelación junto con la
  entrega), ningún pedido recibe más de un reembolso ni queda Entregado y Reembolsado a la vez.
- **SC-004**: Ante un fallo de la pasarela, el 100 % de los pedidos conserva su estado y su pago Cobrado, y
  el reintento posterior completa la cancelación con un único reembolso.
- **SC-005**: El 100 % de los pedidos en efectivo figuran pendientes de cobro hasta la entrega y cobrados
  después; ningún pedido en efectivo cancelado figura como cobrado.
- **SC-006**: Cada cancelación administrativa genera exactamente un aviso al cliente, uno al restaurante y
  uno al repartidor cuando lo había, y el repartidor queda disponible de inmediato.

## Assumptions

- El reembolso es siempre total (subtotal, envío y comisión); los reembolsos parciales quedan fuera.
- Solo el administrador cancela después de aceptar; el restaurante o el repartidor que no pueden continuar
  lo comunican fuera de la plataforma y el administrador decide. Una solicitud de cancelación dentro de la
  aplicación queda fuera.
- La pasarela sigue en modo de pruebas (constitución); el reembolso se considera hecho cuando la pasarela
  lo acepta, sin esperar la acreditación bancaria ni usar notificaciones de la pasarela.
- La cancelación administrativa no devuelve productos ni repone disponibilidad: el catálogo no maneja
  inventario.
- El motivo de la cancelación administrativa es visible para cliente, restaurante y repartidor, igual que
  el motivo de un rechazo; el administrador lo redacta pensando en esos lectores.
- El aviso de cancelación existente basta; no se crea un tipo de aviso nuevo.
- Los pedidos y pagos anteriores al incremento conservan sus registros; no se migran datos de pago.
