# Feature Specification: Disponibilidad, asignación y entrega

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-17

**Status**: Draft

**Input**: Incremento 008 del usuario: RF-21, RF-22, RF-23, RF-24 y RF-25, condicionado por RNF-05.
Depende de la spec 002 (cuentas y permisos por rol) y de la spec 007 (el restaurante deja el pedido en
estado Listo para recoger). Fuera de alcance: registro y visualización de la ubicación del repartidor, y
avisos y notificaciones (spec 009), historial (spec 010), vistas de administración de pedidos y entregas
(spec 011) y algoritmos avanzados de optimización de rutas.

## Clarifications

### Session 2026-11-17

- Q: ¿Con qué criterio elige el sistema entre varios repartidores candidatos? → A: Al que lleva más tiempo
  libre (sin entrega activa), sin cálculo de distancias.
- Q: ¿Qué hace el sistema si no hay ningún candidato? → A: La entrega queda pendiente de asignación y se
  asigna sola cuando un repartidor pasa a disponible o queda libre; el administrador puede asignarla
  manualmente en cualquier momento.
- Q: Al asignar manualmente, ¿qué repartidores puede elegir el administrador? → A: Solo candidatos: cuenta
  activa, disponibles, sin entrega activa y que no hayan rechazado esa entrega.
- Q: ¿Qué pasa con una entrega Asignada a un repartidor cuya cuenta se inhabilita (no puede operar ni
  rechazar)? → A: El administrador puede liberarla; vuelve a Listo para recoger sin repartidor, sin contar
  como rechazo, y el sistema intenta reasignarla.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Asignación automática de un pedido listo para recoger (Priority: P1)

Como repartidor, quiero indicar si estoy disponible y recibir automáticamente una entrega cuando haya un
pedido listo, para trabajar solo cuando puedo y sin buscar pedidos manualmente.

**Why this priority**: Es el núcleo del incremento (RF-21 y RF-22): sin disponibilidad y asignación
automática no hay entregas.

**Independent Test**: Con dos repartidores habilitados, uno disponible y otro no, dejar un pedido Listo
para recoger y comprobar que se asigna solo al disponible y queda Asignado.

**Acceptance Scenarios**:

1. **Given** un repartidor con cuenta activa, **When** activa o desactiva su disponibilidad, **Then** el
   cambio queda guardado y se muestra su estado actual.
2. **Given** un pedido que pasa a Listo para recoger y al menos un repartidor candidato (cuenta activa,
   disponible y sin entrega activa), **When** el sistema lo asigna, **Then** la entrega queda Asignada a
   un solo repartidor, el que lleva más tiempo libre.
3. **Given** repartidores no disponibles, con cuenta inhabilitada o con una entrega activa, **When** se
   asigna un pedido, **Then** ninguno de ellos es elegido.
4. **Given** un pedido Listo para recoger sin candidatos, **When** un repartidor activa su disponibilidad
   o queda libre, **Then** el sistema le asigna automáticamente la entrega pendiente más antigua.
5. **Given** un repartidor con una entrega Asignada, **When** desactiva su disponibilidad, **Then** la
   entrega asignada se mantiene y no se le ofrecen nuevas.

---

### User Story 2 - El repartidor acepta, rechaza, recoge y entrega (Priority: P1)

Como repartidor, quiero ver la entrega que tengo asignada y aceptarla o rechazarla, y después registrar
que recogí el pedido y que lo entregué, para completar el servicio y dejar constancia.

**Why this priority**: Cierra el flujo de entrega (RF-25) y lleva el pedido hasta Entregado.

**Independent Test**: Con una entrega Asignada, aceptarla, registrar la recogida y la entrega; comprobar
los estados Asignado → Recogido → Entregado con fecha y responsable en cada cambio.

**Acceptance Scenarios**:

1. **Given** un repartidor con una entrega Asignada, **When** consulta sus entregas, **Then** ve
   únicamente las suyas, con datos del restaurante, del cliente y de la dirección de entrega.
2. **Given** una entrega Asignada a él, **When** la acepta y registra que retiró el pedido, **Then** pasa
   a Recogido.
3. **Given** una entrega Recogida a él, **When** registra que la entregó al cliente, **Then** el pedido
   pasa a Entregado.
4. **Given** una entrega Asignada a otro repartidor, **When** un repartidor intenta aceptarla, rechazarla,
   recogerla o entregarla, **Then** se deniega sin cambiar nada.
5. **Given** una persona sin sesión o con rol distinto de repartidor, **When** intenta operar una entrega,
   **Then** se deniega (401 o 403) sin efecto.
6. **Given** una entrega Asignada, **When** intenta registrar la entrega sin haber registrado la recogida,
   o retroceder un estado, **Then** se rechaza con un mensaje claro y el estado no cambia.
7. **Given** una acción ya aplicada, **When** se reenvía, **Then** no se repite ni se duplica el registro.

---

### User Story 3 - Rechazo y reasignación automática (Priority: P2)

Como repartidor, quiero poder rechazar una entrega asignada, y como sistema, reasignarla a otro
repartidor sin devolvérsela al que la rechazó, para que el pedido no quede detenido.

**Why this priority**: Cubre RF-23; el flujo básico funciona sin él, pero evita pedidos atascados.

**Independent Test**: Con una entrega Asignada y otro candidato disponible, rechazarla y comprobar que
queda Asignada al otro repartidor y nunca de nuevo al primero.

**Acceptance Scenarios**:

1. **Given** una entrega Asignada a un repartidor, **When** la rechaza, **Then** la entrega vuelve a
   quedar disponible para asignar y el sistema la reasigna a otro candidato.
2. **Given** un repartidor que rechazó una entrega, **When** se reasigna, **Then** no se le vuelve a
   ofrecer esa misma entrega.
3. **Given** que ningún otro candidato está disponible, **When** se rechaza la entrega, **Then** queda
   pendiente de asignación hasta que haya candidato o el administrador la asigne.
4. **Given** un rechazo, **When** se procesa, **Then** el pedido sigue en curso (no se rechaza ni se
   cancela) y conserva su estado de pedido.

---

### User Story 4 - Asignación manual por el administrador (Priority: P2)

Como administrador, quiero asignar manualmente una entrega a un repartidor cuando la asignación
automática no tenga éxito, para que ningún pedido listo quede sin atender.

**Why this priority**: Cubre RF-24; es la salida cuando no hay candidatos o la automática falla.

**Independent Test**: Con una entrega pendiente de asignación, asignarla como administrador a un
repartidor candidato y comprobar que queda Asignada a él con el administrador como responsable.

**Acceptance Scenarios**:

1. **Given** una entrega pendiente de asignación, **When** el administrador consulta las pendientes,
   **Then** las ve con su antigüedad y los repartidores candidatos para cada una.
2. **Given** una entrega pendiente, **When** el administrador la asigna a un repartidor candidato,
   **Then** queda Asignada a ese repartidor y el cambio registra al administrador como responsable.
3. **Given** una entrega ya Asignada o posterior, **When** el administrador intenta asignarla, **Then**
   se rechaza con un mensaje claro sin cambiar la asignación existente.
4. **Given** un repartidor inhabilitado, no disponible, con una entrega activa o que ya rechazó esa
   entrega, **When** el administrador lo elige, **Then** se rechaza sin asignar y con un mensaje claro.
5. **Given** una persona que no es administrador, **When** intenta asignar manualmente, **Then** se
   deniega (401 o 403) sin efecto.
6. **Given** una entrega Asignada a un repartidor con cuenta inhabilitada, **When** el administrador la
   libera, **Then** vuelve a Listo para recoger sin repartidor, el cambio registra al administrador como
   responsable y el sistema intenta reasignarla; si el repartidor sigue habilitado, se rechaza.

---

### User Story 5 - Una entrega nunca queda con dos repartidores (Priority: P1)

Como plataforma, quiero que una entrega no se asigne a más de un repartidor aunque lleguen solicitudes
simultáneas, para evitar que dos repartidores recojan el mismo pedido.

**Why this priority**: Es el requisito de calidad RNF-05 que condiciona todo el incremento.

**Independent Test**: Lanzar solicitudes simultáneas de asignación (automática y manual) y de
reasignación sobre la misma entrega; comprobar que exactamente una prospera y el resto se rechaza sin
efecto.

**Acceptance Scenarios**:

1. **Given** una entrega pendiente y varias solicitudes de asignación simultáneas, **When** se procesan,
   **Then** queda asignada a un único repartidor y las demás se descartan con un mensaje claro.
2. **Given** una asignación automática y una manual simultáneas, **When** se procesan, **Then** solo una
   prospera.
3. **Given** un rechazo y una reasignación simultáneos, **When** se procesan, **Then** la entrega
   termina con un único repartidor activo.

### Edge Cases

- Un repartidor inhabilitado tras recibir una entrega Asignada no puede operarla (acceso denegado); el
  administrador puede liberarla y queda pendiente de asignación, sin contar como rechazo del repartidor.
- Un repartidor con entrega activa no es candidato para otra entrega, ni automática ni manualmente.
- Reasignación tras rechazo cuando todos los repartidores disponibles ya la rechazaron: queda pendiente
  de asignación hasta que un repartidor que no la rechazó pase a ser candidato; ni el sistema ni el
  administrador pueden asignársela a quien la rechazó.
- Rechazo de una entrega ya Recogida o Entregada: se rechaza; solo puede rechazarse en estado Asignado.
- Dos repartidores pasan a disponibles a la vez con una única entrega pendiente: solo uno la recibe.
- Pedido que deja de estar Listo para recoger por una carrera con otra operación: no se asigna.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (RF-21): El repartidor con cuenta activa MUST poder activar y desactivar su disponibilidad
  y ver su estado actual.
- **FR-002** (RF-22): Solo los repartidores con cuenta activa, disponibles y sin entrega activa MUST ser
  candidatos a una asignación.
- **FR-003** (RF-22): Al quedar un pedido Listo para recoger, el sistema MUST asignarlo automáticamente
  al candidato que lleva más tiempo libre, dejando la entrega en estado Asignado.
- **FR-004**: Si no hay candidatos, el sistema MUST dejar la entrega pendiente de asignación y
  asignarla automáticamente, en orden de antigüedad, cuando un repartidor pase a disponible o quede libre.
- **FR-005** (RF-25): El repartidor MUST ver únicamente las entregas que tiene asignadas, con los datos
  necesarios para realizarlas.
- **FR-006** (RF-25): El repartidor MUST poder aceptar o rechazar la entrega Asignada; al aceptar y
  registrar que retiró el pedido, la entrega pasa a Recogido.
- **FR-007** (RF-25): El repartidor MUST poder registrar la entrega al cliente, que pasa a Entregado,
  solo desde Recogido.
- **FR-008** (RF-23): Al rechazar el repartidor una entrega Asignada, el sistema MUST dejarla disponible
  para asignar y reasignarla automáticamente a otro candidato, sin volver a ofrecérsela a quien la
  rechazó.
- **FR-009**: El rechazo de la entrega MUST NOT rechazar ni cancelar el pedido ni alterar su estado.
- **FR-010** (RF-24): El administrador MUST poder asignar manualmente una entrega pendiente de asignación
  a un repartidor candidato: cuenta activa, disponible, sin entrega activa y que no haya rechazado
  esa entrega.
- **FR-010a**: El administrador MUST poder liberar una entrega Asignada cuyo repartidor tenga la cuenta
  inhabilitada; la entrega vuelve a Listo para recoger sin repartidor y se intenta reasignar.
- **FR-011** (RNF-05): El sistema MUST garantizar que una entrega tenga como máximo un repartidor
  activo, también ante solicitudes simultáneas de asignación automática, manual o reasignación; solo una
  prospera y las demás se rechazan con un mensaje claro sin efecto.
- **FR-012**: Un repartidor MUST operar únicamente las entregas que tiene asignadas; toda otra
  operación se deniega sin cambios.
- **FR-013**: El sistema MUST aplicar solo las transiciones Listo para recoger → Asignado,
  Asignado → Recogido y Recogido → Entregado, sin saltos ni retrocesos, y la reasignación tras rechazo.
- **FR-014**: El sistema MUST registrar cada cambio de estado de la entrega con estado anterior, estado
  nuevo, fecha y hora y el responsable (cuenta y rol, o «sistema» en la asignación automática).
- **FR-015**: El sistema MUST ser idempotente ante reenvíos de la misma acción: no duplicar
  asignaciones, cambios de estado ni registros.
- **FR-016**: El sistema MUST mostrar mensajes claros ante acciones no permitidas o estados obsoletos,
  sin exponer detalles internos.
- **FR-017**: El sistema MUST denegar (401 o 403) las operaciones del repartidor y del administrador a
  quien no tenga sesión o rol correspondiente.

### Key Entities *(include if feature involves data)*

- **Repartidor** (spec 002): cuenta con rol repartidor; añade su disponibilidad (activa o no) y el
  momento desde el que está libre.
- **Entrega**: relación de un pedido Listo para recoger con a lo sumo un repartidor activo; estado
  (pendiente de asignación, Asignado, Recogido, Entregado).
  El estado de la entrega es el del pedido: pendiente de asignación = Listo para recoger sin repartidor.
- **Rechazo de entrega**: entrega, repartidor que la rechazó, fecha y hora; evita reofrecérsela.
- **Cambio de estado de la entrega**: entrega, estado anterior, estado nuevo, fecha y hora y
  responsable (cuenta y rol, o «sistema»).
- **Pedido** (spec 006/007): se extiende con los estados Asignado, Recogido y Entregado.
- **Administrador** (spec 002): asigna manualmente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ante solicitudes simultáneas sobre la misma entrega, el 100 % de las pruebas termina con
  exactamente un repartidor asignado.
- **SC-002**: Con al menos un candidato, un pedido Listo para recoger queda Asignado en menos de
  5 segundos, sin intervención humana.
- **SC-003**: El 100 % de los repartidores no disponibles, inhabilitados o con entrega activa queda
  excluido de la asignación.
- **SC-004**: Tras un rechazo, la entrega se reasigna en menos de 5 segundos si hay otro candidato, y
  nunca al repartidor que la rechazó.
- **SC-005**: El administrador asigna manualmente una entrega pendiente en menos de 1 minuto.
- **SC-006**: El 100 % de los cambios de estado de entrega queda registrado con fecha y responsable.
- **SC-007**: El 100 % de los intentos de operar una entrega ajena o sin el rol adecuado se deniega
  sin alterar datos.

## Assumptions

- Cada repartidor gestiona como máximo una entrega activa (Asignada o Recogida) a la vez.
- Aceptar la entrega y registrar la recogida es una sola acción del repartidor; no hay estado
  intermedio «Aceptado».
- «Tiempo libre» se mide desde que el repartidor activó su disponibilidad o terminó su última entrega.
- Los repartidores se habilitan al registrarse (spec 002) y el administrador es una cuenta existente.
- No hay tiempo límite para que el repartidor responda a una asignación; fuera de alcance.
- Los avisos al repartidor y al cliente se tratan en la spec 009; aquí la entrega se consulta
  mediante la pantalla del repartidor.
- La ubicación del repartidor y las vistas globales de administración están fuera de alcance.
- Límite conocido: si el repartidor se inhabilita con la entrega ya Recogida, el administrador no puede
  liberarla en este incremento (solo las Asignadas); su resolución corresponde a la gestión de usuarios
  (RF-32, spec 011).
- Al asignarse, el pedido sale de la lista de pendientes y listos del restaurante; su seguimiento global
  es de la spec 011.
