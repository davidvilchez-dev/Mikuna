# Data Model: Inicio público

**Feature:** `001-inicio-publico`
**Referencia:** [spec.md](spec.md), [contrato de interfaz](contracts/ui-home.md).

## Límites del modelo

Este incremento contiene datos editoriales y estado de interfaz en memoria. No crea tablas,
migraciones, entidades de restaurantes, usuarios, pedidos o pagos, ni almacenamiento del navegador.
Recargar la página restablece menú cerrado y aviso oculto. Los identificadores siguientes son
locales de presentación, no códigos RF ni identificadores del negocio futuro.

## NavigationItem y ActionItem

| Campo | Tipo conceptual | Regla |
| --- | --- | --- |
| id | Identificador estable | Único dentro de la página; no depende del texto mostrado. |
| label | Texto español | Rótulo exacto de la spec. |
| order | Entero | Orden estable en escritorio y navegación agrupada. |
| behavior | `home` o `coming-soon` | Inicio y logo usan `home`; los otros accesos usan `coming-soon`. |
| appearance | Normal, activo, primario o secundario | Presentación; no cambia el comportamiento. |

El encabezado tiene Inicio y cuatro accesos futuros, más Pedir ahora como acción destacada.
El hero tiene Empezar a pedir y Crear cuenta. Hay **siete controles futuros** entre esos grupos.
Logo e Inicio son dos accesos al comienzo y no muestran aviso.
No se define un campo de ruta para accesos futuros: su comportamiento no es navegación.

## CategoryItem

| Campo | Tipo conceptual | Regla |
| --- | --- | --- |
| id | Identificador estable | `hamburguesas`, `pollo`, `pizza`, `comida-peruana`, `postres`, `bebidas`. |
| label | Texto | Hamburguesas, Pollo, Pizza, Comida peruana, Postres y Bebidas. |
| imageId | Referencia a VisualAsset | Debe resolver un recurso de comida adecuado. |
| order | Entero de 1 a 6 | Exactamente seis registros, sin duplicados. |
| behavior | `coming-soon` | No selecciona filtros, no persiste selección y no navega. |

Los seis CategoryItem completan los **13 accesos futuros**. Cada categoría utiliza su fotografía
específica de `docs/seis_categorías/`, incluida su propia hamburguesa. Pizza utiliza una porción,
distinta de la pizza entera del hero.

## VisualAsset

| Campo | Tipo conceptual | Regla |
| --- | --- | --- |
| id | Identificador | Nombre estable del recurso, según el contrato de recursos. |
| source | Recurso local importado | URL resuelta por el build; no URL remota de demostración. |
| subject | Texto | Alimento o logo que representa. |
| alternativeText | Texto accesible | Descripción pertinente; sin nombres de archivo ni texto promocional incrustado. |
| width y height | Dimensiones naturales | Se registran al copiar el archivo y permiten reservar su proporción. |
| provenance | Origen | Ruta original de `docs/diseno/` o `docs/seis_categorías/`, según el contrato. |
| sha256 | Huella de integridad | SHA-256 de 64 caracteres hexadecimales; coincide con el original. |

El manifiesto `home-assets.ts` relaciona tres imágenes del hero y seis imágenes de categorías con
nueve archivos de comida distintos. El logo es un décimo recurso y conserva los bytes de su original.
Las reglas de transparencia y revisión están en [visual-assets.md](contracts/visual-assets.md).

## MenuState

**Estado inicial:** `closed`. Solo puede ser `closed` u `open`.

| Evento | Resultado |
| --- | --- |
| Activar el control de navegación agrupada | Alternar abierto/cerrado y actualizar `aria-expanded`. |
| Escape desde el panel abierto | Cerrar y devolver foco al control de apertura sin desplazar la página. |
| Activar una opción del panel | Ejecutar su comportamiento y cerrar; si se retira el control enfocado, restaurar foco al de apertura. |
| Pasar a escritorio desde 1280 px | Cerrar el panel agrupado y retirar sus opciones ocultas del recorrido de foco. |
| Recargar o desmontar | Restablecer cerrado y retirar listeners asociados. |

El menú no almacena sesión ni representa un rol del usuario. No se atrapa el foco como en un diálogo.

## NoticeState

| Campo | Tipo conceptual | Regla |
| --- | --- | --- |
| visibility | Oculto o visible | Inicialmente oculto; solo existe un aviso visual. |
| message | Texto constante | “Esta función estará disponible próximamente”. |
| revision | Contador de activaciones | Cada nueva activación provoca una actualización accesible efectiva. |
| remainingMs | Tiempo activo restante | Comienza en 5 000; nunca es negativo. |
| pauseReasons | Conjunto de motivos | Puntero sobre aviso, foco en aviso o documento oculto. |
| lastTrigger | Referencia efímera | Permite gestionar foco después de cierre manual; no se persiste ni transmite. |

| Estado/evento | Transición |
| --- | --- |
| Oculto + acceso futuro | Mostrar, actualizar anuncio e iniciar 5 000 ms. |
| Visible + otro acceso o el mismo | Mantener un aviso, incrementar revisión y reiniciar 5 000 ms. |
| Aparece un motivo de pausa | Detener descuento y conservar el tiempo restante. |
| Desaparecen todos los motivos | Reanudar el tiempo restante. |
| Tiempo activo llega a cero | Ocultar y vaciar el contenido anunciado, manteniendo montada la región de estado. |
| Cierre manual | Ocultar, cancelar temporizador y devolver foco si el botón de cierre lo tenía. |
| Desmontaje | Cancelar temporizadores y listeners; ninguna actualización tardía de estado. |

La aparición no modifica URL, fragmento, foco ni desplazamiento. El cierre del menú puede restaurar
el foco según su propia regla; el aviso nunca lo toma. No hay cola ni historial de mensajes.

## ImageState

Cada recurso visible distingue carga normal y fallo. En fallo conserva un marco con dimensiones
reservadas y una alternativa accesible; los textos y los controles no dependen de cargar la imagen.
No se reintentan operaciones remotas ni se alteran categorías a consecuencia del fallo.

## Invariantes verificables

- Se conserva el orden de seis categorías y los rótulos exactos en todas las ventanas.
- Solo logo e Inicio tienen comportamiento de volver al comienzo.
- Los 13 accesos futuros comparten el mismo aviso y no producen efectos de negocio.
- Un máximo de un aviso; activaciones repetidas no forman pilas ni temporizadores activos antiguos.
- Ningún estado editorial o de interfaz exige autenticación o persistencia.
