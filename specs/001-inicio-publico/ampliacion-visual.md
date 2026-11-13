# Ampliación visual del inicio público · 2026-11-13

## Último ajuste: retirar Restaurantes y distinguir los bloques de colaboración

El usuario retiró la sección de listado de Restaurantes por exceso de contenido. Se eliminan su
componente, ejemplos, buscador, filtros y enlaces del menú/footer. Se conservan los archivos de
imagen aportados. Orden vigente: Inicio, Sobre nosotros, Cómo funciona, Para restaurantes,
Sé repartidor y footer. Esta decisión sustituye INICIO-022 y la parte de INICIO-027 que situaba
el listado al final; no elimina la sección Para restaurantes.

Pedir ahora y Empezar a pedir vuelven al aviso de próxima disponibilidad; mantienen sus colores,
texto blanco, dimensiones y forma. No quedan enlaces a `#restaurantes`.
Sé repartidor usa fondo melocotón, tarjetas blancas y una división superior naranja, de los tokens
existentes, para separarse del cierre marfil de Para restaurantes. Se conservan la portada,
las cinco secciones continuas y el ajuste de altura. Aceptación visual de este ajuste pendiente.

## Ajuste posterior: recorrido continuo y Sobre nosotros

La instrucción posterior del usuario sustituye la navegación entre vistas independientes por
una sola página recorrible con scroll. Orden: Inicio, Sobre nosotros, Cómo funciona,
Para restaurantes, Sé repartidor, Restaurantes y footer. El menú usa anclas reales, permanece
visible y señala la sección visible; no desmonta las otras secciones al navegar.

| ID local | Cambio solicitado e implementado |
| --- | --- |
| INICIO-027 | Todo el contenido accesible al hacer scroll desde Inicio; Restaurantes al final. |
| INICIO-028 | Sobre nosotros, con presentación de Mikuna, collage de las fotos aportadas y tres ideas sobre su propuesta en Ayacucho; enlace en menú y footer. |
| INICIO-029 | Desde 1280 px de ancho y 600 px de alto, cada sección ocupa el alto de la ventana menos la altura real del encabezado. Las secciones nuevas compactan fotos, espacios y tipografía según el alto. En móvil, ventanas muy bajas y ampliación de texto, el contenido puede crecer para conservar legibilidad. |
| INICIO-030 | Mantener portada y botones existentes. Restaurar blanco en el texto del enlace Pedir ahora y del botón principal de portada; sin cambiar sus dimensiones, bordes ni fondos. |

Implementación adicional: `continuous-home.css`, `AboutSection.tsx` y `useSectionNavigation.ts`.
Se retiró el hook que intercambiaba páginas. Los encabezados e identificadores de las secciones
se adaptaron al documento único. Se mantienen exclusivamente los seis colores definidos.
No se realizaron pruebas de navegador; el usuario revisará la composición.

El resto del documento registra la ampliación anterior; las menciones a páginas independientes
quedan sustituidas por este recorrido continuo.

**Origen:** instrucciones del usuario de esta sesión y cuatro referencias de `docs/categorias/`.
Esta ampliación sustituye las restricciones originales de una sola pantalla, ausencia de footer
y aviso para todas las opciones de navegación. No modifica la constitución ni los RF/RNF.
El usuario pidió concentrarse en diseño y posponer Playwright y pruebas de navegador.

## Alcance y aceptación visual pendiente

| ID local | Diseño implementado | Referencia o decisión |
| --- | --- | --- |
| INICIO-021 | Navegación pública a Inicio, Restaurantes, Cómo funciona, Para restaurantes y Sé repartidor; estado activo y footer compartido. | Cuatro PNG de `docs/categorias/`; footer solicitado expresamente. |
| INICIO-022 | Restaurantes en Ayacucho, ubicación fija sin «Cambiar», buscador y categorías sobre seis ejemplos visuales. | `Descubre restaurantes en Ayacucho-1.png`; alcance solo Ayacucho. |
| INICIO-023 | Cómo funciona: foto de la clienta, cuatro pasos y cierre melocotón con icono de bolsa e invitación a explorar. | `Mikuna_ de tu antojo a tu puerta-2.png`; mejorar el cierre. |
| INICIO-024 | Para restaurantes: foto de la chef, tres prestaciones y tres pasos numerados sin flechas. | `Mikuna para restaurantes-3.png`; quitar flechas entre pasos. |
| INICIO-025 | Sé repartidor: foto del repartidor, tres prestaciones y cierre índigo con recorrido ilustrado por Ayacucho. | `Tu próxima entrega empieza aquí-4.png`; cierre distinto del de Cómo funciona. |
| INICIO-026 | Imágenes en su sección, centradas y con proporción reservada; originales intactos; seis colores de marca. | Imágenes del usuario y paleta ya aprobada. |

El inicio conserva su composición, escala de pizza y contorno de hamburguesa. El footer queda
después de la primera pantalla en escritorio desde 1280 px y 600 px de alto; el hero y las
categorías mantienen sus reglas existentes. Las páginas nuevas permiten desplazamiento vertical.

## Implementación y límites

- `frontend/src/features/public-pages/`: páginas, contenido, componentes y CSS independiente.
- Navegación por fragmentos `#inicio`, `#restaurantes`, `#como-funciona`, `#para-restaurantes`,
  `#se-repartidor`; sin dependencias nuevas, backend o configuración de rutas del servidor.
- `HomePage` comparte encabezado, footer y aviso. Pedir ahora y Empezar a pedir abren Restaurantes.
  Las altas, acceso a cuenta y cartas muestran el aviso existente.
- Buscador y filtros actúan solo sobre los seis ejemplos de la referencia. La vista los identifica
  como ejemplos. No acredita RF-09/RF-10, afiliación real ni disponibilidad.
- Las tres fotografías humanas corresponden a Cómo funciona, Para restaurantes y Sé repartidor.
  Las seis de platos se asignan a los seis ejemplos de restaurantes según la referencia.
- Diez copias en `frontend/src/assets/public-pages/`; `manifest.json` conserva nombre original,
  dimensiones y SHA-256. La imagen de costillas número 10 es una alternativa; no crea un séptimo
  restaurante ni duplica una tarjeta.
- Colores exclusivos: índigo `#11094C`, naranja `#FF5A1F`, marfil `#FDF8F4`, melocotón `#FDE8D8`,
  gris `#464B53` y blanco `#FFFFFF`, mediante los tokens existentes.

## Verificación diferida

No instalar, reparar, ejecutar ni ampliar Playwright en esta etapa de diseño. No se afirma
aceptación visual ni funcional en navegador. La suite anterior contiene expectativas del alcance
original (sin footer, cuatro opciones con aviso y trece disparadores) y deberá actualizarse al
retomar la validación final. Las tareas pendientes de navegador y publicación permanecen abiertas.
