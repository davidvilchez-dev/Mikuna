# Feature Specification: Página de inicio pública de Mikuna

> **Actualización 2026-11-13:** el usuario amplió el diseño a las cuatro páginas del menú y pidió
> un footer. [Ampliación visual](ampliacion-visual.md) define INICIO-021 a INICIO-026 y prevalece
> sobre las restricciones originales de pantalla única, ausencia de footer y navegación con aviso.
> Playwright y la adaptación de su suite quedan pospuestos por instrucción del usuario.

**Feature Branch**: No creada; no hay un hook de Git configurado.

**Feature Directory**: `specs/001-inicio-publico`

**Created**: 2026-11-12

**Status**: Clarified — comportamiento de navegación definido; listo para planificación.

**Input**: User description: "Especificar el primer incremento de Mikuna: inicio público con el logo y la navegación de la referencia, sección principal ‘Tu antojo, en camino.’, acciones, composición de alitas con pizza y hamburguesa y seis categorías. Terminar el contenido en las categorías, respetar la paleta indicada y revisar la calidad de la especificación."

**Origen del incremento**: Instrucción explícita del usuario del 2026-11-12, conforme al desarrollo
incremental de la constitución. No se le atribuye un RF de la propuesta. Los identificadores
`INICIO-001` a `INICIO-020` son locales de esta especificación y no sustituyen ni amplían los RF.

**Fuentes consultadas**:
[constitución v1.0.0](../../.specify/memory/constitution.md),
[propuesta del proyecto](../../Propuesta_de_Proyecto.docx),
[referencia visual](../../docs/diseno/inicio-referencia.png) y
[logo suministrado](../../docs/diseno/logo-mikuna.png) y
[recursos de categorías](../../docs/seis_categorías/).
La instrucción del usuario fija el alcance y la paleta; la referencia fija los detalles visuales
no contradictorios y los rótulos transcritos en esta especificación.

## Clarifications

### Session 2026-11-12

- Q: ¿Qué deben hacer en este incremento todos los enlaces, botones y categorías que apuntan a funciones todavía no disponibles? → A: Los cuatro enlaces futuros del encabezado, las tres acciones “Pedir ahora”, “Empezar a pedir” y “Crear cuenta” y las seis categorías muestran al activarse el aviso temporal «Esta función estará disponible próximamente», sin salir del inicio.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conocer Mikuna desde el inicio público (Priority: P1)

Como visitante sin cuenta, quiero reconocer Mikuna y entender su propuesta para decidir si deseo
seguir explorando el servicio.

**Why this priority**: Es el propósito principal de la página y permite presentar el servicio sin
depender de funcionalidades posteriores.

**Independent Test**: Abrir el inicio como visitante sin sesión y comprobar únicamente el
encabezado y la sección principal, sin necesitar cuentas, restaurantes o pedidos disponibles.

**Acceptance Scenarios**:

1. **Given** una persona sin sesión iniciada, **When** abre el inicio, **Then** ve el logo y el
   contenido público sin registro, inicio de sesión ni permiso de ubicación obligatorios.
2. **Given** la sección principal visible, **When** lee su contenido, **Then** encuentra exactamente
   “Tu antojo, en camino.” y “Descubre restaurantes cerca de ti y disfruta tus favoritos sin salir
   de casa.”, con las acciones “Empezar a pedir” y “Crear cuenta”.
3. **Given** el diseño de escritorio, **When** observa la sección principal, **Then** ve el texto
   a la izquierda y las alitas como imagen dominante a la derecha, con pizza arriba y hamburguesa
   abajo superpuestas sobre la composición, sin cubrir texto ni acciones.
4. **Given** el inicio cargado, **When** compara sus bloques con la referencia, **Then** encuentra
   encabezado, sección principal y categorías, en ese orden, sin secciones posteriores.

### User Story 2 - Explorar las categorías de comida (Priority: P2)

Como visitante, quiero reconocer las categorías de comida que presenta Mikuna para identificar
las opciones que me interesan.

**Why this priority**: Expone la variedad del servicio sin exigir un listado de restaurantes ni
una consulta de productos, que corresponden a otros incrementos.

**Independent Test**: Revisar la sección de categorías y verificar sus imágenes, rótulos y orden,
sin ejecutar búsquedas ni disponer de restaurantes registrados.

**Acceptance Scenarios**:

1. **Given** el final de la sección principal, **When** continúa hacia abajo, **Then** encuentra
   el título exacto “¿Qué se te antoja hoy?” y seis categorías, cada una con su imagen y nombre.
2. **Given** las categorías visibles, **When** las recorre en orden de lectura, **Then** encuentra
   Hamburguesas, Pollo, Pizza, Comida peruana, Postres y Bebidas, sin duplicados ni categorías extra.
3. **Given** la última categoría, **When** llega al final del contenido, **Then** no aparece un
   listado de restaurantes, productos, promociones adicionales ni un pie de página.
4. **Given** cualquiera de las seis categorías, **When** la activa, **Then** aparece el aviso
   temporal “Esta función estará disponible próximamente”, permanece en la misma vista del inicio
   y no se abre un catálogo, un filtro ni un listado de restaurantes.

### User Story 3 - Orientarse con la navegación del inicio (Priority: P2)

Como visitante, quiero reconocer las opciones del encabezado y las acciones principales, y poder
volver al comienzo de la página sin perder mi orientación.

**Why this priority**: Mantiene la navegación de la referencia y distingue las opciones de inicio
de los accesos a funcionalidades que todavía no se han construido.

**Independent Test**: Comprobar los rótulos y el estado activo, activar logo e “Inicio” y verificar
el aviso de cada acceso futuro, sin disponer de las funcionalidades posteriores.

**Acceptance Scenarios**:

1. **Given** el encabezado de escritorio, **When** recorre la navegación, **Then** encuentra
   “Inicio”, “Restaurantes”, “Cómo funciona”, “Para restaurantes”, “Sé repartidor” y “Pedir ahora”,
   en ese orden, con “Inicio” activo y “Pedir ahora” destacado.
2. **Given** que ha bajado por la página, **When** activa el logo o “Inicio”, **Then** vuelve al
   comienzo de esta misma página pública, sin abrir una funcionalidad posterior.
3. **Given** una pantalla que agrupa la navegación, **When** abre y cierra el menú, **Then** puede
   encontrar las mismas opciones, conocer si está abierto y continuar sin quedar atrapado en él.
4. **Given** cualquiera de los cuatro enlaces futuros del encabezado o de las tres acciones
   principales, **When** lo activa, **Then** aparece el aviso temporal “Esta función estará
   disponible próximamente”, conserva la vista actual y no navega ni inicia una operación futura.

### User Story 4 - Usar el inicio en distintos tamaños de pantalla (Priority: P2)

Como visitante desde un móvil, una tableta o un escritorio, quiero leer y recorrer el mismo
contenido sin recortes ni elementos que se superpongan e impidan usarlo.

**Why this priority**: La página es pública y debe conservar su contenido y jerarquía en los tres
contextos solicitados, aunque la referencia solo muestre escritorio.

**Independent Test**: Revisar las seis ventanas definidas en la matriz de adaptación, cambiar
orientación y recorrer la página con teclado, sin ejecutar operaciones del servicio.

**Acceptance Scenarios**:

1. **Given** cualquiera de las seis ventanas de verificación, **When** recorre toda la página,
   **Then** todos los textos, acciones, fotografías y categorías están disponibles mediante
   desplazamiento vertical, sin desplazamiento horizontal de la página ni texto cortado.
2. **Given** una pantalla móvil, **When** lee la sección principal, **Then** encuentra primero el
   texto y las acciones y después la composición de comida, que conserva las tres imágenes.
3. **Given** una pantalla de escritorio, **When** observa las categorías, **Then** ve las seis en
   una fila; en móvil se distribuyen en dos columnas y en tableta en tres, manteniendo el orden.
4. **Given** que usa teclado o amplía el texto al 200 %, **When** recorre los elementos disponibles,
   **Then** distingue el foco y puede acceder al contenido sin textos o controles ocultos por
   superposición. El aviso de función futura se anuncia de forma accesible y permite continuar
   usando la página.

### Edge Cases

- **Imagen no disponible**: el texto descriptivo y los nombres de las seis categorías siguen
  siendo legibles; la ausencia de una imagen no oculta acciones ni altera el orden del contenido.
  La entrega visual normal sí requiere las imágenes definidas.
- **Pantalla estrecha de 320 px**: la navegación puede agruparse y las acciones y fotografías
  redistribuirse; ningún elemento provoca desplazamiento horizontal de la página.
- **Cambio de orientación**: se conserva el contenido y el estado comprensible del menú;
  ningún panel abierto tapa permanentemente las opciones o impide cerrarlo.
- **Textos en más de una línea o ampliados**: se permite aumentar la altura de los bloques;
  no se recortan “Comida peruana”, el título ni los rótulos de las acciones.
- **Usuario sin cuenta o sin permiso de ubicación**: el inicio permanece accesible; el texto
  “cerca de ti” no activa por sí mismo una solicitud de geolocalización.
- **Funcionalidad futura inexistente**: cada acceso muestra el aviso temporal confirmado, sin
  crear pantallas vacías, errores de destino, simulaciones de operaciones ni secciones adicionales.
  Repetir la activación conserva ese mismo resultado y no acumula avisos que tapen el contenido.

## Requirements *(mandatory)*

### Functional Requirements

- **INICIO-001**: El inicio DEBE ser público y consultable sin sesión, registro ni permiso de
  ubicación obligatorio. Su lectura no DEBE solicitar datos personales ni datos de pago.
- **INICIO-002**: El contenido DEBE contener solo encabezado, sección principal y categorías,
  en ese orden. DEBE terminar después de las categorías; se excluyen listado de restaurantes,
  productos, búsqueda, formularios de acceso, carrito, pedidos, pagos y pie de página.
- **INICIO-003**: El encabezado DEBE mostrar el logo de `docs/diseno/logo-mikuna.png`, completo y
  con sus proporciones y colores originales, sin sustituirlo por texto o por otro logotipo.
- **INICIO-004**: El encabezado DEBE mostrar los seis rótulos y el orden definidos en US3.1;
  “Inicio” DEBE distinguirse como opción activa mediante el subrayado naranja de la referencia.
- **INICIO-005**: El título principal DEBE decir exactamente “Tu antojo, en camino.”. En escritorio
  DEBE presentarse en dos líneas: “Tu antojo,” en índigo y “en camino.” en naranja, con la jerarquía
  tipográfica dominante de la referencia. En pantallas menores puede redistribuirse sin cambiar
  el texto ni la asociación de cada fragmento con su color.
- **INICIO-006**: La descripción DEBE ser “Descubre restaurantes cerca de ti y disfruta tus
  favoritos sin salir de casa.”, en gris y con menor jerarquía que el título.
- **INICIO-007**: La sección principal DEBE mostrar “Empezar a pedir” como acción primaria naranja,
  con texto blanco y flecha hacia la derecha, y “Crear cuenta” como acción secundaria con texto
  y borde índigo. “Pedir ahora” DEBE conservar el tratamiento naranja destacado del encabezado.
- **INICIO-008**: La composición principal DEBE mostrar alitas sobre un fondo melocotón de contorno
  redondeado; la pizza circular DEBE superponerse arriba a la derecha y la hamburguesa en un
  recuadro redondeado abajo a la derecha, ambas con borde blanco. Las alitas DEBEN ser la imagen
  dominante. La superposición NO DEBE cubrir título, descripción, controles o categorías.
- **INICIO-009**: La sección de categorías DEBE mostrar exactamente “¿Qué se te antoja hoy?” y las
  seis categorías definidas en US2.2, con ese orden de lectura en todos los tamaños.
- **INICIO-010**: Cada categoría DEBE mostrar una imagen de comida reconocible y su rótulo separado
  debajo: hamburguesa, pollo, porción de pizza, plato de comida peruana, postre y bebida,
  respectivamente. Es contenido de presentación; no DEBE implicar datos reales de restaurantes,
  productos disponibles, precios o resultados de una búsqueda.
- **INICIO-011**: Los colores base de los elementos de interfaz DEBEN seguir la tabla de paleta.
  Las variaciones propias de fotografías, suavizado de texto y del logo suministrado no se
  consideran colores adicionales de la interfaz ni autorizan recolorear el logo.
- **INICIO-012**: La presentación de escritorio DEBE satisfacer los diez puntos de fidelidad
  visual V-01 a V-10 y las comprobaciones de proporción descritas más abajo.
- **INICIO-013**: La navegación DEBE conservar todos sus rótulos en móvil y tableta. Si se agrupa,
  DEBE poder abrirse y cerrarse, indicar su estado y ser operable con teclado sin atrapar el foco.
- **INICIO-014**: La página DEBE satisfacer la matriz de adaptación sin desplazamiento horizontal
  de la página, pérdida de contenido, deformación de imágenes ni superposición que impida leer o
  usar controles. El desplazamiento vertical está permitido en todos los tamaños.
- **INICIO-015**: Los textos DEBEN ser legibles como contenido de la página y no formar parte de
  una captura única del diseño. El logo y las imágenes informativas DEBEN tener una descripción
  accesible; los controles disponibles DEBEN tener nombre reconocible y foco visible. Las áreas
  activables DEBEN medir al menos 44 × 44 px y conservar acceso al ampliar texto al 200 %.
- **INICIO-016**: Activar el logo o “Inicio” DEBE llevar al comienzo de esta misma página pública.
  No se prescribe una nueva página ni un destino de negocio para esta acción.
- **INICIO-017**: Activar “Restaurantes”, “Cómo funciona”, “Para restaurantes” o “Sé repartidor”
  DEBE mostrar el aviso temporal “Esta función estará disponible próximamente”, sin navegar ni
  cambiar la vista actual. No se añade una sección “Cómo funciona” ni otra funcionalidad.
- **INICIO-018**: Activar “Pedir ahora”, “Empezar a pedir” o “Crear cuenta” DEBE mostrar el aviso
  temporal “Esta función estará disponible próximamente”, sin navegar ni cambiar la vista actual.
  Ninguna de ellas DEBE iniciar autenticación, catálogo, pedidos o pagos en este incremento.
- **INICIO-019**: Activar cualquiera de las seis categorías DEBE mostrar el aviso temporal
  “Esta función estará disponible próximamente”, sin navegar ni cambiar la vista actual.
  No se aplican filtros, resultados de restaurantes ni selecciones persistentes.
- **INICIO-020**: Si una imagen no carga, DEBEN conservarse el texto, los nombres de categorías,
  el orden de lectura y el acceso a los controles disponibles; el resto de la página DEBE
  permanecer utilizable. Este caso alternativo no sustituye la fidelidad exigida con imágenes.

### Contenido y fidelidad visual

Se utiliza `docs/diseno/inicio-referencia.png` como referencia visual, no como un bloque que
reemplace todo el contenido de la página. La comparación de escritorio se realiza a **1536 × 1024 px**,
que es el tamaño de la referencia. Deben cumplirse los diez puntos siguientes:

| Punto | Criterio observable |
| --- | --- |
| V-01 | Tres bloques en orden: encabezado blanco, sección principal marfil y categorías blancas; el contenido termina en las categorías. |
| V-02 | Logo a la izquierda del encabezado y navegación a su derecha; “Inicio” subrayado y “Pedir ahora” destacado. |
| V-03 | Sección principal en dos columnas: texto y acciones a la izquierda, composición de comida a la derecha. |
| V-04 | Título de mayor tamaño que el resto del texto, con primera línea índigo y segunda naranja y el texto exacto de INICIO-005. |
| V-05 | Descripción gris debajo del título y las dos acciones debajo de ella, alineadas en una fila en escritorio. |
| V-06 | Acción primaria naranja y secundaria con borde índigo; formas redondeadas como en la referencia. |
| V-07 | Plato de alitas de mayor tamaño que las dos imágenes secundarias, sobre el fondo melocotón. |
| V-08 | Pizza circular arriba y hamburguesa en recuadro redondeado abajo, ambas a la derecha y superpuestas con borde blanco. |
| V-09 | Título de categorías índigo, alineado a la izquierda con el texto de la sección principal. |
| V-10 | Seis imágenes de categoría sobre sus rótulos índigo, en una fila de escritorio, sin tarjetas de restaurantes ni secciones posteriores. |

Para que la fidelidad no dependa solo de una apreciación subjetiva, a 1536 × 1024 px el margen
lateral izquierdo del texto principal y del título de categorías DEBE estar entre el 5 % y el
6 % del ancho; el encabezado DEBE ocupar entre el 10 % y el 13 % de la altura; y el inicio del
bloque de categorías DEBE situarse entre el 60 % y el 68 % de la altura. Son tolerancias de revisión
derivadas de esta referencia, no umbrales de la propuesta ni restricciones para pantallas menores.
La tipografía DEBE conservar la apariencia sin serifas y la jerarquía de pesos de la referencia;
no se impone una familia tipográfica específica.

### Paleta de colores

| Color | Hexadecimal | Uso |
| --- | --- | --- |
| Azul índigo | `#11094C` | Títulos, navegación y bordes. |
| Naranja | `#FF5A1F` | Botones y elementos destacados. |
| Marfil | `#FDF8F4` | Fondo de la sección principal. |
| Melocotón claro | `#FDE8D8` | Fondo detrás de las alitas. |
| Gris | `#464B53` | Texto descriptivo. |
| Blanco | `#FFFFFF` | Encabezado y sección de categorías; texto de acciones naranjas y bordes de imágenes superpuestas. |

### Adaptación y ventanas de aceptación

Estos tamaños son casos de verificación del incremento, no puntos de cambio de diseño obligatorios.
Las distribuciones de móvil y tableta son supuestos de adaptación documentados porque la referencia
solo muestra escritorio.

| Contexto | Ventanas de verificación, ancho × alto | Resultado observable |
| --- | --- | --- |
| Móvil | 320 × 568 y 390 × 844 px | Texto y acciones antes de las fotografías; categorías en dos columnas; navegación agrupada cuando no quepa completa. |
| Tableta | 768 × 1024 y 1024 × 768 px | Encabezado sin colisiones, composición y texto legibles y categorías en tres columnas, con el mismo orden. |
| Escritorio | 1440 × 900 y 1536 × 1024 px | Encabezado horizontal, sección principal en dos columnas y seis categorías en una fila; comparación con la referencia a 1536 × 1024. |

En las seis ventanas DEBE poder encontrarse todo el contenido sin desplazamiento horizontal de
la página. Se permite redistribuir o apilar las acciones y ajustar las posiciones de las fotografías
en pantallas menores, conservando los tres alimentos y sin tapar otros contenidos.

### Matriz de navegación y aclaraciones

AP-01, AP-02 y AP-03 se resolvieron mediante una política común confirmada por el usuario en la
sesión del 2026-11-12: los accesos a funcionalidades futuras muestran el aviso temporal
“Esta función estará disponible próximamente” y conservan la vista actual del inicio.
No tienen un destino de navegación en este incremento.

| Elemento | Destino o comportamiento confirmado | Estado |
| --- | --- | --- |
| Logo Mikuna e “Inicio” | Volver al comienzo de esta misma página pública. | Definido, INICIO-016. |
| “Restaurantes” | Mostrar el aviso temporal común; permanecer en el inicio. | Confirmado, AP-01. |
| “Cómo funciona” | Mostrar el aviso temporal común; no añadir una sección. | Confirmado, AP-01. |
| “Para restaurantes” | Mostrar el aviso temporal común; permanecer en el inicio. | Confirmado, AP-01. |
| “Sé repartidor” | Mostrar el aviso temporal común; permanecer en el inicio. | Confirmado, AP-01. |
| “Pedir ahora” | Mostrar el aviso temporal común; permanecer en el inicio. | Confirmado, AP-02. |
| “Empezar a pedir” | Mostrar el aviso temporal común; permanecer en el inicio. | Confirmado, AP-02. |
| “Crear cuenta” | Mostrar el aviso temporal común; permanecer en el inicio. | Confirmado, AP-02. |
| Hamburguesas, Pollo, Pizza, Comida peruana, Postres y Bebidas | Mostrar el aviso temporal común; no filtrar ni navegar. | Confirmado, AP-03 para las seis. |

- **AP-01 — Enlaces del encabezado, resuelta**: los cuatro enlaces indicados muestran el aviso
  temporal común sin navegar ni incorporar secciones.
- **AP-02 — Acciones principales, resuelta**: las tres acciones indicadas muestran el aviso
  temporal común sin navegar ni iniciar operaciones de negocio.
- **AP-03 — Categorías, resuelta**: las seis categorías muestran el aviso temporal común
  sin navegar, filtrar ni guardar una selección.

El aviso DEBE ser transitorio, anunciarse de forma accesible y permitir seguir usando la página;
no añade una sección permanente ni exige una operación de negocio. Su presentación y duración
concretas se precisan en la planificación, conservando el mensaje y el comportamiento confirmados.
US2, US3 y SC-005 verifican esta política. Los destinos de las funcionalidades posteriores se
acordarán en sus respectivos incrementos, sin reservar rutas ni simularlas en esta entrega.

### Alcance y trazabilidad con el proyecto

| Origen o código existente | Relación con este incremento |
| --- | --- |
| Instrucción del usuario, 2026-11-12 | Origen de todos los requisitos INICIO-001 a INICIO-020; página pública, referencia, logo, paleta y final del contenido en categorías. |
| Constitución, principios II, III, V y VI | Separación de presentación y negocio, acceso público, verificación y trazabilidad sin inventar RF. |
| RF-01 y RF-02 | Acceso y recuperación de cuentas: futuros. Mostrar “Crear cuenta” no los implementa. |
| RF-05 | Registro de restaurantes: futuro. El enlace “Para restaurantes” no lo implementa. |
| RF-09 y RF-10 | Consulta de restaurantes y productos: futura. Las categorías de portada no satisfacen estos RF. |
| RF-11 a RF-17 | Carrito, dirección, importes, pedido y pago: futuros. Las acciones de portada no satisfacen estos RF. |
| RNF-06 y aclaración de RNF-07 | Al entregar la página se verifica acceso público y comunicación cifrada conforme a la constitución; no se crean operaciones protegidas en este incremento. |
| RNF-11 | Se conserva el mínimo del 90 % de líneas de lógica de negocio y el reporte de ramas sin umbral. Si solo existe presentación, se justifica como no aplicable a ese código; no se declara un 100 % ni se omite verificar la interfaz. |
| RNF-12 | La entrega posterior mantiene la obligación de pruebas y despliegue automáticos; esta especificación no constituye evidencia de su ejecución. |
| Otros RF y RNF de la propuesta | Permanecen vigentes y pendientes de sus incrementos y verificaciones; no se declaran cumplidos por esta portada. |

No hay ningún RF de la propuesta que se declare completado por este incremento. El alcance
no incorpora autenticación, catálogo, listado de restaurantes, pedidos, pagos, geolocalización
operativa, incorporación de restaurantes o repartidores ni sus procesos de gestión.

### Key Entities *(include if feature involves data)*

- **Elemento de navegación**: rótulo, orden, estado activo y comportamiento; logo e “Inicio”
  vuelven al comienzo, y los elementos de AP-01/AP-02 muestran el aviso temporal sin navegar.
- **Categoría de portada**: nombre, imagen representativa, orden de lectura y aviso temporal
  al activarse, según AP-03. No representa un catálogo consultable ni tiene un destino en esta entrega.
- **Contenido visual de portada**: logo, texto principal, descripción, composición de alimentos
  y paleta; es contenido de presentación y no incluye datos de cuentas, restaurantes o pedidos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 — Contenido completo**: En el 100 % de las seis ventanas se encuentran el logo,
  los seis rótulos del encabezado, el título y la descripción exactos, las dos acciones principales,
  las tres imágenes de comida, el título de categorías y las seis categorías, sin omisiones.
- **SC-002 — Fidelidad verificable**: A 1536 × 1024 px se cumplen los 10 de 10 puntos V-01 a V-10,
  las tres tolerancias de proporción y los seis colores base con los usos definidos en la paleta.
- **SC-003 — Adaptación**: Las 6 de 6 ventanas de aceptación permiten leer y recorrer el 100 %
  del contenido mediante desplazamiento vertical, con cero textos o controles cortados u ocultos
  por superposición y sin desplazamiento horizontal de la página.
- **SC-004 — Alcance acotado**: Se muestran exactamente seis categorías, una vez cada una y en
  el orden indicado, y cero listados de restaurantes, productos, formularios de acceso, carritos,
  flujos de pago, secciones adicionales o pies de página.
- **SC-005 — Navegación confirmada**: Logo e “Inicio” vuelven al comienzo en todos los contextos.
  El 100 % de los 13 accesos futuros —cuatro enlaces, tres acciones y seis categorías— muestra
  al activarse el aviso temporal “Esta función estará disponible próximamente”, mantiene la vista
  actual y no inicia navegación, filtros u operaciones de negocio. El aviso es accesible,
  transitorio y no bloquea la página, también ante activaciones repetidas.
- **SC-006 — Acceso a controles**: El 100 % de los controles disponibles puede alcanzarse con
  teclado y tiene un foco reconocible y un área activable de al menos 44 × 44 px. En las ventanas
  con menú agrupado se puede abrir, recorrer y cerrar sin quedar atrapado ni perder opciones.
- **SC-007 — Resistencia a imágenes ausentes**: Al impedir la carga de imágenes siguen visibles
  el título, la descripción, los nombres de las seis categorías y los rótulos de navegación y
  acciones; ningún control disponible queda cubierto por el espacio de una imagen fallida.
- **SC-008 — Entrada pública**: En las seis ventanas, un visitante sin cuenta puede abrir y leer
  el inicio con cero solicitudes obligatorias de registro, inicio de sesión, ubicación o pago.

La evidencia de aceptación futura debe vincular cada resultado con los requisitos y escenarios
siguientes. Esta relación no equivale a haber ejecutado las pruebas durante la especificación.

| Requisitos locales | Escenarios de aceptación | Resultados |
| --- | --- | --- |
| INICIO-001 | US1.1 y caso sin permiso de ubicación. | SC-008. |
| INICIO-002 | US1.4 y US2.3. | SC-004. |
| INICIO-003 a INICIO-008 | US1.2, US1.3 y US3.1. | SC-001 y SC-002. |
| INICIO-009 e INICIO-010 | US2.1 y US2.2. | SC-001, SC-002 y SC-004. |
| INICIO-011 e INICIO-012 | Comparación con V-01 a V-10 y tabla de paleta. | SC-002. |
| INICIO-013 a INICIO-015 | US3.3 y US4.1 a US4.4. | SC-003 y SC-006. |
| INICIO-016 | US3.2. | SC-005. |
| INICIO-017 a INICIO-019 | US2.4 y US3.4; política común confirmada en AP-01 a AP-03. | SC-005. |
| INICIO-020 | Caso de imagen no disponible. | SC-007. |

## Assumptions

- El idioma del contenido es español. Los rótulos del encabezado, la descripción y los detalles
  visuales se transcriben de la referencia; las instrucciones y colores explícitos del usuario
  prevalecen sobre diferencias de color o contenido en esa imagen.
- El viewport mínimo de aceptación es de 320 px de ancho. La organización de dos columnas de
  categorías en móvil y tres en tableta y los tamaños de verificación son supuestos explícitos
  para completar el diseño adaptable, no requisitos atribuidos a la propuesta.
- El logo y “Inicio” llevan al comienzo de la página, como convención de navegación local.
  Los demás accesos muestran el aviso temporal confirmado, sin destinos de navegación en esta entrega.
- El usuario aportó los recursos sin fondo de `docs/diseno/` para el hero y de
  `docs/seis_categorías/` para las seis categorías. Se han inspeccionado las nueve fotografías
  seleccionadas y el logo; el contrato de recursos documenta sus rutas exactas. Se utilizan esos
  archivos existentes y se conservan los originales, sin generar fotografías nuevas.
- El contenido es de presentación y no necesita cuentas ni datos reales de restaurantes,
  productos o pedidos para comprobar su aspecto. No se autoriza introducir datos simulados de
  esas operaciones ni funciones adicionales para dar respuesta a las acciones de portada.
- No se exige una familia tipográfica determinada ni animaciones. La apariencia tipográfica,
  jerarquía y proporciones descritas sí forman parte de la aceptación.
- AP-01 a AP-03 están resueltas para este incremento mediante la respuesta del usuario registrada
  en Clarifications. La planificación puede concretar la presentación del aviso y la obtención
  de recursos visuales sin modificar su mensaje, el comportamiento confirmado ni el alcance.
