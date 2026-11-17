# Feature Specification: Registro, aprobación y datos del restaurante

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-17

**Status**: Ready for planning

**Input**: Incremento 003 del usuario: RF-05, RF-06 y RF-07, con la autorización de RF-04 aplicada
a estas operaciones. Depende de la spec 002 (cuentas, autenticación y rol). Fuera de alcance:
catálogo de productos (spec 004), pedidos y pagos.

## Clarifications

### Session 2026-11-17

- Q: ¿Cuándo se piden los datos del negocio? → A: Tras iniciar sesión con la cuenta de restaurante
  de la spec 002; el registro público no cambia (decisión del usuario).
- Q: ¿Qué puede hacer un restaurante rechazado? → A: Ver el motivo, corregir y reenviar; la
  solicitud vuelve a pendiente (decisión del usuario).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Solicitar el registro del restaurante (Priority: P1)

Como titular de una cuenta de restaurante, quiero enviar los datos generales de mi negocio para
que la plataforma revise mi solicitud.

**Why this priority**: Sin solicitud no hay nada que aprobar ni administrar (RF-05).

**Independent Test**: Con una cuenta de restaurante recién registrada, enviar la solicitud con
datos válidos y comprobar, tras reiniciar la aplicación, que queda guardada en estado pendiente.

**Acceptance Scenarios**:

1. **Given** una cuenta de restaurante habilitada y sin solicitud, **When** completa los datos
   generales obligatorios y envía la solicitud, **Then** se guarda una única solicitud asociada a su
   cuenta en estado **pendiente** y se le muestra ese estado.
2. **Given** datos obligatorios vacíos, demasiado largos o con formato inválido, **When** envía la
   solicitud, **Then** se explica cada error y no se guarda ni modifica ninguna solicitud.
3. **Given** una solicitud pendiente, **When** su titular consulta su restaurante, **Then** ve sus
   datos y el estado pendiente, y se le indica que aún no puede recibir pedidos.
4. **Given** una solicitud pendiente, **When** su titular corrige sus datos generales, **Then** los
   cambios se guardan y la solicitud sigue pendiente.
5. **Given** una solicitud, **When** se intenta indicar desde el cliente un estado aprobado, otra
   cuenta como titular o una segunda solicitud para la misma cuenta, **Then** se rechaza sin cambios.
6. **Given** una cuenta de cliente, repartidor o administrador, o una persona sin sesión, **When**
   intenta enviar una solicitud de restaurante, **Then** se deniega (403 o 401 respectivamente).

### User Story 2 - Revisar y resolver solicitudes (Priority: P1)

Como administrador, quiero ver las solicitudes pendientes y aprobarlas o rechazarlas para que solo
los restaurantes aprobados queden habilitados para recibir pedidos.

**Why this priority**: Es el control de RF-06 y la condición de seguridad de la constitución.

**Independent Test**: Con varias solicitudes pendientes, aprobar una y rechazar otra con motivo;
comprobar el estado persistido y la habilitación resultante de cada restaurante.

**Acceptance Scenarios**:

1. **Given** solicitudes en distintos estados, **When** el administrador abre la revisión,
   **Then** ve las solicitudes pendientes, de la más antigua a la más reciente, con sus datos
   generales, titular y fecha de envío.
2. **Given** una solicitud pendiente, **When** el administrador la aprueba, **Then** el restaurante
   pasa a **aprobado**, queda registrado quién y cuándo lo aprobó, y su titular ve el nuevo estado.
3. **Given** una solicitud pendiente, **When** el administrador la rechaza indicando un motivo,
   **Then** pasa a **rechazado**, el titular ve el estado y el motivo, y no queda habilitado.
4. **Given** un rechazo sin motivo, **When** se confirma, **Then** se pide el motivo y no cambia el estado.
5. **Given** un restaurante rechazado, **When** su titular corrige sus datos y reenvía la solicitud,
   **Then** vuelve a pendiente y aparece de nuevo en la lista del administrador.
6. **Given** una solicitud ya aprobada o rechazada, **When** se intenta aprobarla o rechazarla otra
   vez, incluso por dos administradores a la vez, **Then** solo prospera la primera resolución y la
   otra recibe un error controlado.
7. **Given** una cuenta que no es administradora, **When** intenta listar o resolver solicitudes,
   **Then** se deniega (401 sin sesión, 403 con otro rol o cuenta inhabilitada).

### User Story 3 - Administrar datos, horarios y disponibilidad (Priority: P2)

Como restaurante aprobado, quiero mantener mis datos generales, mis horarios de atención y mi
disponibilidad para que la plataforma sepa cuándo puedo recibir pedidos.

**Why this priority**: Completa RF-07; depende de que exista un restaurante aprobado.

**Independent Test**: Con un restaurante aprobado, editar sus datos, definir horarios semanales y
pausar y reanudar su disponibilidad; comprobar los valores persistidos y la habilitación resultante.

**Acceptance Scenarios**:

1. **Given** un restaurante aprobado, **When** modifica sus datos generales válidos, **Then** se
   guardan sin volver a requerir aprobación y sin alterar su estado.
2. **Given** un restaurante aprobado, **When** define para cada día de la semana un horario de
   apertura y cierre, o lo marca como cerrado, **Then** se guarda su horario semanal.
3. **Given** un horario con cierre igual o anterior a la apertura, o un día sin ambos valores,
   **When** lo guarda, **Then** se explica el error y se conserva el horario anterior.
4. **Given** un restaurante aprobado, **When** se marca como no disponible y luego como disponible,
   **Then** cada cambio se guarda y se refleja en su habilitación para recibir pedidos.
5. **Given** un restaurante pendiente o rechazado, **When** intenta cambiar horarios o
   disponibilidad, **Then** se deniega (403) sin cambios.
6. **Given** la sesión de un restaurante, **When** intenta consultar o modificar el restaurante de
   otra cuenta, **Then** se deniega sin revelar sus datos.

### Edge Cases

- Envíos repetidos o simultáneos de la solicitud de una misma cuenta: existe como máximo una solicitud.
- Resoluciones simultáneas de una misma solicitud: queda un único estado final y una única resolución.
- Cuenta de restaurante inhabilitada (estado de acceso de la spec 002) con restaurante aprobado:
  no puede operar ni queda habilitada para recibir pedidos.
- Solicitud vacía (ninguna pendiente): el administrador ve un aviso de lista vacía.
- Horarios que cruzan la medianoche: no se admiten en este incremento; se informa el error.
- Fallo al guardar: no se confirma un estado o cambio que no se persistió; se puede reintentar.
- Restaurante rechazado: ve el motivo, puede corregir sus datos y reenviar la solicitud, que vuelve
  a pendiente; sigue sin horarios ni disponibilidad editables hasta ser aprobado.

## Requirements *(mandatory)*

### Functional Requirements

Los códigos RF originales se conservan; las referencias FR son internas de esta especificación.

- **FR-001 (RF-05)**: Una cuenta de restaurante habilitada DEBE poder enviar una única solicitud
  con sus datos generales: nombre comercial, categoría o tipo de cocina, descripción breve,
  dirección, distrito o zona y teléfono de contacto. La solicitud se envía desde la sesión, después
  de crear la cuenta con el registro público de la spec 002, que no cambia.
- **FR-002 (RF-05)**: El sistema DEBE validar los datos obligatorios y sus longitudes también al
  procesarlos, y guardar la solicitud en estado pendiente con su fecha de envío.
- **FR-003 (RF-05)**: El titular DEBE poder consultar su restaurante y su estado, y corregir los
  datos generales mientras esté pendiente.
- **FR-004 (RF-06)**: El administrador DEBE poder listar las solicitudes pendientes con sus datos,
  titular y fecha, ordenadas por antigüedad.
- **FR-005 (RF-06)**: El administrador DEBE poder aprobar o rechazar una solicitud pendiente; el
  rechazo exige un motivo. Solo se permiten las transiciones pendiente → aprobado, pendiente →
  rechazado y, por reenvío del titular, rechazado → pendiente; se registran administrador y fecha
  de cada resolución.
- **FR-005a (RF-05/RF-06)**: El titular de un restaurante rechazado DEBE ver el motivo y poder
  corregir sus datos y reenviar la solicitud, que vuelve a pendiente con nueva fecha de envío.
- **FR-006 (RF-06)**: La resolución DEBE ser única ante solicitudes simultáneas: una solicitud ya
  resuelta no puede resolverse de nuevo.
- **FR-007 (RF-06)**: El sistema DEBE determinar si un restaurante está **habilitado para recibir
  pedidos** solo cuando esté aprobado, su cuenta esté habilitada, esté marcado como disponible y
  la hora actual esté dentro de su horario del día. Pendiente o rechazado nunca está habilitado.
  Esta condición DEBE consultarse y mostrarse al titular y al administrador para que la reutilice
  el futuro incremento de pedidos.
- **FR-008 (RF-07)**: El restaurante aprobado DEBE poder editar sus datos generales sin perder la
  aprobación.
- **FR-009 (RF-07)**: El restaurante aprobado DEBE poder definir un horario semanal con, por día,
  una franja de apertura y cierre o la marca de cerrado; el cierre debe ser posterior a la apertura.
- **FR-010 (RF-07)**: El restaurante aprobado DEBE poder activar o pausar su disponibilidad; un
  restaurante recién aprobado empieza como no disponible hasta que la active.
- **FR-011 (RF-04)**: Cada operación DEBE autorizarse con información confiable de identidad, rol,
  estado de cuenta, estado del restaurante y pertenencia: 401 sin autenticación válida; 403 para
  otro rol, cuenta inhabilitada, estado de restaurante no permitido o recurso ajeno. El sistema NO
  DEBE aceptar estado, titular o aprobación enviados por el cliente.
- **FR-012**: La interfaz DEBE ofrecer al titular la solicitud y la gestión de su restaurante, y al
  administrador la revisión de solicitudes, accesibles desde su sesión.

### Key Entities *(include if feature involves data)*

- **Restaurante**: negocio asociado a una única cuenta de restaurante; datos generales, estado de
  aprobación (pendiente, aprobado, rechazado), fecha de envío, motivo de rechazo, administrador y
  fecha de resolución, y disponibilidad. Sustituye la condición comercial pendiente que la spec 002
  guardaba en la cuenta.
- **Horario de atención**: por restaurante y día de la semana, apertura y cierre o cerrado.
- **Cuenta** (spec 002): aporta identidad, rol y estado de acceso; no cambia de rol.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un titular de restaurante completa y envía su solicitud en menos de 3 minutos, y la
  solicitud se conserva pendiente tras reiniciar la aplicación.
- **SC-002**: Un administrador resuelve una solicitud pendiente en menos de 1 minuto desde la lista.
- **SC-003**: En el 100 % de los casos de prueba, un restaurante pendiente, rechazado, inhabilitado,
  no disponible o fuera de horario figura como no habilitado para recibir pedidos.
- **SC-004**: El 100 % de los accesos sin sesión, con otro rol, con cuenta inhabilitada o sobre un
  restaurante ajeno se rechaza sin revelar datos ni modificar el estado.
- **SC-005**: Ante resoluciones o solicitudes simultáneas, nunca existen dos solicitudes para una
  misma cuenta ni dos resoluciones para una misma solicitud.

## Assumptions

- **Restaurante por cuenta**: una cuenta de restaurante corresponde a un único restaurante; no hay
  sucursales en este incremento.
- **Datos generales**: los campos de FR-001 son una convención de este incremento, no atribuida a
  la propuesta. Categoría y distrito o zona se guardan como texto o lista simple para la futura
  búsqueda de RF-09; no se valida la cobertura de reparto (política pendiente de la constitución).
- **Sin imágenes ni mapas**: el logo y la ubicación en mapa quedan fuera; no se prepara Cloudinary
  ni MapTiler. La dirección es texto.
- **Horario**: una franja por día, sin cruzar la medianoche, en la hora local de la plataforma.
- **Aviso al titular**: el resultado se ve al consultar su restaurante; no se envían correos ni
  notificaciones (RF-29 fuera de alcance).
- **Fuera del incremento**: catálogo (RF-08, spec 004), búsqueda pública (RF-09/RF-10), pedidos,
  pagos, entregas, suspensión o revocación posterior de restaurantes y gestión administrativa
  general (RF-32). RF-04 sigue parcial: las relaciones con pedidos y entregas quedan pendientes.
- **Dependencias y restricciones heredadas**: spec 002 y constitución 3.0.0 (local, HTTP,
  PostgreSQL con base creada por el usuario, `ddl-auto=update`, RNF-07 aplicado a estas operaciones,
  RNF-11 con al menos 90 % de líneas de lógica de negocio, sin E2E automatizadas ni Playwright).
- **Trazabilidad**: RF-04, RF-05, RF-06 y RF-07 de la [propuesta](../../Propuesta_de_Proyecto.docx)
  y la [constitución](../../.specify/memory/constitution.md); solicitud del usuario del 2026-11-17.
