# Feature Specification: Carrito, direcciones y cálculo de importes

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-17

**Status**: Draft

**Input**: Incremento 005 del usuario: RF-11, RF-12 y RF-13. Depende de la spec 002 (cuentas y
permisos por rol), de la spec 003 (restaurantes aprobados, con sus horarios y disponibilidad) y de
la spec 004 (catálogo de productos y su consulta). Fuera de alcance: confirmación del pedido y pago
(spec 006), historial de pedidos (spec 010) y optimización con caché (spec de carga y escalamiento).

## Clarifications

### Session 2026-11-17

- Q: ¿Cómo se define la zona de reparto cubierta? → A: Lista de distritos atendidos (decisión del
  usuario).
- Q: ¿Cómo se calcula el costo de envío? → A: Tarifa fija (decisión del usuario).
- Q: ¿Cómo se calcula la comisión y quién la asume? → A: Porcentaje del subtotal, cargo adicional
  al cliente que suma al total (decisión del usuario).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Armar el carrito (Priority: P1)

Como cliente, quiero agregar productos de un restaurante a mi carrito, cambiar cantidades y quitar
productos mientras no haya confirmado el pedido, para preparar lo que voy a pedir.

**Why this priority**: Es el núcleo del incremento y la base del pedido (RF-11).

**Independent Test**: Con un cliente y un restaurante con productos disponibles, agregar productos,
cambiar una cantidad, eliminar uno y vaciar el carrito; comprobar el estado persistido tras reiniciar.

**Acceptance Scenarios**:

1. **Given** un cliente con sesión y un producto disponible de un restaurante habilitado, **When**
   lo agrega con una cantidad válida, **Then** el carrito contiene ese producto y cantidad, asociado
   a ese restaurante.
2. **Given** un producto que ya está en el carrito, **When** el cliente lo agrega de nuevo, **Then**
   se suma a la cantidad existente en lugar de duplicar la línea.
3. **Given** un producto en el carrito, **When** el cliente cambia su cantidad a un entero válido,
   **Then** la cantidad se actualiza; una cantidad cero, negativa, decimal o superior al máximo
   permitido se rechaza sin modificar el carrito.
4. **Given** un producto en el carrito, **When** el cliente lo elimina, **Then** deja de estar en el
   carrito; si era el último, el carrito queda vacío y no pertenece a ningún restaurante.
5. **Given** un carrito con productos de un restaurante, **When** el cliente intenta agregar un
   producto de otro restaurante, **Then** se rechaza con un mensaje que explica que el carrito es de
   un solo restaurante y ofrece vaciarlo para empezar con el otro; no se mezclan productos.
6. **Given** un producto no disponible, retirado, o de un restaurante pendiente, rechazado, con
   cuenta inhabilitada o que no está habilitado para recibir pedidos, **When** el cliente intenta
   agregarlo, **Then** se rechaza sin modificar el carrito.
7. **Given** un cliente, **When** consulta su carrito, **Then** ve sus productos con nombre, precio
   vigente, cantidad y subtotal de cada línea, y solo el suyo; no puede ver ni modificar el de otra
   persona.
8. **Given** un producto del carrito que dejó de estar disponible, fue retirado o cuyo restaurante
   dejó de estar habilitado, **When** el cliente consulta el carrito, **Then** la línea se marca como
   no disponible, se excluye de los importes y se le avisa.
9. **Given** una persona sin sesión o con rol distinto de cliente, **When** intenta usar el carrito,
   **Then** se deniega (401 sin sesión, 403 con otro rol) sin cambios.

---

### User Story 2 - Registrar y seleccionar direcciones de entrega (Priority: P1)

Como cliente, quiero registrar una o varias direcciones de entrega dentro de la zona de reparto
cubierta y seleccionar una para mi pedido, para que llegue al lugar correcto.

**Why this priority**: Sin dirección válida no se puede calcular el envío ni entregar (RF-12).

**Independent Test**: Registrar direcciones dentro y fuera de la zona cubierta, seleccionar una,
editarla y eliminarla; comprobar que solo las válidas se guardan y que otro cliente no las ve.

**Acceptance Scenarios**:

1. **Given** un cliente con sesión, **When** registra una dirección con los datos obligatorios
   dentro de la zona de reparto cubierta, **Then** se guarda en su libreta de direcciones.
2. **Given** una dirección fuera de la zona de reparto cubierta, **When** intenta registrarla o
   editarla hacia esa ubicación, **Then** se rechaza con un mensaje claro y no se guarda ni modifica.
3. **Given** datos obligatorios vacíos o demasiado largos, **When** intenta guardar, **Then** se
   explica cada error y no se guarda nada.
4. **Given** varias direcciones registradas, **When** el cliente selecciona una para su pedido,
   **Then** queda como la dirección seleccionada; solo una puede estar seleccionada a la vez.
5. **Given** una dirección propia, **When** el cliente la edita o la elimina, **Then** el cambio se
   guarda; si era la seleccionada, el carrito queda sin dirección seleccionada.
6. **Given** un cliente, **When** consulta sus direcciones, **Then** ve solo las suyas; no puede ver,
   seleccionar, editar ni eliminar las de otra persona (se responde como no encontrada).
7. **Given** una persona sin sesión o con rol distinto de cliente, **When** intenta gestionar
   direcciones, **Then** se deniega (401 o 403) sin cambios.

---

### User Story 3 - Ver los importes calculados (Priority: P1)

Como cliente, quiero ver el subtotal, el costo de envío, la comisión y el monto total de mi pedido,
actualizados automáticamente cuando cambio el carrito o la dirección, para saber cuánto pagaría.

**Why this priority**: Completa RF-13 y deja listo el resumen que usará la confirmación (spec 006).

**Independent Test**: Con un carrito y una dirección seleccionada, comprobar los cuatro importes;
cambiar cantidades, productos y dirección y verificar que se recalculan cada vez.

**Acceptance Scenarios**:

1. **Given** un carrito con productos disponibles, **When** el cliente lo consulta, **Then** ve el
   subtotal (suma de precio × cantidad de las líneas disponibles).
2. **Given** un carrito con una dirección seleccionada dentro de la zona cubierta, **When** el
   cliente consulta el resumen, **Then** ve además el costo de envío, la comisión y el monto total
   calculados con las reglas vigentes: el envío es una tarifa fija en soles para todo pedido con
   dirección válida.
3. **Given** el cálculo de la comisión, **When** se muestra el resumen, **Then** la comisión es un
   porcentaje del subtotal, se muestra como cargo adicional al cliente y se suma al monto total:
   total = subtotal + envío + comisión.
4. **Given** un carrito sin dirección seleccionada, **When** el cliente consulta el resumen, **Then**
   ve el subtotal y se le indica que falta seleccionar una dirección; el envío, la comisión y el total
   quedan pendientes.
5. **Given** un resumen mostrado, **When** el cliente agrega, cambia o elimina productos, o cambia la
   dirección seleccionada, **Then** los importes se recalculan y reflejan el estado vigente.
6. **Given** un precio de producto que el restaurante modificó, **When** el cliente consulta su
   carrito, **Then** los importes usan el precio vigente del producto (el carrito no guarda precios).
7. **Given** un carrito vacío, **When** se consulta el resumen, **Then** todos los importes son cero
   y se indica que el carrito está vacío.
8. **Given** los importes, **When** se calculan, **Then** se expresan en soles con dos decimales,
   redondeados de forma consistente, y el monto total es siempre coherente con sus componentes.

### Edge Cases

- Un producto del carrito se pausa, se retira o su restaurante sale de horario o se inhabilita: la
  línea se marca como no disponible y no suma; el carrito se conserva para que el cliente decida.
- Dos solicitudes simultáneas sobre el mismo carrito (por ejemplo, desde dos pestañas): no se pierde
  ni duplica una línea; el resultado refleja ambos cambios o el segundo se rechaza con un mensaje
  para recargar.
- Cantidad máxima por producto o por carrito excedida: se rechaza con un mensaje.
- Agregar un producto de otro restaurante con el carrito lleno: se rechaza o se ofrece vaciar; nunca
  se mezclan restaurantes (Escenario 1.5).
- La dirección seleccionada ya no cae en la zona cubierta (cambió la cobertura): se invalida la
  selección y se pide elegir otra.
- Texto de dirección con caracteres especiales o muy largo: se trata como texto literal y se valida
  su longitud.
- Cliente sin direcciones registradas: puede armar el carrito, y los importes dependientes de la
  dirección quedan pendientes.
- Fallo al guardar: no se confirma un cambio que no se persistió; se puede reintentar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 (RF-11)**: Un cliente con sesión DEBE poder agregar un producto disponible de un
  restaurante habilitado a su carrito con una cantidad entera positiva; agregar un producto ya
  presente suma a su cantidad.
- **FR-002 (RF-11)**: El cliente DEBE poder modificar la cantidad de un producto del carrito y
  eliminar productos, así como vaciar el carrito, mientras no haya confirmado el pedido.
- **FR-003 (RF-11)**: El carrito DEBE pertenecer a un solo restaurante a la vez; el sistema NO DEBE
  permitir mezclar productos de restaurantes distintos y DEBE ofrecer vaciar el carrito para cambiar
  de restaurante. Un carrito vacío no pertenece a ningún restaurante.
- **FR-004 (RF-11)**: El sistema DEBE aceptar solo productos disponibles, no retirados, de
  restaurantes aprobados, con cuenta habilitada y habilitados para recibir pedidos (specs 003 y
  004), y DEBE validar la cantidad (entero, mínimo 1, máximo razonable) también al procesarla.
- **FR-005 (RF-11)**: Al consultar el carrito, el sistema DEBE mostrar cada línea con el precio
  vigente del producto y marcar como no disponibles las líneas cuyo producto o restaurante dejó de
  ser elegible, excluyéndolas de los importes.
- **FR-006 (RF-12)**: Un cliente DEBE poder registrar, listar, editar y eliminar sus direcciones de
  entrega, con los datos obligatorios y longitudes máximas validados.
- **FR-007 (RF-12)**: El sistema DEBE rechazar toda dirección que esté fuera de la zona de reparto
  cubierta, al registrar y al editar, con un mensaje claro. La zona cubierta es una lista de distritos atendidos; una
  dirección es válida solo si su distrito figura en ella.
- **FR-008 (RF-12)**: El cliente DEBE poder seleccionar una de sus direcciones para su pedido; solo
  una está seleccionada a la vez, y eliminar o invalidar la seleccionada deja el carrito sin
  dirección.
- **FR-009 (RF-13)**: El sistema DEBE calcular automáticamente subtotal, costo de envío, comisión y
  monto total del carrito, y recalcularlos cada vez que cambia el carrito o la dirección
  seleccionada.
- **FR-010 (RF-13)**: Los importes DEBEN calcularse siempre en el servidor con los precios vigentes
  del catálogo; el sistema NO DEBE aceptar ni usar ningún importe, precio o total enviado por el
  cliente (un campo así se rechaza). Todo importe se expresa en soles con exactamente dos decimales y
  se redondea con una única regla: el total de cada línea es exacto (precio × cantidad); la comisión
  se redondea una sola vez, al céntimo, con medio hacia arriba; el monto total es la suma de los
  componentes ya redondeados (subtotal + envío + comisión), de modo que siempre coincide con ellos
  al céntimo. Ninguna etapa usa aritmética de coma flotante.
- **FR-011 (RF-13)**: Sin dirección seleccionada, el sistema DEBE mostrar el subtotal e indicar que
  el envío, la comisión y el total quedan pendientes; con carrito vacío, todos los importes son cero.
- **FR-012 (RF-04)**: Solo un cliente con sesión válida y cuenta habilitada DEBE usar el carrito, las
  direcciones y los importes, y SOLO sobre sus propios datos. El propietario se determina por la
  sesión, nunca por datos enviados por el cliente: 401 sin autenticación; 403 con otro rol o cuenta
  inhabilitada; recurso ajeno se responde como no encontrado, sin revelar su existencia.
- **FR-013**: La interfaz DEBE ofrecer al cliente el carrito, su libreta de direcciones y el resumen
  de importes desde su sesión, sin presentar como implementados la confirmación del pedido, el pago
  ni el historial.

### Key Entities *(include if feature involves data)*

- **Carrito**: pertenece a un único cliente y, mientras tenga líneas, a un único restaurante; guarda
  la dirección seleccionada y sus fechas de creación y modificación.
- **Línea de carrito**: producto, cantidad y referencia al carrito; el precio se toma del producto
  vigente al consultar.
- **Dirección de entrega**: pertenece a un cliente; datos de ubicación y referencia, y su
  pertenencia a la zona de reparto cubierta; puede estar seleccionada para el pedido.
- **Zona de reparto**: lista de distritos cubiertos por el servicio.
- **Importes del pedido**: subtotal, costo de envío, comisión y monto total derivados del carrito y
  la dirección; no se guardan como pedido en este incremento.
- **Producto, Restaurante y Cuenta** (specs 004, 003 y 002): aportan disponibilidad, habilitación y
  rol; no cambian en este incremento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cliente arma un carrito con tres productos y selecciona una dirección en menos de
  3 minutos, y carrito y direcciones se conservan tras reiniciar la aplicación.
- **SC-002**: En el 100 % de los casos de prueba, el carrito nunca contiene productos de más de un
  restaurante ni productos no disponibles, retirados o de restaurantes no elegibles al agregarlos.
- **SC-003**: En el 100 % de los casos de prueba, una dirección fuera de la zona cubierta se rechaza
  sin guardarse.
- **SC-004**: En el 100 % de los casos de prueba, subtotal, envío, comisión y total coinciden con el
  cálculo esperado y se actualizan tras cada cambio del carrito o de la dirección.
- **SC-005**: El 100 % de los accesos sin sesión, con otro rol, con cuenta inhabilitada o sobre el
  carrito o las direcciones de otro cliente se rechaza sin revelar datos ni modificar estado.
- **SC-006**: Las operaciones de carrito, direcciones e importes muestran su resultado en menos de
  2 segundos con los datos de prueba locales (la validación con 1 000 usuarios concurrentes de
  RNF-01 queda aplazada).

## Assumptions

- **Moneda**: soles peruanos, con dos decimales; sin impuestos, descuentos, propinas ni cupones.
- **Variantes**: los productos no tienen opciones ni notas especiales; el carrito guarda solo
  producto y cantidad.
- **Un carrito por cliente**: cada cliente tiene un único carrito activo; no hay carritos guardados
  ni compartidos.
- **Precio vigente**: el carrito no congela precios; el precio se fija al confirmar el pedido (spec
  006). Aquí se muestra siempre el vigente.
- **Persistencia**: el carrito y las direcciones se guardan en el servidor, ligados a la cuenta.
- **Dirección**: texto estructurado (calle y número, distrito o zona, referencia y teléfono de
  contacto opcional); sin mapas ni geolocalización, porque MapTiler sigue aplazado por la
  constitución.
- **Límites**: cantidad máxima por producto y de líneas por carrito razonables (por ejemplo, 20 y
  30), ajustables en el plan.
- **Reglas de negocio** (decisión del usuario, 2026-11-17): zona de reparto = lista de distritos;
  envío = tarifa fija; comisión = porcentaje del subtotal como cargo al cliente. La lista de
  distritos, la tarifa y el porcentaje son valores de configuración que el usuario debe indicar
  antes de implementar; las pruebas usan valores de prueba. No hay valores por defecto en la
  constitución.
- **Restaurante abierto al agregar** (U1): solo se puede agregar un producto cuando el restaurante
  puede recibir pedidos en ese momento (abierto, disponible y habilitado). Un carrito ya armado se
  conserva si el restaurante deja de atender; sus líneas se marcan como no disponibles.
- **Límite de direcciones**: cada cliente puede registrar hasta 10 direcciones.
- **Dirección y carrito vacío**: vaciar el carrito conserva la dirección seleccionada; solo
  eliminar esa dirección o dejar de estar en zona la quita.
- **Fuera del incremento**: confirmación del pedido y pago (spec 006), historial de pedidos (spec
  010), asignación de repartidor, y caché y optimización de carga. Impedir pedir a un restaurante
  que no atiende corresponde a la confirmación; aquí solo se impide agregar y se avisa.
- **Dependencias y restricciones heredadas**: specs 002, 003 y 004 y constitución 3.0.0 (local, HTTP,
  PostgreSQL, RNF-07 aplicado a estas operaciones, RNF-11 con al menos 90 % de líneas de lógica de
  negocio, sin E2E automatizadas).
- **Trazabilidad**: RF-04, RF-11, RF-12 y RF-13 de la [propuesta](../../Propuesta_de_Proyecto.docx)
  y la [constitución](../../.specify/memory/constitution.md); solicitud del usuario del 2026-11-17.
