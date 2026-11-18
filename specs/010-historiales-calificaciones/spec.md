# Feature Specification: Historiales y calificaciones

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-18

**Status**: Draft

**Input**: Incremento 010 del usuario: RF-30 y RF-31. Depende de la spec 002 (cuentas y permisos), 006
(pedidos y pagos), 007 (estados del pedido) y 008 (entregas, con el estado Entregado). Fuera de alcance:
vistas de administración de pedidos y entregas (spec 011), reportes y estadísticas avanzadas, reseñas con
texto libre, moderación de calificaciones y cualquier reembolso.

## Clarifications

### Session 2026-11-18

- Q: ¿Qué entregas incluye el historial del repartidor? → A: Las Entregadas y también las que tiene
  asignadas o recogidas y aún están en curso.
- Q: ¿Qué datos del cliente ven el restaurante y el repartidor en su historial y calificaciones? → A: Solo
  el nombre del cliente, sin dirección ni contacto.
- Q: ¿Qué ve el repartidor de cada entrega en su historial? → A: Estado, productos (nombre y cantidad), la
  dirección de entrega y el costo de envío; no ve la comisión ni los demás importes del pedido. (Ajusta la
  respuesta anterior: la dirección de entrega sí, el teléfono y otros datos de contacto no.)
- Q: ¿El restaurante y el repartidor desactivados conservan acceso a su propio historial? → A: Sí; la lectura
  no exige que el restaurante o el repartidor estén habilitados para recibir pedidos. Una cuenta bloqueada ya no
  puede iniciar sesión, y eso lo cubre RF-04.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El cliente consulta el historial de sus pedidos (Priority: P1)

Como cliente, quiero consultar el historial de todos mis pedidos, incluidos los rechazados y cancelados,
con su estado final, productos, importes y estado del pago, para revisar lo que pedí y lo que pagué.

**Why this priority**: Es el valor central de RF-30 para el actor principal y no depende de las calificaciones.

**Independent Test**: Con un cliente que tiene pedidos entregados, rechazados y cancelados, consultar el
historial y comprobar que aparecen todos, solo los suyos, con estado, productos, importes y pago.

**Acceptance Scenarios**:

1. **Given** un cliente con pedidos en distintos estados finales, **When** consulta su historial, **Then**
   ve todos sus pedidos, incluidos Rechazado y Cancelado, con estado, productos, importes y estado del pago.
2. **Given** un cliente y pedidos de otros clientes, **When** consulta su historial, **Then** no aparece
   ningún pedido ajeno.
3. **Given** un cliente sin pedidos, **When** consulta su historial, **Then** ve un listado vacío con un
   mensaje claro.
4. **Given** una persona sin sesión, **When** intenta consultar un historial, **Then** se deniega.

---

### User Story 2 - El cliente califica un pedido entregado (Priority: P1)

Como cliente, quiero calificar con un valor de 1 a 5 un pedido mío que ya me fue entregado, para expresar
mi nivel de satisfacción.

**Why this priority**: Cubre RF-31 completo y es el otro requisito del incremento.

**Independent Test**: Con un pedido propio Entregado, enviar una calificación de 1 a 5 y comprobar que
queda registrada y que un segundo intento se rechaza.

**Acceptance Scenarios**:

1. **Given** un pedido propio Entregado sin calificar, **When** el cliente envía un valor entero de 1 a 5,
   **Then** la calificación queda registrada y se muestra en el pedido.
2. **Given** un pedido ya calificado, **When** el cliente intenta calificarlo de nuevo, **Then** se rechaza
   y la calificación original no cambia.
3. **Given** un pedido que no está Entregado (en curso, rechazado o cancelado), **When** el cliente intenta
   calificarlo, **Then** se rechaza.
4. **Given** un pedido de otro cliente, **When** un cliente intenta calificarlo, **Then** se deniega sin
   revelar si existe.
5. **Given** un valor fuera de 1 a 5, no entero o ausente, **When** se envía, **Then** se rechaza y no se
   registra nada.

---

### User Story 3 - El restaurante consulta su historial y sus calificaciones (Priority: P2)

Como restaurante, quiero consultar el historial de los pedidos que recibí y las calificaciones que
corresponden a ellos, para conocer mi actividad y la satisfacción de mis clientes.

**Why this priority**: Completa RF-30 para el restaurante y da valor a las calificaciones.

**Independent Test**: Con un restaurante que recibió pedidos en varios estados, algunos calificados,
consultar el historial y comprobar que solo ve los suyos con la calificación cuando existe.

**Acceptance Scenarios**:

1. **Given** un restaurante con pedidos recibidos, **When** consulta su historial, **Then** ve únicamente los
   pedidos dirigidos a él, con su estado final y las calificaciones de los que las tienen.
2. **Given** pedidos de otro restaurante, **When** este restaurante consulta su historial, **Then** no los ve.
3. **Given** un pedido sin calificar, **When** el restaurante lo consulta, **Then** se indica que aún no
   tiene calificación.

---

### User Story 4 - El repartidor consulta su historial y sus calificaciones (Priority: P2)

Como repartidor, quiero consultar el historial de las entregas que realicé y las calificaciones de esos
pedidos, para llevar registro de mi trabajo.

**Why this priority**: Completa RF-30 para el tercer actor con historial.

**Independent Test**: Con un repartidor con entregas realizadas, consultar el historial y comprobar que solo
ve sus entregas, con la calificación del pedido cuando existe.

**Acceptance Scenarios**:

1. **Given** un repartidor con entregas Entregadas y otras asignadas o recogidas en curso, **When**
   consulta su historial, **Then** ve únicamente sus entregas (concluidas y en curso) con su estado actual y
   la calificación del pedido si la hay.
2. **Given** entregas de otros repartidores, **When** consulta su historial, **Then** no las ve.
3. **Given** una entrega suya, **When** la ve en su historial, **Then** aparecen los productos (nombre y
   cantidad), la dirección de entrega y el costo de envío, y no la comisión, los demás importes, los precios ni
   el estado del pago.
4. **Given** un repartidor que ya no está habilitado para recibir entregas pero cuya cuenta puede iniciar
   sesión, **When** consulta su historial, **Then** lo ve completo.

---

### User Story 5 - Paginación, orden y filtros del historial (Priority: P2)

Como cliente, restaurante o repartidor, quiero que mi historial se presente paginado, del más reciente al
más antiguo, y poder filtrarlo por estado y por fecha, para encontrar rápido una operación.

**Why this priority**: Mejora el uso del historial cuando crece, sin cambiar qué datos se muestran.

**Independent Test**: Con más pedidos que el tamaño de página, recorrer las páginas y aplicar filtros de
estado y de rango de fechas.

**Acceptance Scenarios**:

1. **Given** más operaciones que el tamaño de página, **When** se consulta el historial, **Then** se muestra
   una página en orden del más reciente al más antiguo y se puede avanzar a las siguientes sin repetir ni
   omitir operaciones.
2. **Given** un filtro por estado, **When** se aplica, **Then** solo aparecen operaciones en ese estado.
3. **Given** un filtro por rango de fechas, **When** se aplica, **Then** solo aparecen operaciones dentro
   del rango, incluidos ambos extremos.
4. **Given** un rango con fecha inicial posterior a la final, **When** se aplica, **Then** se rechaza con un
   mensaje claro.

---

### User Story 6 - El historial conserva los datos del momento del pedido (Priority: P2)

Como cliente, restaurante o repartidor, quiero que el historial muestre nombres, precios e importes tal como
eran al hacer el pedido, aunque después el catálogo cambie, para que mi registro sea fiel.

**Why this priority**: Garantiza la exactitud del historial como registro de lo ocurrido.

**Independent Test**: Hacer un pedido, cambiar el precio y el nombre de un producto y luego retirarlo del
catálogo; el historial debe seguir mostrando los valores originales.

**Acceptance Scenarios**:

1. **Given** un pedido cuyo producto cambió de nombre o precio después, **When** se consulta el historial,
   **Then** se ven el nombre y el precio originales y los mismos importes.
2. **Given** un pedido con un producto retirado del catálogo, **When** se consulta el historial, **Then** el
   producto sigue apareciendo con sus datos originales.

---

### Edge Cases

- Cliente, restaurante o repartidor sin operaciones: listado vacío con mensaje claro, no un error.
- Página solicitada fuera del rango existente: resultado vacío coherente, sin error.
- Dos intentos simultáneos de calificar el mismo pedido: solo uno se registra y el otro se rechaza.
- Pedido Entregado cuyo restaurante o repartidor dejó de estar habilitado después: sigue apareciendo en el
  historial del cliente, que puede calificarlo, y en el del propio restaurante o repartidor, que conservan el
  acceso a su historial mientras su cuenta pueda iniciar sesión.
- Cuenta bloqueada: no puede iniciar sesión y, por tanto, no accede a ningún historial (RF-04).
- Pedido con pago no completado (por ejemplo Rechazado o Cancelado): se muestra con el estado de pago real
  que tenga, sin sugerir un cobro inexistente.
- Intento de ver el historial de otro usuario modificando identificadores: se deniega sin revelar datos.
- Entregas reasignadas: el historial del repartidor incluye solo las que tiene o tuvo a su cargo hasta
  entregarlas, no las que se le quitaron.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al cliente autenticado consultar el historial de sus propios pedidos,
  incluidos los Rechazados y Cancelados, mostrando por cada uno su estado final, los productos con
  cantidades, los importes y el estado del pago. *(RF-30)*
- **FR-002**: El sistema MUST permitir al restaurante autenticado consultar el historial de los pedidos que
  recibió, aunque ya no esté habilitado para recibir pedidos. *(RF-30)*
- **FR-003**: El sistema MUST permitir al repartidor autenticado consultar el historial de sus entregas: las
  Entregadas y también las que tiene asignadas o recogidas y aún están en curso, aunque ya no esté habilitado
  para recibir entregas. *(RF-30)*
- **FR-004**: El sistema MUST mostrar a cada usuario únicamente las operaciones que le corresponden y MUST
  denegar el acceso a operaciones ajenas sin revelar si existen; una persona sin sesión MUST ser denegada.
- **FR-005**: El historial MUST mostrar nombres de productos, precios e importes tal como eran al momento
  del pedido, sin verse alterados por cambios posteriores del catálogo ni por el retiro de productos.
- **FR-006**: El historial MUST presentarse paginado y ordenado del más reciente al más antiguo, de modo que
  recorrer todas las páginas no repita ni omita operaciones.
- **FR-007**: El historial MUST poder filtrarse por estado y por rango de fechas, combinables entre sí; un
  rango con fecha inicial posterior a la final MUST rechazarse con un mensaje claro.
- **FR-008**: El sistema MUST permitir al cliente calificar con un valor entero de 1 a 5 un pedido propio en
  estado Entregado. *(RF-31)*
- **FR-009**: El sistema MUST admitir una sola calificación por pedido y MUST rechazar cualquier intento
  posterior sin modificar la original, incluso si se envían de forma simultánea.
- **FR-010**: El sistema MUST rechazar la calificación de un pedido que no esté Entregado y MUST denegar la
  de un pedido ajeno, sin revelar datos del pedido.
- **FR-011**: El sistema MUST rechazar valores no enteros, fuera de 1 a 5 o ausentes, sin registrar nada.
- **FR-012**: El sistema MUST permitir al restaurante ver las calificaciones de los pedidos que recibió y al
  repartidor ver las calificaciones de las entregas que realizó, indicando cuando un pedido aún no tiene
  calificación.
- **FR-013**: El cliente MUST ver la calificación que emitió en cada pedido de su historial, o la indicación
  de que puede calificarlo cuando el pedido está Entregado y sin calificar.
- **FR-014**: La calificación MUST ser inmutable: no puede editarse ni eliminarse en este incremento.
- **FR-015**: El sistema MUST registrar de cada calificación el pedido, el cliente que la emitió, el valor y
  la fecha y hora.
- **FR-016**: En el historial y en las calificaciones, el restaurante MUST ver del cliente únicamente su nombre;
  el repartidor MUST ver su nombre y la dirección de entrega del pedido. Ninguno de los dos MUST ver el
  teléfono ni otros datos de contacto del cliente.
- **FR-017**: En el historial del repartidor, cada entrega MUST mostrar el estado, los productos (nombre y
  cantidad), la dirección de entrega, el costo de envío y la calificación del pedido; MUST NOT mostrar la
  comisión, el subtotal, el total, los precios de los productos ni el estado del pago.

### Key Entities

- **Historial**: vista de solo lectura de pedidos o entregas pasadas de un usuario, con estado final, fecha,
  productos, importes y pago, filtrable y paginada.
- **Pedido (histórico)**: pedido de la spec 006 con su estado final (spec 007/008) y los datos congelados al
  momento de confirmarlo (productos, precios, importes).
- **Entrega**: asignación y entrega del pedido a un repartidor (spec 008), de la que se deriva el historial
  del repartidor.
- **Calificación**: valor entero de 1 a 5 emitido por el cliente dueño de un pedido Entregado; una por
  pedido; con fecha y hora; visible para el cliente, el restaurante y el repartidor del pedido.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario con hasta 1.000 operaciones ve la primera página de su historial en menos de 2
  segundos.
- **SC-002**: En el 100 % de las pruebas con datos de varios usuarios, cada usuario ve solo sus operaciones
  y ningún intento de ver las ajenas revela datos.
- **SC-003**: En el 100 % de los casos probados, el historial muestra nombres, precios e importes idénticos
  a los del momento del pedido tras cambiar o retirar el producto.
- **SC-004**: Un cliente califica un pedido entregado en menos de 30 segundos desde que abre su historial.
- **SC-005**: En el 100 % de los casos probados, un pedido nunca acumula más de una calificación, incluso con
  envíos simultáneos, y nunca se acepta una calificación de un pedido no entregado, ajeno o con valor
  inválido.
- **SC-006**: Recorrer todas las páginas de un historial muestra cada operación exactamente una vez, en
  orden del más reciente al más antiguo.
- **SC-007**: Los filtros por estado y por fecha devuelven exactamente las operaciones que cumplen la
  condición en el 100 % de los casos probados.

## Assumptions

- Las cuentas, los roles y los permisos son los de la spec 002; los pedidos, importes y pagos, los de la spec
  006; los estados, los de la spec 007; y las entregas, con Entregado, los de la spec 008.
- Los estados finales de un pedido son Entregado, Rechazado y Cancelado; el filtro por estado admite
  cualquier estado del pedido, ya que el historial incluye también pedidos aún en curso.
- El historial del cliente y del restaurante lista pedidos; el del repartidor lista sus entregas: las
  Entregadas y las asignadas o recogidas en curso a su cargo.
- El filtro por estado del repartidor admite solo Asignado, Recogido y Entregado, los estados que incluye su
  historial.
- El orden «más reciente» usa la fecha de creación del pedido en los tres historiales.
- El tamaño de página por defecto es de 20 operaciones, con un máximo razonable por solicitud.
- El filtro de fechas se aplica sobre la fecha de creación del pedido, con extremos inclusivos, en la zona
  horaria de la plataforma.
- La calificación es solo numérica; no hay comentario, edición, eliminación ni moderación.
- No se calcula ni muestra promedio ni estadística agregada; eso queda para reportes futuros.
- Las vistas del administrador sobre pedidos y entregas pertenecen a la spec 011.
- La interfaz es web y sigue las convenciones ya usadas en incrementos anteriores.
