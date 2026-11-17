# Feature Specification: Catálogo y consulta de restaurantes y productos

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-17

**Status**: Draft

**Input**: Incremento 004 del usuario: RF-08, RF-09 y RF-10, con la autorización de RF-04 aplicada
a estas operaciones. Depende de la spec 002 (cuentas, autenticación y rol) y de la spec 003
(restaurantes aprobados, con sus datos, horarios y disponibilidad). Fuera de alcance: carrito y
direcciones de entrega (spec 005), pedidos, pagos y la optimización con caché y réplica de lectura.

## Clarifications

### Session 2026-11-17

- Q: ¿Un restaurante aprobado pero pausado o fuera de horario se oculta o se muestra? → A: Se muestra
  en la búsqueda y su catálogo se puede ver, con el aviso «Fuera de horario» o «Sin atención por
  ahora». Si no está aprobado (pendiente, rechazado) o su cuenta está inhabilitada, se responde «no
  encontrado» (decisión del usuario).
- Q: ¿La búsqueda y el catálogo requieren sesión de cliente? → A: No; son públicos, cualquier
  persona puede consultarlos sin sesión. La gestión del catálogo sigue exigiendo un restaurante
  aprobado con sesión.
- Q: ¿La imagen es obligatoria al registrar un producto? → A: Sí; no se guarda un producto sin
  imagen válida (decisión del usuario).
- Q: ¿Qué pasa con la segunda de dos ediciones simultáneas del mismo producto? → A: Se rechaza si el
  producto cambió desde que se abrió, con un mensaje para recargar y reintentar (decisión del
  usuario).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrar el catálogo de productos (Priority: P1)

Como restaurante aprobado, quiero registrar, editar y retirar productos de mi catálogo con
categoría, precio, imagen y disponibilidad para ofrecer a los clientes lo que vendo.

**Why this priority**: Sin productos no hay nada que consultar ni, más adelante, que pedir (RF-08).

**Independent Test**: Con un restaurante aprobado, crear un producto, editarlo, marcarlo como no
disponible y retirarlo; comprobar, tras reiniciar la aplicación, el estado persistido de cada paso.

**Acceptance Scenarios**:

1. **Given** un restaurante aprobado con cuenta habilitada, **When** registra un producto con
   nombre, categoría, precio válido e imagen, **Then** el producto se guarda en su catálogo,
   asociado a ese restaurante, y queda disponible.
2. **Given** datos obligatorios vacíos, demasiado largos, un precio no positivo o con más de dos
   decimales, o una imagen inválida, **When** intenta guardar, **Then** se explica cada error y no
   se guarda ni modifica ningún producto.
3. **Given** un producto propio, **When** el restaurante edita sus datos válidos, **Then** los
   cambios se guardan y se reflejan en su catálogo; si no cambia la imagen, conserva la anterior,
   y no puede quedar sin imagen.
   - Si el producto cambió desde que se abrió la edición, se rechaza con un mensaje para recargar
     y reintentar, sin sobrescribir el cambio ajeno.
4. **Given** un producto propio, **When** lo marca como no disponible y luego como disponible,
   **Then** cada cambio se guarda sin perder el producto ni sus datos.
5. **Given** un producto propio, **When** lo retira, **Then** deja de aparecer en su catálogo activo,
   no puede volver a editarse ni a consultarse por los clientes, y su retiro persiste.
6. **Given** la sesión de un restaurante, **When** intenta consultar, editar o retirar un producto
   de otro restaurante, **Then** se deniega sin revelar sus datos ni modificar nada.
7. **Given** un restaurante pendiente o rechazado, una cuenta de cliente, repartidor o
   administrador, o una persona sin sesión, **When** intenta gestionar productos, **Then** se
   deniega (403, o 401 sin sesión) sin cambios.

### User Story 2 - Buscar restaurantes (Priority: P1)

Como cliente, quiero buscar restaurantes por nombre, categoría y ubicación para encontrar dónde
pedir.

**Why this priority**: Es la entrada del cliente al catálogo (RF-09).

**Independent Test**: Con restaurantes en distintos estados, buscar por cada criterio y combinados;
comprobar que solo aparecen los aprobados con cuenta habilitada y que cada uno indica si atiende
ahora.

**Acceptance Scenarios**:

1. **Given** restaurantes de distintos estados, **When** el cliente abre la búsqueda sin criterios,
   **Then** ve únicamente los restaurantes aprobados con cuenta habilitada, con su nombre,
   categoría, zona y su situación de atención (**Abierto**, **Fuera de horario** o **Sin atención
   por ahora**); los que atienden ahora aparecen primero.
2. **Given** un texto de nombre, **When** busca, **Then** ve los restaurantes cuyo nombre lo contiene,
   sin distinguir mayúsculas, minúsculas ni tildes.
3. **Given** una categoría o una zona, **When** filtra, **Then** ve solo los restaurantes de esa
   categoría o ubicación; combinar criterios restringe el resultado.
4. **Given** criterios sin coincidencias, **When** busca, **Then** se informa claramente que no hay
   resultados y se puede limpiar la búsqueda.
5. **Given** un restaurante pendiente, rechazado o con cuenta inhabilitada, **When** cualquier
   cliente busca, **Then** no aparece en los resultados.
6. **Given** un restaurante aprobado que está pausado o fuera de su horario, **When** el cliente
   busca, **Then** aparece marcado como «Sin atención por ahora» o «Fuera de horario» según el caso.
7. **Given** una persona sin sesión o con cualquier rol, **When** busca restaurantes, **Then**
   obtiene los mismos resultados públicos, sin datos de cuentas ni del titular.

### User Story 3 - Consultar el catálogo de un restaurante (Priority: P1)

Como cliente, quiero ver los productos disponibles de un restaurante con su precio, categoría e
imagen para decidir qué me interesa.

**Why this priority**: Completa RF-10 y deja listo lo que usará el futuro carrito.

**Independent Test**: Con un restaurante que tiene productos disponibles, no disponibles y
retirados, consultar su catálogo como cliente y comprobar que solo se ven los disponibles.

**Acceptance Scenarios**:

1. **Given** un restaurante aprobado con cuenta habilitada, **When** el cliente abre su catálogo,
   **Then** ve sus productos disponibles con nombre, precio, categoría e imagen, agrupados por
   categoría.
2. **Given** un producto no disponible o retirado, **When** el cliente consulta el catálogo,
   **Then** el producto no aparece, ni en la lista ni por su identificador.
3. **Given** un restaurante sin productos disponibles, **When** el cliente abre su catálogo,
   **Then** ve un aviso de catálogo vacío.
4. **Given** un restaurante pendiente, rechazado, con cuenta inhabilitada o inexistente, **When**
   el cliente intenta abrir su catálogo, **Then** recibe un resultado de no encontrado, sin
   revelar si el restaurante existe.
5. **Given** un restaurante aprobado que está pausado o fuera de horario, **When** el cliente abre
   su catálogo, **Then** puede ver sus productos disponibles junto al aviso «Fuera de horario» o
   «Sin atención por ahora», que indica que no recibe pedidos en este momento.
6. **Given** un restaurante que cambia de situación de atención mientras el cliente ve su catálogo,
   **When** el cliente actualiza la vista, **Then** se muestra la situación vigente.
7. **Given** una persona sin sesión o con cualquier rol, **When** consulta un catálogo visible,
   **Then** ve el mismo contenido público, sin datos de cuentas ni del titular.

### Edge Cases

- Un restaurante aprobado que se pausa o sale de su horario conserva su catálogo y sigue visible,
  con el aviso de que no atiende por ahora. Si su cuenta se inhabilita, deja de ser visible para
  los clientes hasta que se habilite de nuevo.
- Dos ediciones simultáneas del mismo producto (incluidos cambios de disponibilidad o retiro): no
  se pierde silenciosamente un cambio; se rechaza la edición hecha sobre una versión desactualizada
  con un mensaje para recargar el producto y reintentar.
- Producto retirado y luego intento de editarlo o disponibilizarlo: se rechaza como no encontrado.
- Nombre de producto repetido dentro de un mismo restaurante: se admite en categorías distintas; el
  duplicado exacto (mismo nombre y categoría entre productos no retirados) se rechaza con un mensaje.
- Archivo de imagen de formato no admitido, demasiado grande o corrupto: se rechaza sin guardar el
  producto; si falla el servicio de imágenes, no se confirma el cambio y se puede reintentar.
- Precio con separador distinto, texto o valor extremo: se rechaza con un mensaje, sin guardar.
- Búsqueda con texto muy largo o caracteres especiales: se trata como texto literal, sin error.
- Fallo al guardar o al subir una imagen: no se confirma un cambio que no se persistió; se puede
  reintentar.
- Restaurante con muchos productos o resultados de búsqueda numerosos: se muestran por páginas.

## Requirements *(mandatory)*

### Functional Requirements

Los códigos RF originales se conservan; las referencias FR son internas de esta especificación.

- **FR-001 (RF-08)**: Un restaurante aprobado con cuenta habilitada DEBE poder registrar un producto
  en su catálogo con nombre, categoría, precio positivo e imagen, todos obligatorios; un producto
  nuevo queda disponible. No se guarda un producto sin imagen válida.
- **FR-002 (RF-08)**: El sistema DEBE validar los datos obligatorios, sus longitudes, el precio
  (mayor que cero, hasta dos decimales y un máximo razonable) y la imagen (archivo de imagen en un
  formato admitido y dentro del tamaño máximo) también al procesarlos.
- **FR-003 (RF-08)**: El restaurante DEBE poder listar, consultar y editar los datos de sus
  productos, incluida su categoría, precio e imagen.
- **FR-004 (RF-08)**: El restaurante DEBE poder marcar un producto como disponible o no disponible
  sin perderlo.
- **FR-005 (RF-08)**: El restaurante DEBE poder retirar un producto; el retiro es lógico para
  conservar la referencia histórica futura de pedidos, y un producto retirado no se vuelve a
  mostrar ni a editar.
- **FR-006 (RF-04/RF-08)**: Un restaurante SOLO DEBE gestionar productos de su propio restaurante.
  El restaurante propietario se determina por la sesión, nunca por datos enviados por el cliente.
- **FR-007 (RF-09)**: Un cliente DEBE poder buscar restaurantes por nombre, por categoría y por
  ubicación (distrito o zona), de forma individual o combinada, con coincidencia parcial de nombre
  sin distinguir mayúsculas ni tildes.
- **FR-008 (RF-09)**: Los resultados de búsqueda DEBEN incluir únicamente restaurantes **visibles**:
  aprobados y con cuenta habilitada (spec 002 y 003). Cada resultado muestra nombre, categoría,
  zona, descripción breve y su situación de atención: **Abierto** cuando está **habilitado para
  recibir pedidos** según la spec 003 (disponible y dentro de horario), **Fuera de horario** o
  **Sin atención por ahora** (pausado) en los demás casos. Los que atienden ahora se listan primero.
- **FR-009 (RF-09)**: La búsqueda DEBE ofrecer las categorías y zonas existentes entre los
  restaurantes habilitados como opciones de filtro, y paginar los resultados.
- **FR-010 (RF-10)**: Un cliente DEBE poder consultar el catálogo de un restaurante visible y ver
  únicamente productos disponibles, con nombre, precio, categoría e imagen, junto con la situación
  de atención del restaurante (FR-008).
- **FR-011 (RF-10)**: Un producto retirado o no disponible NO DEBE mostrarse al cliente por ningún
  medio, incluidos su consulta directa y los resultados de búsqueda.
- **FR-012 (RF-04)**: La búsqueda de restaurantes y la consulta de catálogos (FR-007 a FR-011) son
  **públicas**: no exigen sesión y solo exponen datos comerciales públicos (nunca datos de cuenta
  ni del titular). Las operaciones de gestión del catálogo (FR-001 a FR-006) DEBEN autorizarse con
  información confiable de identidad, rol, estado de cuenta, estado del restaurante y pertenencia:
  401 sin autenticación válida; 403 para otro rol, cuenta inhabilitada, restaurante no aprobado o
  recurso ajeno. Las consultas públicas de un restaurante o producto no visible responden como no
  encontrado. El sistema NO DEBE aceptar identidad del restaurante, estado o retiro enviados por
  el cliente.
- **FR-013**: La interfaz DEBE ofrecer al restaurante la gestión de su catálogo desde su sesión, y a
  cualquier visitante la búsqueda y el catálogo desde el inicio, sin presentar como implementados
  el carrito, los pedidos ni los pagos.

### Key Entities *(include if feature involves data)*

- **Producto**: perteneciente a un único restaurante; nombre, descripción breve opcional, categoría,
  precio, imagen, disponibilidad, estado de retiro y fechas de creación y modificación.
- **Categoría de producto**: agrupación definida por el restaurante dentro de su catálogo (por
  ejemplo, entradas o bebidas); texto simple, sin catálogo global.
- **Restaurante** (spec 003): aporta nombre, categoría, zona, estado de aprobación, disponibilidad,
  horario y la condición de habilitado para recibir pedidos; no cambia en este incremento.
- **Cuenta** (spec 002): aporta identidad, rol y estado de acceso.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un restaurante registra un producto completo en menos de 2 minutos, y el producto y
  sus cambios se conservan tras reiniciar la aplicación.
- **SC-002**: Un cliente encuentra un restaurante por nombre, categoría o zona y abre su catálogo en
  menos de 1 minuto.
- **SC-003**: En el 100 % de los casos de prueba, un restaurante pendiente, rechazado o con cuenta
  inhabilitada no aparece en la búsqueda ni permite consultar su catálogo, y uno aprobado pausado o
  fuera de horario aparece con el aviso de atención correcto.
- **SC-004**: En el 100 % de los casos de prueba, un producto retirado o no disponible no aparece en
  el catálogo que ve el cliente.
- **SC-005**: El 100 % de los accesos de gestión del catálogo sin sesión, con otro rol, con cuenta
  inhabilitada o sobre un producto ajeno se rechaza sin revelar datos ni modificar estado, y las
  consultas públicas nunca exponen datos de cuentas ni de restaurantes no visibles.
- **SC-006**: Las consultas de búsqueda y de catálogo muestran su resultado en menos de 2 segundos
  con los datos de prueba locales (la validación con 1 000 usuarios concurrentes de RNF-01 queda
  aplazada).

## Assumptions

- **Imagen del producto**: el restaurante sube la imagen como archivo (decisión del usuario). Se
  almacena con Cloudinary, integración que la constitución permite cuando es necesaria; sus
  credenciales se configuran en `application.properties` sin incluirse en commits. Se admiten solo
  formatos de imagen comunes (JPG, PNG, WebP) con un tamaño máximo; MapTiler y demás integraciones
  siguen aplazadas.
- **Ubicación**: en RF-09 la "ubicación" es el distrito o zona de la spec 003; no hay mapas, cercanía
  geográfica ni cobertura de reparto (MapTiler y la política de zona urbana siguen aplazados).
- **Categorías**: las categorías de producto son texto libre por restaurante; las de restaurante son
  las de la spec 003.
- **Moneda**: precios en soles peruanos, con dos decimales; sin impuestos, descuentos ni variantes
  u opciones de producto.
- **Visibilidad para el cliente**: la búsqueda y el catálogo muestran restaurantes aprobados con
  cuenta habilitada, atiendan o no en ese momento (FR-008). Este incremento solo informa la
  situación de atención; impedir pedidos a quien no atiende corresponde al incremento de pedidos.
- **Acceso público**: la búsqueda y el catálogo se pueden consultar sin sesión (decisión del
  usuario). Esto amplía las rutas públicas de la spec 002 solo con estas consultas de lectura; el
  inicio se conecta a la búsqueda. Pedir seguirá exigiendo sesión en la spec 005.
- **Sin stock**: la disponibilidad es un indicador manual, no un inventario por cantidades.
- **Fuera del incremento**: carrito y direcciones (spec 005), pedidos, pagos, entregas, calificaciones,
  caché y réplica de lectura (spec de carga y escalamiento), notificaciones y gestión administrativa
  de catálogos (RF-32). RF-04 sigue parcial.
- **Dependencias y restricciones heredadas**: specs 002 y 003 y constitución 3.0.0 (local, HTTP,
  PostgreSQL con base creada por el usuario, `ddl-auto=update`, RNF-07 aplicado a estas operaciones,
  RNF-11 con al menos 90 % de líneas de lógica de negocio, sin E2E automatizadas ni Playwright).
- **Trazabilidad**: RF-04, RF-08, RF-09 y RF-10 de la [propuesta](../../Propuesta_de_Proyecto.docx)
  y la [constitución](../../.specify/memory/constitution.md); solicitud del usuario del 2026-11-17.
