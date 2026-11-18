# Feature Specification: Aceptación, rechazo, preparación y cancelación del pedido

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-17

**Status**: Draft

**Input**: Incremento 007 del usuario: RF-18, RF-19 y RF-20. Depende de la spec 002 (cuentas y permisos),
003 (restaurantes), 004 (catálogo) y 006 (pedido Pendiente con el monto reservado y las operaciones de
cobrar y liberar la reserva). Fuera de alcance: asignación de repartidor y entrega (spec 008), avisos y
notificaciones (spec 009), historial (spec 010), reembolsos y vista del administrador (spec 011).

## Clarifications

### Session 2026-11-17

- Q: Si la reserva ya venció o no puede cobrarse al aceptar, ¿en qué estado debe quedar el pedido? → A:
  Pasa a Cancelado (por el sistema, con motivo "reserva vencida"), sin cobro; el restaurante ve el mensaje.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El restaurante acepta o rechaza un pedido pendiente (Priority: P1)

Como restaurante, quiero ver los pedidos pendientes que recibo y decidir si los acepto o los rechazo,
para comprometerme solo con los pedidos que puedo preparar.

**Why this priority**: Es la decisión central del flujo (RF-18) y determina si el dinero reservado se cobra
o se libera.

**Independent Test**: Con dos pedidos Pendientes de un restaurante, aceptar uno y rechazar el otro;
comprobar que el primero queda Aceptado con el monto cobrado y el segundo Rechazado con la reserva
liberada y sin cobro.

**Acceptance Scenarios**:

1. **Given** un restaurante con pedidos Pendientes, **When** consulta sus pedidos pendientes, **Then**
   ve únicamente los suyos, con productos, cantidades, importes y datos necesarios para decidir.
2. **Given** un pedido Pendiente del restaurante, **When** lo acepta, **Then** el pedido pasa a Aceptado
   y el monto reservado se cobra por completo.
3. **Given** un pedido Pendiente del restaurante, **When** lo rechaza, **Then** el pedido pasa a
   Rechazado y la reserva se libera sin cobro alguno.
4. **Given** un pedido de otro restaurante, **When** un restaurante intenta aceptarlo o rechazarlo,
   **Then** se deniega sin cambiar el pedido ni el pago.
5. **Given** una persona sin sesión o con rol distinto de restaurante, **When** intenta decidir sobre un
   pedido, **Then** se deniega (401 o 403) sin efecto.
6. **Given** un pedido que ya no está Pendiente, **When** el restaurante intenta aceptarlo o rechazarlo,
   **Then** se rechaza con un mensaje claro y no se repite ningún cobro ni liberación.

---

### User Story 2 - El cliente cancela su pedido pendiente (Priority: P1)

Como cliente, quiero cancelar un pedido propio mientras el restaurante aún no lo ha aceptado, para no
quedar atado a un pedido que ya no quiero y recuperar mi reserva sin cobro.

**Why this priority**: Cubre RF-20 y protege al cliente; comparte con la historia 1 la regla crítica de
coherencia entre pago y estado.

**Independent Test**: Con un pedido Pendiente propio, cancelarlo y comprobar que queda Cancelado con la
reserva liberada; con un pedido ya Aceptado, comprobar que la cancelación se rechaza.

**Acceptance Scenarios**:

1. **Given** un pedido propio Pendiente, **When** el cliente lo cancela, **Then** pasa a Cancelado y la
   reserva se libera sin cobro.
2. **Given** un pedido propio Aceptado, En preparación o Listo para recoger, **When** el cliente intenta
   cancelarlo, **Then** se rechaza con un mensaje que explica que ya fue aceptado y cobrado, sin
   reembolso, y el pedido no cambia.
3. **Given** un pedido de otro cliente, **When** un cliente intenta cancelarlo, **Then** se deniega sin
   revelar si el pedido existe ni cambiar nada.
4. **Given** un pedido ya Rechazado o Cancelado, **When** el cliente intenta cancelarlo de nuevo,
   **Then** se rechaza sin repetir la liberación.

---

### User Story 3 - Decisiones simultáneas con un único resultado (Priority: P1)

Como plataforma, necesito que si el restaurante acepta y el cliente cancela al mismo tiempo el pedido
termine en un único estado coherente, para no cobrar a quien canceló ni liberar un pedido aceptado.

**Why this priority**: Es una garantía de integridad del dinero y del estado; sin ella el flujo no es
confiable.

**Independent Test**: Lanzar de forma simultánea la aceptación y la cancelación del mismo pedido
Pendiente, repetidas muchas veces; comprobar que en todos los casos el resultado es exactamente uno:
Aceptado y cobrado, o Cancelado y liberado.

**Acceptance Scenarios**:

1. **Given** un pedido Pendiente, **When** el restaurante acepta y el cliente cancela al mismo tiempo,
   **Then** solo una de las dos acciones prevalece y la otra recibe un mensaje de que el pedido ya
   cambió de estado.
2. **Given** cualquier desenlace de la simultaneidad, **When** se revisa el pedido y su pago, **Then**
   nunca está Aceptado y Cancelado a la vez ni cobrado y liberado a la vez.
3. **Given** dos aceptaciones o dos rechazos simultáneos del mismo pedido, **When** se procesan,
   **Then** se aplica una sola vez y el pago se cobra o libera una sola vez.

---

### User Story 4 - El restaurante avanza la preparación (Priority: P2)

Como restaurante, quiero marcar un pedido aceptado como En preparación y luego como Listo para recoger,
para reflejar su avance real y dejarlo disponible para el reparto.

**Why this priority**: Completa el ciclo del restaurante (RF-19), pero depende de que existan pedidos
aceptados.

**Independent Test**: Con un pedido Aceptado, pasarlo a En preparación y después a Listo para recoger;
comprobar que se rechazan los saltos y retrocesos.

**Acceptance Scenarios**:

1. **Given** un pedido Aceptado del restaurante, **When** lo marca En preparación, **Then** el estado
   cambia y queda registrado.
2. **Given** un pedido En preparación, **When** lo marca Listo para recoger, **Then** el estado cambia y
   el pedido queda disponible para la asignación de repartidor (spec 008).
3. **Given** un pedido Aceptado, **When** intenta marcarlo directamente Listo para recoger, o un pedido
   En preparación o Listo para recoger, **When** intenta retroceder, **Then** se rechaza y el estado no
   cambia.
4. **Given** un pedido de otro restaurante, **When** intenta actualizar su estado, **Then** se deniega
   sin cambios.
5. **Given** un pedido Pendiente, Rechazado o Cancelado, **When** el restaurante intenta avanzarlo,
   **Then** se rechaza.

---

### User Story 5 - Registro de cada cambio de estado (Priority: P2)

Como operador de la plataforma, quiero que cada cambio de estado quede registrado con su fecha y su
responsable, para poder auditar qué ocurrió con cada pedido.

**Why this priority**: Da trazabilidad a todas las demás historias; la consulta del historial completo
llega en la spec 010.

**Independent Test**: Recorrer un pedido por varios estados y comprobar que existe un registro por cada
cambio, con estado anterior, nuevo, fecha y responsable.

**Acceptance Scenarios**:

1. **Given** cualquier cambio de estado válido, **When** se aplica, **Then** queda registrado con el
   estado anterior, el estado nuevo, la fecha y hora y quién lo realizó (restaurante o cliente).
2. **Given** un intento rechazado (sin permiso o transición inválida), **When** se evalúa, **Then** no
   se registra ningún cambio de estado.

---

### Edge Cases

- Fallo de la pasarela al cobrar al aceptar: el pedido no debe quedar Aceptado sin cobro; permanece
  Pendiente y el restaurante recibe un mensaje claro para reintentar.
- Fallo de la pasarela al liberar al rechazar o cancelar: el pedido no debe quedar Rechazado o Cancelado
  con la reserva activa sin que quede constancia; se deja un resultado consistente y reintentable.
- Reserva vencida: si la reserva ya no puede cobrarse al aceptar, el pedido no queda Aceptado; pasa a
  Cancelado por el sistema con motivo "reserva vencida", sin cobro, y se informa al restaurante.
- Doble envío (doble clic o reintento de red) de la misma acción: se aplica una sola vez.
- Pedido inexistente o identificador inválido: respuesta de no encontrado sin efectos.
- Restaurante deshabilitado o cuenta suspendida tras crearse el pedido: no puede decidir sobre pedidos
  hasta recuperar su habilitación; el cliente aún puede cancelar mientras esté Pendiente.
- Un pedido Pendiente que nadie decide permanece Pendiente; el vencimiento automático queda fuera.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al restaurante autenticado consultar sus pedidos por estado
  (Pendientes por defecto; también Aceptados, En preparación y Listos para recoger, para poder avanzarlos),
  y solo los de su propio restaurante.
- **FR-002**: El sistema MUST permitir al restaurante aceptar un pedido Pendiente propio, pasándolo a
  Aceptado y cobrando el monto reservado en su totalidad.
- **FR-003**: El sistema MUST permitir al restaurante rechazar un pedido Pendiente propio, pasándolo a
  Rechazado y liberando la reserva sin cobro.
- **FR-004**: El sistema MUST permitir al cliente cancelar un pedido propio únicamente mientras esté
  Pendiente, pasándolo a Cancelado y liberando la reserva sin cobro.
- **FR-005**: El sistema MUST rechazar la cancelación del cliente sobre pedidos Aceptados, En
  preparación o Listos para recoger, informando que no hay reembolsos.
- **FR-006**: El sistema MUST permitir al restaurante propietario avanzar un pedido Aceptado a En
  preparación y luego a Listo para recoger, en ese orden, sin saltos ni retrocesos.
- **FR-007**: El sistema MUST permitir solo las transiciones Pendiente→Aceptado, Pendiente→Rechazado,
  Pendiente→Cancelado, Aceptado→En preparación y En preparación→Listo para recoger; cualquier otra
  MUST rechazarse sin efectos. Rechazado y Cancelado son estados finales.
- **FR-008**: El sistema MUST denegar a cualquier restaurante distinto del propietario decidir sobre un
  pedido o actualizar su estado, y a cualquier cliente distinto del dueño cancelarlo, sin revelar datos
  del pedido.
- **FR-009**: El sistema MUST denegar estas acciones a personas sin sesión (401) y a roles no
  autorizados (403).
- **FR-010**: El sistema MUST garantizar un único resultado coherente ante acciones simultáneas sobre un
  mismo pedido: nunca Aceptado y Cancelado a la vez, ni cobrado y liberado a la vez, y cada
  cobro o liberación ocurre una sola vez.
- **FR-011**: El sistema MUST mantener el estado del pedido y el resultado del pago consistentes: un
  pedido Aceptado tiene el monto cobrado; uno Rechazado o Cancelado tiene la reserva liberada y ningún
  cobro; si la operación de pago falla, el estado no avanza.
- **FR-011a**: Si la reserva venció o no puede cobrarse al aceptar, el sistema MUST pasar el pedido a
  Cancelado, con motivo "reserva vencida" y el sistema como responsable, sin cobro, e informar al
  restaurante.
- **FR-012**: El sistema MUST registrar cada cambio de estado con estado anterior, estado nuevo, fecha y
  hora y el responsable: cuenta y rol, o «sistema» cuando el cambio no lo origina ninguna cuenta (FR-011a).
- **FR-013**: El sistema MUST dejar el pedido Listo para recoger disponible para la asignación de
  repartidor (spec 008), sin asignarlo en este incremento.
- **FR-014**: El sistema MUST mostrar mensajes claros ante acciones no permitidas, estados obsoletos o
  fallos de pago, sin exponer detalles internos.
- **FR-015**: El sistema MUST ser idempotente ante reenvíos de la misma acción: no repetir cobros,
  liberaciones ni registros.

### Key Entities *(include if feature involves data)*

- **Pedido** (spec 006): se extiende con los estados Aceptado, Rechazado, Cancelado, En preparación y
  Listo para recoger, además de Pendiente.
- **Cambio de estado**: pedido, estado anterior, estado nuevo, fecha y hora, responsable (cuenta y
  rol) y, en rechazo o cancelación, el motivo si se proporciona.
- **Transacción de pago** (spec 006): se completa con la captura (al aceptar) o la liberación (al
  rechazar o cancelar); un pedido nunca tiene ambas.
- **Restaurante, Cliente y Cuenta** (specs 003 y 002): determinan quién es propietario y quién puede
  actuar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un restaurante ve sus pedidos pendientes y acepta o rechaza uno en menos de 30 segundos.
- **SC-002**: En el 100 % de los casos de prueba, aceptar cobra el monto reservado exacto y rechazar o
  cancelar libera la reserva sin cobro.
- **SC-003**: En el 100 % de las repeticiones de prueba de aceptación y cancelación simultáneas, el
  pedido termina en exactamente un estado coherente con su pago, sin cobros ni liberaciones dobles.
- **SC-004**: El 100 % de los intentos de transición no permitida (saltos, retrocesos, cancelar tras
  aceptar) se rechazan sin modificar el pedido ni el pago.
- **SC-005**: El 100 % de los intentos por parte de restaurantes o clientes ajenos al pedido, o de roles
  no autorizados, se deniegan sin efecto.
- **SC-006**: El 100 % de los cambios de estado aplicados tienen un registro con fecha y responsable, y
  ningún intento rechazado genera registro.
- **SC-007**: Ante fallo de la pasarela, en el 100 % de los casos de prueba el pedido no queda en un
  estado incoherente con su pago.

## Assumptions

- La captura y la liberación del monto reservado ya existen desde la spec 006; este incremento solo las
  invoca.
- No hay reembolsos: lo cobrado no se devuelve (spec 011 los trata aparte).
- Un único usuario restaurante opera cada restaurante; no hay roles internos dentro del restaurante.
- No hay vencimiento automático de pedidos Pendientes ni avisos al cliente o restaurante (spec 009).
- Los motivos de rechazo y cancelación son opcionales.
- La consulta de pedidos pendientes es una lista simple; el historial completo y filtros llegan en la
  spec 010.
- Todos los importes se mantienen en la moneda y con el redondeo definidos en la spec 006.
