# UI Contract: Inicio público de Mikuna

> **Actualización 2026-11-13:** [ampliacion-visual.md](../ampliacion-visual.md) sustituye las
> restricciones iniciales de superficie única, ausencia de footer y navegación futura sin destino.
> Hay cinco vistas públicas por fragmentos y footer compartido. El aviso conserva su contrato para
> registros, acceso a cuentas, cartas y categorías de portada. Validación de navegador pospuesta.

**Versión del contrato:** 1.0.0
**Feature:** `001-inicio-publico`
**Requisitos fuente:** INICIO-001 a INICIO-020 de [spec.md](../spec.md).

## Entrada y superficie pública

La aplicación sirve una única página pública en su raíz. Recargarla directamente no exige cuenta,
ubicación, datos de pago ni backend. No expone API propia ni rutas futuras de negocio.
Logo e Inicio usan el ancla local `#inicio`; los otros accesos no tienen destinos de navegación.
El documento declara español y un título que identifica Mikuna y su servicio de delivery.

La superficie permanente contiene exclusivamente encabezado, hero y categorías. El aviso temporal
y el panel de navegación son estados de esos elementos, no secciones nuevas de contenido.

## Contenido y componentes

| Componente previsto | Responsabilidad y contrato |
| --- | --- |
| HomePage | Componer las tres regiones y compartir el controlador del aviso entre los 13 accesos. |
| SiteHeader | Logo, Inicio activo, cuatro opciones futuras y Pedir ahora; administrar navegación agrupada. |
| HeroSection | Texto exacto, descripción, Empezar a pedir, Crear cuenta y composición de comida. |
| HeroFoodComposition | Fondo melocotón de interfaz, alitas dominantes, pizza circular superior y hamburguesa inferior. |
| CategorySection | Título exacto, seis categorías ordenadas y acciones que solo notifican. |
| ComingSoonNotice | Mostrar un único aviso, anunciarlo sin mover foco y administrar cierre accesible. |
| FoodImage | Mantener proporción y alternativa visible/accesible si falla un recurso. |

La lógica de temporización se encapsula en `useComingSoonNotice`; es estado de interfaz y no lógica
de pedidos. El contenido estático vive separado de los componentes. No se introduce router,
almacén global, biblioteca de iconos ni servicios de datos.

## Textos y acciones observables

| Elemento | Texto u orden | Resultado al activar |
| --- | --- | --- |
| Logo e Inicio | Logo suministrado; Inicio activo | Volver al comienzo de esta página. |
| Navegación futura | Restaurantes; Cómo funciona; Para restaurantes; Sé repartidor | Aviso común y conservación de la vista. |
| Acción del encabezado | Pedir ahora | Aviso común y conservación de la vista. |
| Título del hero | Tu antojo, en camino. | Primera parte índigo y segunda naranja; no es un control. |
| Descripción | Descubre restaurantes cerca de ti y disfruta tus favoritos sin salir de casa. | Contenido de lectura. |
| Acciones del hero | Empezar a pedir; Crear cuenta | Aviso común y conservación de la vista. |
| Título de categorías | ¿Qué se te antoja hoy? | Contenido de lectura. |
| Categorías | Hamburguesas; Pollo; Pizza; Comida peruana; Postres; Bebidas | Aviso común, sin navegación, filtro o selección persistente. |

Los accesos sin destino son botones nativos estilizados, no enlaces vacíos. Se activan con puntero,
tacto, Enter o Espacio. El ancla Inicio conserva semántica de enlace y estado actual accesible.
No hay rutas de login, registro, catálogo o checkout simuladas.

## Aviso temporal

- Texto exacto: **Esta función estará disponible próximamente**.
- Región `role="status"`, `aria-live="polite"`, `aria-atomic="true"`, montada desde el inicio;
  vacía cuando el aviso no está visible. El control de cierre queda fuera del texto anunciado.
- Duración de 5 000 ms activos; pausa por puntero, foco en el aviso o documento oculto.
  Al salir de todos esos estados se consume únicamente el tiempo restante.
- Activar otra vez cualquiera de los 13 controles reinicia duración y actualización accesible.
  Nunca hay más de un aviso visible. Cierre manual y expiración cancelan temporizadores anteriores.
- No se usa un diálogo, `window.alert`, petición de negocio ni redirección. Al aparecer no se
  mueve foco, URL o desplazamiento. El aviso debe situarse en la vista actual sin ocultar el
  disparador ni impedir el acceso a los demás controles; su componente debe adaptarse al espacio
  disponible y verificarse con el panel abierto, en móvil y junto a la última fila de categorías.
- Al cerrar manualmente con foco en el aviso se devuelve al disparador si sigue visible; si era
  una opción de un panel cerrado, se devuelve al control de apertura, sin provocar desplazamiento.

## Navegación agrupada

Bajo 1280 px el encabezado puede agrupar las opciones en un panel. El control de apertura tiene
nombre accesible, relación con el panel y estado expandido. Las opciones ocultas no reciben foco.
Tab y Shift+Tab siguen el orden de lectura normal; Escape cierra y restaura el foco. Activar una
opción cierra el panel y ejecuta la acción elegida. No se utiliza el rol de menú de aplicación
ni una trampa de foco. El panel no puede quedar fuera de pantalla ni impedir su propio cierre.

## Diseño adaptable y colores

| Ancho | Encabezado y hero | Categorías |
| --- | --- | --- |
| Menor de 768 px | Navegación agrupada; texto y acciones antes de las fotografías. | Dos columnas. |
| 768 a 1279 px | Navegación agrupada; distribuir contenido sin colisiones. | Tres columnas. |
| Desde 1280 px | Navegación horizontal y hero en dos columnas. | Seis columnas. |

Ventanas obligatorias: 320 × 568, 390 × 844, 768 × 1024, 1024 × 768, 1440 × 900 y 1536 × 1024 px.
La matriz no se sustituye por un único tamaño denominado móvil. Los controles tienen al menos
44 × 44 px y foco visible. Se permite desplazamiento vertical; no horizontal de la página.

| Token de Tailwind | Valor exacto | Uso |
| --- | --- | --- |
| mikuna-indigo | `#11094C` | Títulos, navegación y bordes. |
| mikuna-orange | `#FF5A1F` | Botones y elementos destacados. |
| mikuna-ivory | `#FDF8F4` | Fondo del hero. |
| mikuna-peach | `#FDE8D8` | Fondo detrás de las alitas. |
| mikuna-gray | `#464B53` | Descripción y texto secundario. |
| mikuna-white | `#FFFFFF` | Encabezado, categorías, texto sobre naranja y bordes fotográficos. |

Los tokens se declaran en `@theme`. Las fotografías transparentes no incluyen fondos de interfaz.
El logo conserva sus colores originales. La composición usa proporciones reservadas y capas
limitadas a su contenedor; no se deforma ni invade texto o acciones. No se exige una fuente remota.

## Fidelidad y comprobaciones

La revisión compara los diez puntos V-01 a V-10 de la spec a 1536 × 1024 px y escala normal.
Se comprueba margen izquierdo entre 5 % y 6 % del ancho, encabezado entre 10 % y 13 % de la altura
y comienzo de categorías entre 60 % y 68 % de la altura. La ampliación al 200 % puede aumentar
alturas y redistribuir contenido; se verifica legibilidad, no esas proporciones de escritorio.

| Grupo de verificación | Requisitos | Escenarios y resultados |
| --- | --- | --- |
| Contenido público y límite | INICIO-001 a INICIO-007, INICIO-009, INICIO-010 | US1, US2, US3.1; SC-001, SC-004 y SC-008. |
| Fotografías, paleta y fidelidad | INICIO-008, INICIO-011, INICIO-012 | V-01 a V-10; SC-002. |
| Adaptación, menú y teclado | INICIO-013 a INICIO-015 | US3.3 y US4; SC-003 y SC-006. |
| Inicio y 13 acciones futuras | INICIO-016 a INICIO-019 | US2.4 y US3.2/US3.4; SC-005. |
| Fallo de imágenes | INICIO-020 | Edge Cases; SC-007. |

Se guarda evidencia de cada ventana, activación de cada acceso, repetición y expiración del aviso,
fallo de imágenes y geometría. La revisión con lector de pantalla y ampliación real es adicional
al ejecutor de navegador. No se declara cumplido un RF del negocio por aprobar esta interfaz.
