# Feature Specification: Rendimiento, caché y pruebas de carga

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-26

**Status**: Draft

**Input**: Incremento 015 del usuario. Abordar el rendimiento y la escalabilidad de Mikuna (criterios 8, 9,
11 y 13 de la evaluación del curso): servir las consultas públicas de restaurantes, filtros y catálogos con
respuesta rápida aunque haya muchos usuarios a la vez; que cualquier cambio del restaurante o del
administrador se vea de inmediato; que la plataforma pueda correr con varias instancias sin perder
coherencia; revisar consultas, índices y conexiones de la base de datos; y medir todo con pruebas de carga
reproducibles, antes y después de la caché. Retoma lo que la spec 004 dejó para "la spec de carga"
(filtrado en memoria del catálogo público) y los objetivos RNF-01, RNF-02, RNF-03 y RNF-04. Contexto de
despliegue previsto: AWS en capa gratuita, con el backend y el almacén de caché en la misma instancia de
1 GB, la base de datos en el servicio gestionado gratuito y el frontend en almacenamiento estático con CDN.
Depende de las specs 004 (catálogo público), 002 (freno del inicio de sesión), 013 (límite del asistente)
y 012 (vencimiento de pedidos). Requiere la enmienda 6.0.0 de la constitución, aprobada por el usuario el
2026-11-26. En este incremento NO se crea configuración de AWS ni de HTTPS/TLS: el despliegue es solo
contexto para dimensionar.

## Clarifications

### Session 2026-11-26

- Q: ¿Cuántos usuarios concurrentes como objetivo medido, si RNF-01 pide 1 000 y la instancia gratuita de
  1 GB probablemente no los alcanza? → A: 200 usuarios concurrentes como objetivo; la prueba de estrés busca
  el límite real y RNF-01 se registra como parcial con ese dato.
- Q: Si el almacén compartido cae, ¿el freno de fuerza bruta cuenta en la memoria de cada instancia o se
  rechaza el inicio de sesión? → A: Vuelve a contar en la memoria de cada instancia; el inicio de sesión
  sigue funcionando, menos estricto, hasta que el almacén vuelva.
- Q: ¿La prueba de varias instancias se hace solo en local o también en AWS? → A: Solo en local, con dos
  instancias que comparten base de datos y almacén.
- Restricción del usuario: no configurar HTTPS, SSL, TLS, certificados ni llaves privadas en el equipo
  local, ni archivos o carpetas de infraestructura de AWS. La aplicación DEBE seguir arrancando en local sin
  el almacén de caché ni contenedores.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El visitante navega el catálogo rápido aunque haya muchos usuarios (Priority: P1)

Como visitante o cliente, quiero que la lista de restaurantes, los filtros y el catálogo de cada restaurante
carguen rápido incluso en horas de mucho tráfico, para elegir mi pedido sin esperas.

**Why this priority**: Es la consulta más frecuente de la plataforma y la que todos los usuarios hacen antes
de pedir. Hoy cada búsqueda lee todos los restaurantes visibles de la base de datos y los filtra, así que
el costo crece con el tráfico.

**Independent Test**: Con datos de volumen cargados, se ejecuta la prueba de carga sobre las consultas
públicas sin caché y luego con caché; se comparan tiempos y errores, y se comprueba que las respuestas son
idénticas a las de la base de datos.

**Acceptance Scenarios**:

1. **Given** varios visitantes consultando la misma lista, filtros o catálogo, **When** repiten la consulta
   sin que nada haya cambiado, **Then** la plataforma responde sin volver a leer esos datos de la base de
   datos y con el mismo contenido.
2. **Given** un restaurante con horario de 09:00 a 18:00 cuyos datos ya se sirvieron antes de las 18:00,
   **When** alguien consulta a las 18:01, **Then** lo ve "fuera de horario", aunque sus datos sigan
   guardados.
3. **Given** un restaurante pausado por su titular, **When** cualquier visitante lo consulta, **Then** lo ve
   pausado en ese mismo momento.

---

### User Story 2 - Los cambios del restaurante y del administrador se ven de inmediato (Priority: P1)

Como restaurante o administrador, quiero que lo que cambio se vea enseguida en la parte pública, para que
ningún cliente pida un plato retirado, vea un precio viejo o encuentre un restaurante suspendido.

**Why this priority**: Una caché que sirve datos viejos produce pedidos con precios o platos incorrectos.
Sin esta garantía, la caché de la historia 1 no se puede usar.

**Independent Test**: Se consulta un catálogo (queda guardado), el restaurante cambia el precio de un plato y
se vuelve a consultar; el nuevo precio aparece en la siguiente consulta. Se repite con cada tipo de cambio
de la lista de FR-004.

**Acceptance Scenarios**:

1. **Given** un catálogo ya consultado, **When** el restaurante crea, edita, desactiva, marca como no
   disponible o retira un plato, **Then** la siguiente consulta pública muestra el cambio.
2. **Given** la lista y los filtros ya consultados, **When** el restaurante cambia su nombre, categoría,
   zona, descripción, imágenes u horario, **Then** la siguiente consulta los muestra actualizados.
3. **Given** un restaurante visible, **When** el administrador lo suspende o deshabilita su cuenta,
   **Then** desaparece de la lista y su catálogo responde "no encontrado" en la siguiente consulta; y al
   aprobar uno nuevo, este aparece.

---

### User Story 3 - La plataforma funciona igual con varias instancias (Priority: P2)

Como responsable de la plataforma, quiero poder levantar más de una instancia del backend para atender más
usuarios o seguir funcionando si una cae, sin que cambien las reglas que hoy se cumplen con una sola.

**Why this priority**: Es la base de RNF-02 y RNF-04. Hoy el freno de fuerza bruta del inicio de sesión y el
límite de mensajes del asistente se cuentan en la memoria de cada instancia: con dos instancias, un atacante
tendría el doble de intentos.

**Independent Test**: Con dos instancias que comparten la base de datos y el almacén, se reparten intentos de
inicio de sesión fallidos entre ambas y se comprueba que el bloqueo llega al mismo número de fallos que con
una sola; lo mismo con los mensajes del asistente.

**Acceptance Scenarios**:

1. **Given** dos instancias activas, **When** se envían 5 inicios de sesión fallidos para la misma cuenta
   repartidos entre ambas, **Then** el siguiente intento se rechaza por exceso de intentos en cualquiera.
2. **Given** dos instancias activas, **When** un cliente envía al asistente el máximo de mensajes por minuto
   repartidos entre ambas, **Then** el siguiente se rechaza en cualquiera de ellas.
3. **Given** un cambio de catálogo hecho a través de una instancia, **When** se consulta a través de la
   otra, **Then** se ve el cambio.
4. **Given** dos instancias ejecutando el barrido de pedidos vencidos, **When** vence un pedido pendiente,
   **Then** se cancela una sola vez, con un solo aviso y una sola liberación de su reserva de pago.

---

### User Story 4 - El rendimiento se mide con pruebas de carga reproducibles (Priority: P2)

Como responsable de la plataforma, quiero pruebas de carga que cualquiera pueda repetir y que digan si se
cumplen los objetivos, para demostrar con datos el efecto de la caché y de la revisión de la base de datos.

**Why this priority**: Sin medición no hay evidencia de RNF-01 ni RNF-03 (principio VI), y el "antes y
después" es lo que justifica la caché.

**Independent Test**: Se ejecutan las pruebas de humo, carga y estrés siguiendo sus instrucciones; cada
corrida termina indicando si cumplió los umbrales y queda un informe con versión, datos, duración y
configuración.

**Acceptance Scenarios**:

1. **Given** la plataforma en marcha con los datos de prueba, **When** se ejecuta la prueba de humo,
   **Then** recorre catálogo público, inicio de sesión y creación de pedido sin errores en pocos usuarios.
2. **Given** la prueba de carga, **When** termina, **Then** informa el percentil 95 de tiempo y la tasa de
   errores de cada flujo y si cumplió los umbrales, y la corrida falla si no los cumple.
3. **Given** la prueba de estrés, **When** termina, **Then** el informe registra a partir de cuántos
   usuarios concurrentes se incumplen los umbrales.
4. **Given** dos corridas de la misma prueba, sin caché y con caché, **When** se comparan, **Then** el
   informe muestra la diferencia en tiempos, errores y consultas atendidas desde la caché.

---

### Edge Cases

- **Almacén de caché caído o lento**: las consultas públicas siguen respondiendo correctamente desde la base
  de datos (más lentas), sin errores para el usuario; al volver, la caché se repuebla sola.
- **Freno de inicio de sesión sin almacén compartido**: el freno vuelve a contar en la memoria de cada
  instancia; el inicio de sesión sigue funcionando y nunca queda abierto sin límite.
- **Cambio y consulta simultáneos**: una consulta que lee datos mientras otro los cambia no deja guardada la
  versión vieja después del cambio.
- **Pedido con datos de la caché**: confirmar un pedido siempre valida precios, disponibilidad y estado del
  restaurante contra la base de datos, aunque el catálogo mostrado viniera de la caché.
- **Fin del día y cambio de horario**: el estado abierto o cerrado cambia a la hora exacta sin esperar a que
  caduque lo guardado.
- **Búsquedas con texto libre**: búsquedas distintas por nombre no llenan el almacén sin límite.
- **Catálogo paginado**: cambiar un plato actualiza todas las páginas del catálogo de ese restaurante.
- **Pago con tarjeta en la carga**: la creación de pedido de las pruebas de carga no llama a la pasarela de
  pagos.

## Requirements *(mandatory)*

### Functional Requirements

**Caché de consultas públicas (RF-09, RF-10, RNF-01, RNF-03)**

- **FR-001**: La plataforma DEBE guardar y reutilizar los datos de las consultas públicas de solo lectura:
  restaurantes visibles, opciones de filtro y productos disponibles de cada restaurante.
- **FR-002**: El estado de atención (abierto, fuera de horario, pausado) DEBE calcularse en cada consulta con
  la hora actual; NUNCA DEBE servirse un estado guardado.
- **FR-003**: La plataforma NO DEBE guardar en caché datos personales ni transaccionales: pedidos, carrito,
  pagos, notificaciones, direcciones, historiales, cuentas ni datos privados del restaurante.
- **FR-004**: Todo cambio que afecte lo público DEBE descartar los datos guardados afectados antes de que la
  siguiente consulta los lea. Como mínimo: crear, editar, cambiar disponibilidad, desactivar o retirar un
  producto; cambiar datos, imágenes, horario o disponibilidad del restaurante; aprobar, rechazar, suspender
  o reactivar un restaurante; y habilitar o deshabilitar la cuenta de su titular.
- **FR-005**: Los datos guardados DEBEN caducar solos tras un tiempo máximo, como respaldo si un descarte
  fallara; ese tiempo NO DEBE ser el mecanismo normal de actualización.
- **FR-006**: Si el almacén de caché no está disponible, las consultas DEBEN responder igual leyendo de la
  base de datos, sin error para el usuario, y la falla DEBE quedar registrada.
- **FR-007**: Confirmar un pedido DEBE seguir validando precios, disponibilidad y estado contra la base de
  datos (sin cambio respecto de la spec 006).
- **FR-008**: El almacén de caché DEBE poder cambiarse entre uno en la misma máquina, uno gestionado o uno en
  memoria para desarrollo solo con configuración, sin cambiar el código. Sin configuración adicional, la
  aplicación DEBE arrancar en local con el almacén en memoria, sin requerir contenedores.

**Coherencia con varias instancias (RNF-02, RNF-04)**

- **FR-009**: El freno de fuerza bruta del inicio de sesión (spec 002) y el límite de mensajes del asistente
  (spec 013) DEBEN contarse de forma compartida entre todas las instancias, con los mismos límites de hoy.
- **FR-010**: El barrido de pedidos vencidos (spec 012) DEBE cancelar cada pedido una sola vez aunque lo
  ejecuten varias instancias a la vez.
- **FR-010a**: Si el almacén compartido no está disponible, los contadores de FR-009 DEBEN seguir
  aplicándose en la memoria de cada instancia, sin bloquear el inicio de sesión ni el asistente.
- **FR-011**: Ninguna instancia DEBE guardar estado de sesión propio: cualquier solicitud DEBE poder
  atenderse por cualquier instancia.

**Base de datos (criterio 9)**

- **FR-012**: Las consultas de mayor tráfico (búsqueda y catálogo público, inicio de sesión, creación y
  consulta de pedidos) DEBEN revisarse con su plan de ejecución sobre datos de volumen, añadiendo los índices
  que falten mediante una migración nueva.
- **FR-013**: La cantidad de conexiones a la base de datos por instancia DEBE configurarse según el límite de
  conexiones del servicio de base de datos y la cantidad de instancias.
- **FR-014**: La búsqueda pública NO DEBE cargar todos los restaurantes desde la base de datos en cada
  consulta.

**Pruebas de carga (criterio 13, RNF-01)**

- **FR-015**: DEBEN existir pruebas de carga reproducibles de humo, carga y estrés sobre: búsqueda y catálogo
  público, inicio de sesión y creación de pedido.
- **FR-016**: Cada prueba DEBE declarar umbrales y terminar como fallida si no se cumplen. Los umbrales base
  son los de la propuesta: percentil 95 menor a 2 s y tasa de errores menor al 1 %.
- **FR-017**: La creación de pedido en las pruebas de carga DEBE usar pago en efectivo, sin llamar a la
  pasarela de pagos.
- **FR-018**: DEBE existir un procedimiento para cargar datos de volumen de prueba (restaurantes, productos,
  clientes) que no se use con datos reales.
- **FR-019**: Cada corrida DEBE dejar un informe con versión de la aplicación, datos usados, duración,
  usuarios, configuración (con o sin caché, cantidad de instancias) y resultados.
- **FR-020**: La plataforma DEBE poder medir qué parte de las consultas públicas repetidas se atendieron desde
  la caché.

### Key Entities

- **Dato público guardado**: copia de la lista de restaurantes visibles, de las opciones de filtro o de los
  productos disponibles de un restaurante; tiene un tiempo máximo de vida y se descarta ante los cambios de
  FR-004. No contiene estado de atención calculado.
- **Contador compartido**: intentos fallidos de inicio de sesión por cuenta y por dirección, y mensajes del
  asistente por cliente, dentro de su ventana de tiempo; visible para todas las instancias.
- **Informe de carga**: resultado de una corrida (versión, datos, duración, usuarios, configuración,
  percentiles, tasa de errores, aciertos de caché, cumplimiento de umbrales).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con 200 usuarios concurrentes, el 95 % de las consultas de restaurantes y catálogo se
  responde en menos de 2 s y menos del 1 % falla. La prueba de estrés registra el máximo alcanzado; RNF-01
  (1 000 usuarios) queda como parcial con ese dato.
- **SC-002**: Al menos el 80 % de las consultas públicas repetidas se atiende desde la caché durante la
  prueba de carga (RNF-03).
- **SC-003**: Con caché, el percentil 95 de las consultas públicas mejora al menos un 50 % frente a la misma
  corrida sin caché.
- **SC-004**: El 100 % de los cambios de la lista de FR-004 se ve en la siguiente consulta pública, en
  menos de 1 s.
- **SC-005**: Con el almacén de caché detenido, el 100 % de las consultas públicas sigue respondiendo
  correctamente.
- **SC-006**: Con dos instancias, el bloqueo por fuerza bruta y el límite del asistente se alcanzan con el
  mismo número de intentos que con una sola, y ningún pedido vencido se cancela dos veces.
- **SC-007**: Cualquier integrante del equipo puede repetir una corrida de carga siguiendo solo sus
  instrucciones y obtener un informe comparable.

## Assumptions

- El almacén de caché previsto es Redis (decisión del usuario del 2026-11-26, enmienda 6.0.0): en local,
  opcional mediante un contenedor; en el despliegue futuro, en la misma instancia del backend. La
  configuración de AWS queda fuera de este incremento.
- La caché es solo del backend; el frontend estático ya se publica con caché de CDN (recursos inmutables por
  un año, página principal sin caché) y no cambia en esta spec.
- Las pruebas de carga se ejecutan primero en local y se repiten tras el despliegue; los resultados locales
  no acreditan RNF-01 en producción (principio VI).
- La herramienta de carga es k6, conservada por la constitución para la evaluación futura.
- El tiempo máximo de vida de respaldo es del orden de minutos (se fija en el plan).
- RNF-10 (panel de CPU, tiempos y errores en la nube) y la prueba de caída en la nube de RNF-04 quedan fuera.
  La prueba de varias instancias (historia 3, RNF-02) se hace solo en local con dos instancias; RNF-02 y
  RNF-04 quedan como parciales.
- No cambian reglas de negocio, contratos públicos ni pantallas.
