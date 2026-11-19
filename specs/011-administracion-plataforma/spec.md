# Feature Specification: Administración de la plataforma

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-18

**Status**: Draft

**Input**: Incremento 011 del usuario: RF-32 y RF-33. Depende de la spec 002 (cuentas y permisos por rol), 003
(restaurantes y su aprobación), 007 (estados del pedido) y 008 (entregas y su asignación). Fuera de alcance:
aprobación o rechazo del registro de restaurantes (spec 003), asignación manual de entregas (spec 008),
reembolsos y conciliación contable, reportes y estadísticas avanzadas y moderación de calificaciones.

## Clarifications

### Session 2026-11-18

- Q: ¿Desactivar un restaurante o repartidor bloquea también el inicio de sesión de su titular? → A: No; el
  titular sigue entrando y viendo su historial, y solo pierde los pedidos nuevos (restaurante) o la candidatura
  a entregas nuevas (repartidor). Bloquear el acceso exige desactivar también su cuenta de usuario.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El administrador consulta usuarios, restaurantes y repartidores (Priority: P1)

Como administrador, quiero listar y buscar los usuarios, restaurantes y repartidores registrados y ver el
detalle de cada uno, para conocer quién participa en la plataforma y su situación actual.

**Why this priority**: Es la base de RF-32; sin visibilidad no se puede gestionar ninguna cuenta.

**Independent Test**: Con cuentas de cada tipo registradas, listar cada categoría, buscar por nombre y abrir el
detalle de una cuenta, comprobando que se muestran los datos y su estado (activa o desactivada).

**Acceptance Scenarios**:

1. **Given** hay usuarios, restaurantes y repartidores registrados, **When** el administrador lista cada
   categoría, **Then** ve las cuentas de esa categoría con su nombre y su estado (activa o desactivada), paginadas.
2. **Given** existen varias cuentas, **When** el administrador busca por nombre o correo, **Then** solo aparecen
   las que coinciden.
3. **Given** una cuenta existente, **When** el administrador abre su detalle, **Then** ve sus datos de registro,
   su estado y el historial de acciones administrativas sobre ella.
4. **Given** un identificador inexistente, **When** el administrador consulta su detalle, **Then** recibe una
   respuesta de recurso no encontrado.

---

### User Story 2 - El administrador activa y desactiva cuentas (Priority: P1)

Como administrador, quiero desactivar y reactivar cuentas de usuarios, restaurantes y repartidores, para
suspender a quien incumpla las reglas sin perder su historial.

**Why this priority**: Es la acción de gestión central de RF-32 y tiene efectos en el resto de la plataforma.

**Independent Test**: Desactivar una cuenta y comprobar que no puede iniciar sesión; reactivarla y comprobar
que vuelve a poder hacerlo.

**Acceptance Scenarios**:

1. **Given** una cuenta de usuario activa, **When** el administrador la desactiva, **Then** la cuenta queda
   desactivada y su titular ya no puede iniciar sesión ni usar una sesión abierta.
2. **Given** una cuenta desactivada, **When** el administrador la reactiva, **Then** su titular puede iniciar
   sesión de nuevo.
3. **Given** un restaurante activo, **When** el administrador lo desactiva, **Then** deja de aparecer en la
   búsqueda y no puede recibir pedidos nuevos; su titular sigue pudiendo iniciar sesión y ver su historial.
4. **Given** un restaurante desactivado, **When** el administrador lo reactiva, **Then** vuelve a aparecer en
   la búsqueda y puede recibir pedidos (si cumple las demás condiciones de la spec 003).
5. **Given** un repartidor activo, **When** el administrador lo desactiva, **Then** deja de ser candidato para
   nuevas entregas; su titular sigue pudiendo iniciar sesión y ver su historial.
6. **Given** una cuenta ya en el estado pedido, **When** el administrador solicita el mismo cambio, **Then** el
   sistema lo informa sin duplicar el registro de la acción.
7. **Given** cualquier cuenta, **When** se busca una operación de eliminación, **Then** no existe: las cuentas
   solo se desactivan.

---

### User Story 3 - El administrador consulta pedidos y entregas por estado (Priority: P1)

Como administrador, quiero ver todos los pedidos y entregas de la plataforma filtrados por estado, con
paginación y del más reciente al más antiguo, para supervisar la operación.

**Why this priority**: Es el valor completo de RF-33.

**Independent Test**: Con pedidos y entregas en distintos estados, filtrar por un estado y comprobar que solo
aparecen los de ese estado, ordenados del más reciente al más antiguo y paginados.

**Acceptance Scenarios**:

1. **Given** hay pedidos de varios clientes y restaurantes, **When** el administrador lista los pedidos,
   **Then** ve los de toda la plataforma, del más reciente al más antiguo, paginados.
2. **Given** pedidos en distintos estados, **When** filtra por un estado, **Then** solo aparecen los que están
   en ese estado.
3. **Given** entregas en distintos estados, **When** el administrador lista las entregas y filtra por estado,
   **Then** solo aparecen las de ese estado, con el mismo orden y paginación.
4. **Given** un estado inexistente en el filtro, **When** se consulta, **Then** el sistema rechaza el filtro
   con un error de validación.
5. **Given** un filtro sin coincidencias, **When** se consulta, **Then** se devuelve una lista vacía, no un error.

---

### User Story 4 - El administrador ve el detalle de un pedido o entrega (Priority: P2)

Como administrador, quiero abrir el detalle de un pedido o entrega con su estado de pago y sus cambios de
estado, para entender qué ocurrió y atender incidencias.

**Why this priority**: Complementa el listado; el listado ya aporta valor por sí solo.

**Independent Test**: Abrir el detalle de un pedido entregado y comprobar que muestra cliente, restaurante,
repartidor, productos, importes, estado de pago y la secuencia de cambios de estado con fecha.

**Acceptance Scenarios**:

1. **Given** un pedido con pago, **When** el administrador abre su detalle, **Then** ve cliente, restaurante,
   productos, importes, estado del pago y los cambios de estado en orden cronológico con su fecha.
2. **Given** un pedido con entrega asignada, **When** abre el detalle, **Then** ve también el repartidor y el
   estado de la entrega.
3. **Given** una entrega, **When** el administrador abre su detalle, **Then** ve su estado, sus cambios de
   estado y el pedido asociado con su estado de pago.

---

### User Story 5 - Acceso exclusivo del administrador y registro de acciones (Priority: P1)

Como responsable de la plataforma, quiero que solo el administrador acceda a estas funciones y que cada acción
sobre cuentas quede registrada con fecha y responsable, para garantizar control y trazabilidad.

**Why this priority**: Sin esta restricción y trazabilidad, las acciones de gestión serían un riesgo de seguridad.

**Independent Test**: Intentar cada función con cliente, restaurante, repartidor y sin sesión (acceso denegado
o no autenticado), y comprobar que cada activación o desactivación queda registrada.

**Acceptance Scenarios**:

1. **Given** un usuario con rol cliente, restaurante o repartidor, **When** intenta cualquier función de
   administración, **Then** recibe una respuesta de acceso denegado y no se ejecuta nada.
2. **Given** una persona sin sesión válida, **When** intenta una función de administración, **Then** recibe una
   respuesta de no autenticado.
3. **Given** el administrador activa o desactiva una cuenta, **When** se consulta el detalle de esa cuenta,
   **Then** el registro muestra la acción, la fecha y el administrador responsable.

---

### Edge Cases

- El administrador intenta desactivar su propia cuenta: se rechaza para no dejar la plataforma sin administración.
- El administrador intenta desactivar la cuenta de otro administrador: se rechaza; la gestión alcanza solo a
  usuarios (clientes), restaurantes y repartidores.
- Se desactiva un restaurante con pedidos en curso: esos pedidos siguen su flujo normal hasta su estado final;
  solo se bloquean pedidos nuevos.
- Se desactiva un repartidor con una entrega ya asignada o en curso: esa entrega continúa; solo deja de ser
  candidato para entregas nuevas.
- Se desactiva a un titular con sesión abierta: su siguiente solicitud es rechazada como cuenta no habilitada.
- Un restaurante pendiente de aprobación o rechazado aparece en el listado con su estado de registro, pero
  aprobarlo o rechazarlo no se hace aquí (spec 003).
- El historial, pedidos, entregas y calificaciones de una cuenta desactivada se conservan intactos y siguen
  visibles para el administrador.
- Activar la operación de un restaurante pendiente o rechazado no lo aprueba ni lo hace visible: la aprobación
  sigue dependiendo solo de la spec 003.
- Parámetros de paginación inválidos (página o tamaño fuera de rango) se rechazan con error de validación.
- Dos administradores cambian a la vez el estado de la misma cuenta: el resultado final es coherente y cada
  acción efectiva queda registrada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador listar usuarios, restaurantes y repartidores por
  separado, paginados, mostrando de cada cuenta su nombre y su estado de acceso (activa o desactivada); en
  restaurantes y repartidores, además, su estado de operación (activo o inactivo para recibir pedidos o entregas).
- **FR-002**: El sistema MUST permitir al administrador buscar cuentas por nombre o correo dentro de cada
  categoría, y filtrarlas por estado: de acceso en usuarios, y de operación en restaurantes y repartidores.
- **FR-003**: El sistema MUST mostrar el detalle de una cuenta (datos de registro, estado y registro de
  acciones administrativas) y responder recurso no encontrado si no existe.
- **FR-004**: El sistema MUST permitir al administrador desactivar y reactivar cuentas de usuarios,
  restaurantes y repartidores.
- **FR-005**: Una cuenta de usuario desactivada MUST NOT poder iniciar sesión, y sus sesiones abiertas MUST
  dejar de ser aceptadas.
- **FR-006**: Un restaurante desactivado MUST dejar de aparecer en la búsqueda y de poder recibir pedidos
  nuevos; los pedidos ya en curso MUST continuar su flujo hasta un estado final. Esto MUST NOT bloquear el
  inicio de sesión de su titular ni su acceso a su historial.
- **FR-007**: Un repartidor desactivado MUST dejar de ser candidato para nuevas entregas; las entregas ya
  asignadas o en curso MUST continuar. Esto MUST NOT bloquear el inicio de sesión del repartidor ni su acceso
  a su historial.
- **FR-008**: El sistema MUST NOT ofrecer la eliminación de cuentas; MUST conservar todo su historial (pedidos,
  entregas, calificaciones) al desactivarlas.
- **FR-009**: El sistema MUST rechazar que el administrador desactive su propia cuenta o la de otro
  administrador.
- **FR-010**: Solicitar un cambio a un estado que la cuenta ya tiene MUST informarse sin error grave y sin
  generar un nuevo registro de acción.
- **FR-011**: El sistema MUST permitir al administrador listar todos los pedidos de la plataforma, filtrables
  por estado, paginados y ordenados del más reciente al más antiguo.
- **FR-012**: El sistema MUST permitir al administrador listar todas las entregas de la plataforma, filtrables
  por estado, paginadas y ordenadas del más reciente al más antiguo.
- **FR-013**: El sistema MUST rechazar con error de validación un estado de filtro inexistente o parámetros de
  paginación inválidos, y MUST devolver lista vacía cuando no haya coincidencias.
- **FR-014**: El detalle de un pedido MUST mostrar cliente, restaurante, repartidor (si lo hay), productos,
  importes, estado del pago y los cambios de estado en orden cronológico con su fecha.
- **FR-015**: El detalle de una entrega MUST mostrar su estado, sus cambios de estado con fecha, el repartidor
  y el pedido asociado con su estado de pago.
- **FR-016**: Solo los usuarios con rol administrador MUST acceder a estas funciones; cualquier otro rol MUST
  recibir acceso denegado y una persona sin sesión válida MUST recibir no autenticado, sin que se ejecute ninguna
  acción.
- **FR-017**: El sistema MUST registrar cada activación o desactivación efectiva con la cuenta afectada, la
  acción, la fecha y el administrador responsable, y este registro MUST ser consultable y no modificable.
- **FR-018**: Las funciones de administración MUST ser de solo lectura sobre pedidos y entregas: no cambian su
  estado ni los reasignan.
- **FR-019**: El administrador MUST ver los datos de contacto necesarios para gestionar a la cuenta, pero MUST
  NOT exponerse contraseñas ni credenciales en ninguna respuesta.

### Key Entities

- **Cuenta (usuario, restaurante, repartidor)**: titular registrado en la plataforma con un estado activa o
  desactivada, que determina si puede operar; nunca se elimina.
- **Registro de acción administrativa**: constancia inmutable de una activación o desactivación: cuenta
  afectada, acción, fecha y administrador responsable.
- **Pedido**: operación de compra con su estado, importes, estado de pago e historial de cambios de estado
  (specs 006 y 007), consultada aquí sin modificarla.
- **Entrega**: traslado de un pedido con su repartidor, estado e historial de cambios (spec 008), consultada
  aquí sin modificarla.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador encuentra una cuenta concreta mediante búsqueda y abre su detalle en menos de
  1 minuto.
- **SC-002**: El 100 % de las cuentas de usuario con el acceso desactivado es rechazado al intentar iniciar
  sesión, y el 100 % de las reactivadas puede hacerlo. Desactivar la operación de un restaurante o repartidor no
  afecta al inicio de sesión de su titular.
- **SC-003**: Un restaurante desactivado no aparece en ninguna búsqueda ni recibe pedidos nuevos, y un
  repartidor desactivado no es candidato en ninguna asignación nueva (0 excepciones).
- **SC-004**: Los listados filtrados por estado muestran únicamente elementos de ese estado, del más reciente
  al más antiguo, en el 100 % de las consultas.
- **SC-005**: El 100 % de los intentos de clientes, restaurantes, repartidores o personas sin sesión sobre estas
  funciones es rechazado sin ejecutar ninguna acción.
- **SC-006**: El 100 % de las activaciones y desactivaciones efectivas queda registrado con fecha y
  responsable, y se verifica en el detalle de la cuenta.
- **SC-007**: Tras desactivar cuentas, el 100 % de su historial de pedidos, entregas y calificaciones sigue
  disponible y sin cambios.

## Assumptions

- Las cuentas de administrador ya existen en la plataforma (spec 002); este incremento no las crea ni las
  gestiona. "Usuarios" son las cuentas de acceso de clientes, restaurantes y repartidores (filtrables por rol);
  los listados de restaurantes y repartidores son además la vista operativa de esos mismos titulares.
- La aprobación del registro de un restaurante (spec 003) es independiente de su activación: un restaurante
  debe estar aprobado y activo para aparecer y recibir pedidos. Desactivar un restaurante o un repartidor no
  desactiva la cuenta de usuario de su titular; para bloquear su acceso se desactiva también esa cuenta.
- Los estados de pedido y de entrega válidos para filtrar son los definidos en las specs 007 y 008.
- Desactivar no cancela pedidos ni entregas en curso; la intervención sobre ellos queda fuera de alcance.
- No se solicita motivo al desactivar; el registro incluye acción, fecha y responsable.
- La paginación y el orden siguen las mismas convenciones que los historiales de la spec 010.
- Reembolsos, conciliación contable, reportes y estadísticas, moderación de calificaciones y asignación manual
  de entregas quedan fuera de alcance.
