# Feature Specification: Asistente de IA en chat flotante

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-24

**Status**: Draft

**Input**: Incremento 013 del usuario. Requisito del docente del curso: la plataforma debe integrar IA,
como mínimo con un chat al costado. Un asistente en un chat flotante (botón abajo a la derecha que abre
un panel, estilo blanco y naranja del logo) que responde dudas sobre Mikuna y, con datos reales de
restaurantes, platos y categorías, recomienda platos y dice qué restaurantes están abiertos (por ejemplo
"quiero algo vegetariano bajo 20 soles"). Usa Gemini en su plan gratuito, desde el backend y detrás de
un punto de sustitución del proveedor. Depende de 004 (catálogo de consulta) y 002 (acceso). Fuera de
alcance: hacer pedidos o modificar el carrito desde el chat, consultar pedidos o datos personales, voz,
imágenes y archivos adjuntos.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Preguntar al asistente desde cualquier pantalla (Priority: P1)

Como cliente con sesión iniciada, quiero abrir un chat flotante y hacerle preguntas sobre Mikuna (cómo
pedir, cómo pagar, cómo seguir un pedido), para resolver dudas sin salir de la pantalla en la que estoy.

**Why this priority**: Es el mínimo que exige el docente: un chat de IA integrado. Aunque nada más
funcione, este incremento ya cumple el requisito y se puede demostrar.

**Independent Test**: Iniciar sesión como cliente, abrir el chat, preguntar "¿cómo hago un pedido?" y
comprobar que llega una respuesta coherente con el funcionamiento real de Mikuna.

**Acceptance Scenarios**:

1. **Given** un cliente con sesión iniciada en cualquier pantalla, **When** pulsa el botón flotante,
   **Then** se abre un panel de chat con un saludo inicial y una caja de texto, sin perder la pantalla
   que estaba viendo.
2. **Given** el panel abierto, **When** el cliente escribe una pregunta sobre cómo usar Mikuna y la envía,
   **Then** el asistente responde en español, de forma coherente con el funcionamiento de la plataforma.
3. **Given** una conversación en curso, **When** el cliente cierra el panel y lo vuelve a abrir sin
   cambiar de sesión, **Then** ve la conversación anterior.
4. **Given** una pregunta ajena a Mikuna y a la comida (por ejemplo, una tarea escolar), **When** se
   envía, **Then** el asistente declina con amabilidad y orienta hacia lo que sí puede ayudar.
5. **Given** un visitante sin sesión, **When** recorre la plataforma, **Then** no ve el botón del chat.

---

### User Story 2 - Recomendaciones con datos reales del catálogo (Priority: P1)

Como cliente, quiero pedirle al asistente algo como "quiero algo vegetariano bajo 20 soles" y recibir
platos que realmente existen, con su precio y restaurante, para decidir qué pedir sin recorrer todo el
catálogo.

**Why this priority**: Es el valor diferencial frente a un chat genérico y lo que más luce en la
demostración. Depende de que el asistente use datos reales y no invente platos.

**Independent Test**: Con platos conocidos en el catálogo, pedir una recomendación con presupuesto y
comprobar que cada plato citado existe, está disponible y cumple el precio indicado.

**Acceptance Scenarios**:

1. **Given** platos disponibles de restaurantes aprobados, **When** el cliente pide una recomendación con
   un tope de precio, **Then** el asistente sugiere solo platos reales, disponibles y dentro del tope,
   indicando nombre, precio y restaurante.
2. **Given** que ningún plato cumple lo pedido, **When** el cliente lo solicita, **Then** el asistente lo
   dice con claridad y sugiere la alternativa más cercana real, sin inventar platos.
3. **Given** un plato retirado, no disponible o de un restaurante no aprobado, **When** se piden
   recomendaciones, **Then** ese plato nunca aparece en la respuesta.
4. **Given** una petición por categoría (por ejemplo "postres" o "pollo a la brasa"), **When** el cliente
   la envía, **Then** el asistente responde con platos de las categorías existentes.
5. **Given** una petición "vegetariano" y que el catálogo no tiene un dato explícito de ello, **When** el
   asistente responde, **Then** propone solo platos cuyo nombre, descripción o categoría lo sustenten y
   aclara que debe confirmarse con el restaurante.

---

### User Story 3 - Saber qué restaurantes están abiertos (Priority: P2)

Como cliente, quiero preguntar qué restaurantes están abiertos ahora (o abiertos y con cierto tipo de
comida), para no elegir uno que no puede recibir mi pedido.

**Why this priority**: Complementa las recomendaciones y evita frustraciones, pero el chat ya aporta
valor sin ella.

**Independent Test**: Con un restaurante abierto y otro cerrado por horario, preguntar "¿qué está abierto
ahora?" y comprobar que solo aparece el abierto.

**Acceptance Scenarios**:

1. **Given** restaurantes con distinto estado de atención y horario, **When** el cliente pregunta qué hay
   abierto, **Then** el asistente lista solo los que pueden recibir pedidos en ese momento.
2. **Given** que ninguno está abierto, **When** lo pregunta, **Then** el asistente lo indica y, si
   corresponde, menciona cuándo abre el siguiente.
3. **Given** el listado del asistente, **When** el cliente lo contrasta con el catálogo público, **Then**
   coinciden.

---

### User Story 4 - El asistente se degrada con elegancia y no se abusa de él (Priority: P2)

Como cliente, quiero recibir un aviso claro si el asistente no puede responder en ese momento, y como
responsable del proyecto necesito que nadie agote la cuota gratuita del proveedor de IA.

**Why this priority**: El plan gratuito tiene topes. Sin esta protección el chat falla en plena
demostración y sin explicación.

**Independent Test**: Enviar más mensajes por minuto que el límite permitido y simular que el proveedor
rechaza por exceso de uso; en ambos casos se muestra un mensaje amable y el resto de la plataforma sigue
funcionando.

**Acceptance Scenarios**:

1. **Given** un cliente que supera el límite de mensajes por minuto, **When** envía otro, **Then** recibe
   un aviso de espera con el tiempo aproximado y el mensaje no llega al proveedor.
2. **Given** que el proveedor informa que su cuota gratuita se agotó, **When** el cliente envía un
   mensaje, **Then** ve "el asistente está ocupado, intenta en un minuto" sin errores técnicos.
3. **Given** que el proveedor no responde o falla, **When** el cliente envía un mensaje, **Then** ve un
   mensaje de indisponibilidad y puede reintentar; el resto de Mikuna no se ve afectado.
4. **Given** una respuesta larga, **When** se genera, **Then** el cliente ve el texto aparecer
   progresivamente en lugar de esperar a que esté completo.

---

### Edge Cases

- Mensaje vacío, solo espacios o excesivamente largo: se rechaza con un aviso, sin llegar al proveedor.
- Intento de que el asistente ignore sus reglas o revele sus instrucciones internas: no lo hace y
  redirige a su función.
- Intento de que el asistente consulte pedidos, direcciones, cuentas u otros datos privados: declina; no
  tiene acceso a ellos.
- El restaurante cambia el precio o la disponibilidad de un plato durante la conversación: cada respuesta
  usa el dato actual, no el de mensajes anteriores.
- La sesión expira con el panel abierto: se pide iniciar sesión de nuevo sin mostrar datos de otra sesión.
- Pantallas pequeñas: el panel no tapa los controles esenciales y puede cerrarse siempre.
- Conversación muy larga: se conserva lo reciente para no superar los límites del proveedor.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE mostrar un botón flotante de chat, abajo a la derecha, a los clientes con
  sesión iniciada en las pantallas de cliente, y NO DEBE mostrarlo a visitantes sin sesión ni en las
  pantallas de restaurante, repartidor o administración.
- **FR-002**: El botón DEBE abrir y cerrar un panel de chat con saludo inicial, lista de mensajes, caja de
  texto y botón de enviar, con el estilo visual de Mikuna (blanco predominante y naranja del logo).
- **FR-003**: El asistente DEBE responder en español, sobre el funcionamiento de Mikuna y sobre la oferta
  de comida de la plataforma, y DEBE declinar con amabilidad lo ajeno a ese ámbito.
- **FR-004**: El asistente DEBE recomendar platos usando únicamente el catálogo vigente: platos
  disponibles, activos y de restaurantes aprobados, con nombre, precio y restaurante. NO DEBE inventar
  platos, precios ni restaurantes.
- **FR-005**: El asistente DEBE respetar los filtros que exprese el cliente: tope de precio, categoría,
  restaurante y preferencias alimentarias deducibles del nombre, la descripción o la categoría.
- **FR-006**: Cuando el catálogo no permita confirmar una preferencia (por ejemplo vegetariano), el
  asistente DEBE indicarlo y recomendar confirmar con el restaurante.
- **FR-007**: El asistente DEBE informar qué restaurantes pueden recibir pedidos en el momento de la
  consulta, con el mismo criterio que usa la plataforma para aceptar pedidos.
- **FR-008**: El asistente SOLO DEBE tener acceso a datos públicos de restaurantes, platos y categorías.
  NO DEBE recibir ni revelar datos personales, direcciones, pedidos, pagos ni credenciales de ningún usuario.
- **FR-009**: El asistente NO DEBE realizar acciones: no crea pedidos, no modifica el carrito ni cambia
  datos. Solo informa y recomienda.
- **FR-010**: Las respuestas DEBEN mostrarse de forma progresiva a medida que se generan.
- **FR-011**: El sistema DEBE limitar los mensajes por cliente (por defecto 10 por minuto, configurable)
  y avisar con claridad cuándo puede volver a escribir.
- **FR-012**: El sistema DEBE rechazar mensajes vacíos o que superen una longitud máxima (por defecto 500
  caracteres, configurable), sin consultar al proveedor.
- **FR-013**: Ante exceso de cuota, fallo o falta de respuesta del proveedor, el sistema DEBE mostrar un
  mensaje amable y permitir reintentar, sin exponer errores técnicos, claves ni detalles internos.
- **FR-014**: El acceso al chat DEBE exigir sesión válida de cliente; las peticiones sin sesión o con
  otro rol DEBEN rechazarse conforme a la política de acceso de la plataforma.
- **FR-015**: La clave de acceso al proveedor de IA DEBE residir solo en la configuración del servidor, sin
  incluirse en commits, registros, respuestas ni en el navegador.
- **FR-016**: El proveedor de IA DEBE quedar aislado de las reglas del chat, de modo que cambiarlo no exija
  modificar la interfaz ni las consultas al catálogo.
- **FR-017**: El historial de la conversación DEBE mantenerse mientras dure la sesión del cliente y NO
  DEBE persistirse de forma permanente ni compartirse entre clientes.
- **FR-018**: Cada respuesta DEBE basarse en el estado actual del catálogo al momento de la pregunta.

### Key Entities *(include if feature involves data)*

- **Conversación**: Intercambio temporal de un cliente con el asistente, formado por mensajes ordenados.
  Vive mientras dure la sesión y no se guarda de forma permanente.
- **Mensaje**: Texto del cliente o del asistente, con su autor y momento. Acotado en longitud.
- **Plato recomendable** (vista del catálogo): Plato disponible y activo de un restaurante aprobado, con
  nombre, descripción, categoría, precio y restaurante. Solo lectura.
- **Restaurante abierto** (vista del catálogo): Restaurante aprobado que puede recibir pedidos en el
  momento de la consulta, con su horario. Solo lectura.
- **Contador de uso por cliente**: Cuenta temporal de mensajes del último minuto, para aplicar el límite.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cliente abre el chat y obtiene la primera respuesta a una duda sobre Mikuna en menos de
  10 segundos en condiciones normales.
- **SC-002**: En una prueba con al menos 10 peticiones de recomendación con presupuesto o categoría, el
  100 % de los platos citados existe, está disponible y cumple el filtro pedido.
- **SC-003**: En una prueba con restaurantes abiertos y cerrados, el 100 % de los restaurantes que el
  asistente indica como abiertos coincide con los que la plataforma acepta para pedidos.
- **SC-004**: En todas las pruebas de privacidad (pedidos, direcciones, cuentas de otros), el asistente no
  revela ningún dato personal.
- **SC-005**: Al enviar 11 mensajes en un minuto, el undécimo se rechaza con un aviso de espera y los 10
  anteriores se procesan.
- **SC-006**: Ante exceso de cuota o indisponibilidad del proveedor, el 100 % de los casos muestra un
  mensaje amable (sin trazas ni errores técnicos) y el resto de Mikuna sigue operando.
- **SC-007**: El cliente empieza a ver texto de la respuesta en menos de 3 segundos desde el envío en
  condiciones normales.
- **SC-008**: El chat es utilizable en pantalla de escritorio y de móvil, y se puede cerrar siempre.

## Assumptions

- Gobernanza: la constitución solo permite Cloudinary, correo y Stripe en modo de pruebas como
  integraciones externas. Usar el proveedor de IA exige una enmienda MINOR de la constitución antes del
  plan (decisión del usuario del 2026-11-24: Gemini en plan gratuito). Esa enmienda debe fijar que la
  clave reside solo en la configuración del servidor y que las pruebas unitarias no llaman al proveedor real.
- Requisito derivado de una instrucción del docente, no atribuible a un RF/RNF de la propuesta; no se le
  asigna un código.
- El catálogo no tiene un dato explícito de "vegetariano" (los platos tienen nombre, descripción,
  categoría y precio). Se infiere del texto y se advierte al cliente; añadir una etiqueta queda fuera de
  alcance y puede ser un incremento posterior.
- El chat es solo para clientes con sesión; los visitantes lo usarán al iniciar sesión. Los datos del
  catálogo son públicos, pero el límite de uso se aplica por cliente identificado.
- Los límites de 10 mensajes por minuto y 500 caracteres por mensaje son valores por defecto
  configurables, no reglas de negocio fijas.
- El plan gratuito del proveedor tiene topes de uso que pueden cambiar; el límite por cliente es una
  protección, no una garantía de disponibilidad.
- Lo enviado al proveedor en el plan gratuito puede usarse para mejorarlo; por eso solo se envían el texto
  del cliente y datos públicos del catálogo.
- El historial vive en el navegador durante la sesión; no hay tabla ni persistencia permanente.
- Cobertura de pruebas: las reglas de negocio (filtrado del catálogo, límite de uso, manejo de errores)
  se prueban con dobles del proveedor, sin llamadas reales (constitución V).
