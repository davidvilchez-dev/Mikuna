# Research: Inicio público de Mikuna

**Fecha:** 2026-11-12
**Feature:** `001-inicio-publico`
**Fuentes locales:** [spec.md](spec.md), [constitución](../../.specify/memory/constitution.md),
`frontend/package.json`, `frontend/pnpm-lock.yaml`, configuración y archivos actuales del frontend.

## Resultado y consultas realizadas

Las decisiones necesarias para este incremento están resueltas. El usuario confirmó en esta
sesión: **Playwright como única herramienta nueva de pruebas** y **preparar imágenes transparentes
basadas en la referencia**. Posteriormente aportó las imágenes sin fondo del hero y de las seis
categorías: ahora corresponde reutilizarlas, no generarlas. La instalación se hará al implementar.
Las demás decisiones siguientes concretan el diseño técnico permitido por la especificación.
No cambian el alcance ni crean requisitos RF de la propuesta.

La investigación se distribuyó entre tres agentes de solo lectura: frontend y Tailwind;
recursos visuales y aviso; y validación y entrega. No modificaron archivos ni instalaron paquetes.

## R-01. Conservar la configuración existente

**Decisión:** mantener React/React DOM 19.3.1, TypeScript 6.0.3, Vite 8.3.8 y Tailwind CSS con
`@tailwindcss/vite` 4.3.5, según el lock y la instalación inspeccionados. Usar Node 24.14.0 y
pnpm 12.4.2 como versiones reproducibles observadas; conservar el lock y añadir `packageManager`
en la implementación. En PowerShell usar `pnpm.cmd`, porque `pnpm.ps1` está bloqueado localmente.

**Justificación:** Tailwind ya tiene su plugin en `vite.config.ts` y el import en `src/index.css`.
La [integración oficial con Vite](https://tailwindcss.com/docs/installation/using-vite) respalda
esa configuración. No hay motivo para reinstalar Tailwind ni reiniciar el proyecto.

**Alternativas consideradas:** migrar de framework, añadir una segunda solución de estilos o
introducir configuración de Tailwind v3; se descartan por duplicación y riesgo de incompatibilidad.

**Hallazgos locales:** `App.tsx` aún contiene el scaffold e importa tres imágenes que no están
presentes en `src/assets`. `App.css` mantiene estilos ajenos a la portada; `index.html` conserva
`lang="en"` y título `frontend`. Se deben sustituir esas referencias durante la implementación.
No se ejecutó build en esta planificación ni se afirma que el scaffold compile actualmente.

## R-02. Paleta y adaptación con Tailwind

**Decisión:** declarar los seis colores exactos como tokens `@theme` en `src/index.css`, con
nombres `mikuna-indigo`, `mikuna-orange`, `mikuna-ivory`, `mikuna-peach`, `mikuna-gray` y
`mikuna-white`. Utilizar clases completas detectables estáticamente y respetar los usos de la spec.

**Justificación:** las [variables de tema](https://tailwindcss.com/docs/theme) permiten centralizar
los valores; la [detección de clases](https://tailwindcss.com/docs/detecting-classes-in-source-files)
requiere evitar nombres construidos mediante fragmentos dinámicos.

**Decisión de adaptación:** categorías en dos columnas por defecto, tres desde `md` (768 px) y
seis desde `xl` (1280 px). Encabezado horizontal y hero en dos columnas desde `xl`; antes de ese
ancho, navegación desplegable y composición debajo del texto cuando sea necesario. Es una
inferencia de la matriz de aceptación, apoyada en el [modelo adaptable de Tailwind](https://tailwindcss.com/docs/responsive-design).

**Alternativas consideradas:** seis columnas desde `lg`; se descarta porque a 1024 px la spec
exige tres. Tampoco se adopta un ancho fijo de escritorio que cause desbordamiento en móvil.

## R-03. Composición por responsabilidades

**Decisión:** una feature `home`, con contenido editorial tipado, componentes de encabezado,
hero, composición de comida, categorías y aviso. `HomePage` coordina la notificación compartida;
el encabezado administra la apertura de la navegación. El estado es local y efímero.

**Justificación:** permite verificar cada responsabilidad visual sin introducir servicios de
negocio. La descomposición y el propietario común del estado siguen el enfoque de
[Thinking in React](https://react.dev/learn/thinking-in-react).

**Alternativas consideradas:** router, almacén global, API de portada o módulos de restaurantes
simulados; ninguno es necesario para una sola página con los comportamientos confirmados.

## R-04. Recursos aportados por el usuario

**Decisión:** utilizar nueve PNG de comida existentes: las tres fotografías del hero de
`docs/diseno/` y las seis imágenes específicas de `docs/seis_categorías/`. La hamburguesa del hero
y la de categoría son archivos distintos, como ha aportado el usuario. Conservar el logo íntegro.
El [contrato de recursos](contracts/visual-assets.md) relaciona cada fuente con su destino.

**Justificación:** el usuario confirmó la estrategia de transparencia y después proporcionó
los archivos necesarios. Se inspeccionaron visualmente los seis recursos de categorías y las tres
fotografías del hero. Los PNG seleccionados son RGBA y tienen píxeles transparentes en las esquinas;
la revisión final sobre los fondos de la paleta comprobará halos y proporciones. Ya no hay recursos
de comida faltantes para construir el inicio.

**Preparación de implementación:** copiar los recursos a nombres estables bajo
`frontend/src/assets/home/` y el logo a `frontend/src/assets/branding/`, conservando sus bytes.
Registrar dimensiones, origen y SHA-256 en el manifiesto local `home-assets.ts`. Los originales
no se mueven, renombran ni modifican. No se instala una herramienta de imágenes ni se generan
fotografías adicionales como parte de este incremento.

**Alternativas consideradas:** generar los cinco recursos antes faltantes, reutilizar una sola
hamburguesa para hero y categoría o recortar fondos de la captura. Se descartan porque ahora hay
fuentes completas y específicas. Los fondos de la captura difieren de la paleta; los colores de
interfaz se dibujan con estilos y no se toman de esos píxeles.

**Límite:** la presencia de un archivo no acredita por sí sola la fidelidad final. Deben conservarse
alimentos, composición y V-01 a V-10. El archivo adicional `Plato de alitas con salsa-1.png` de la
carpeta de categorías queda como alternativa disponible; no crea una séptima categoría.

## R-05. Aviso temporal accesible

**Decisión:** mensaje exacto “Esta función estará disponible próximamente”; un único aviso no
modal durante **5 000 ms de tiempo activo**. Otra activación reinicia ese tiempo y vuelve a
anunciar el mensaje sin acumular avisos. Se pausa mientras el puntero o el foco estén dentro
del aviso, o el documento esté oculto; se reanuda con el tiempo restante y permite cierre manual.
No cambia URL, desplazamiento ni foco al aparecer. La región de estado permanece montada vacía
cuando no hay mensaje; el botón de cierre es independiente de su contenido anunciado.

**Justificación:** `role="status"`, `aria-live="polite"` y `aria-atomic="true"` permiten anunciar
la actualización sin enfocar el mensaje, según [ARIA22](https://www.w3.org/WAI/WCAG22/Techniques/aria/ARIA22).
La duración de cinco segundos es una decisión de este plan, no un umbral de accesibilidad externo.
Pausa, cierre y nueva activación permiten controlar la lectura, considerando la guía de
[límites de tiempo](https://www.w3.org/WAI/WCAG22/Understanding/timing-adjustable.html).

**Alternativas consideradas:** alerta nativa, diálogo modal, aviso permanente o pila de avisos;
se descartan por bloquear la página, cambiar su contenido permanente o tapar controles.
No se declara conformidad integral con un estándar solo por incorporar estos atributos.

## R-06. Navegación y tipografía

**Decisión:** logo e Inicio usan navegación local al comienzo; los 13 accesos futuros son botones
nativos con apariencia de la referencia y sin destinos de navegación. La navegación pequeña es
un desplegable de sitio, no un widget de menú de aplicación. Debe cerrar con su control y Escape,
restaurar el foco cuando lo requiera el cierre y conservar el desplazamiento. Elegir una opción
lo cierra; cruzar el ancho de escritorio también limpia su estado abierto.

**Justificación:** el [patrón de navegación desplegable de W3C](https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/examples/disclosure-navigation/)
distingue navegación web de los roles de menú de aplicación. Los controles nativos evitan enlaces
ficticios. Se usa una pila tipográfica local sin serifas, empezando por Arial y Helvetica, con
pesos y tamaños ajustados a la referencia; no se añaden fuentes remotas ni otra biblioteca.

**Alternativas consideradas:** enlaces vacíos, rutas futuras, biblioteca de menú o fuentes
externas; no aportan una necesidad de esta portada y añaden dependencias o comportamientos falsos.

## R-07. Playwright confirmado como único ejecutor

**Decisión:** añadir como dependencia de desarrollo la versión exacta `@playwright/test@1.65.0`
durante la implementación. Ejecutar seis proyectos Chromium con las ventanas de la spec,
contra el `dist` construido, mediante `webServer` de Playwright y `vite preview` local.

**Justificación:** el usuario confirmó la herramienta. Sus [metadatos oficiales](https://registry.npmjs.org/@playwright/test/1.65.0)
y la [guía de instalación](https://playwright.dev/docs/intro) sustentan la compatibilidad con
Node 24. Un mismo ejecutor cubre interacción, red, temporizadores, capturas e informes.
El [servidor de pruebas](https://playwright.dev/docs/test-webserver), el
[reloj controlable](https://playwright.dev/docs/clock) y la
[interceptación de red](https://playwright.dev/docs/network) cubren los fallos relevantes sin
esperas reales largas ni servicios externos.

**Alternativas consideradas:** añadir Vitest/Jest o pruebas de constantes aisladas; no se
justifican mientras no exista lógica de negocio. La ampliación real de texto al 200 % y el anuncio
con tecnología de asistencia tienen una comprobación manual adicional. Cambiar densidad de
píxeles no equivale a comprobar zoom de texto.

## R-08. Fidelidad y evidencia

**Decisión:** comprobar contenido, diez puntos visuales, seis colores y tres tolerancias geométricas;
guardar capturas de las seis ventanas. Revisarlas contra la referencia antes de convertirlas en
bases de regresión. Mantener el mismo navegador y entorno para comparaciones posteriores.

**Justificación:** una captura de la propia implementación no prueba por sí misma que esta sea
correcta; las [comparaciones visuales de Playwright](https://playwright.dev/docs/test-snapshots)
dependen del entorno. No se impone un porcentaje arbitrario de diferencia frente a fotografías
aportadas como recursos independientes. No se introducen umbrales de velocidad de portada ausentes de la spec.

**Alternativas consideradas:** aprobar automáticamente la primera captura o medir solo píxeles;
ambas pueden aceptar un diseño incorrecto y no verifican navegación ni accesibilidad.

## R-09. Entrega estática e integración continua

**Decisión:** GitHub Actions ejecutará instalación reproducible, lint, build y Playwright en cada
PR y cambio integrado a la rama principal. Si los controles pasan en la principal, publicará ese
mismo `dist` en S3 e invalidará CloudFront sin aprobación manual del flujo. Los destinos se
suministran como configuración de entorno obligatoria; no se inventan identificadores de AWS.

**Justificación:** conserva RNF-12 y la arquitectura de la propuesta. Se adoptan credenciales
temporales OIDC restringidas al repositorio y rama, de acuerdo con la
[documentación de GitHub para AWS](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws).
La instalación de pnpm en CI seguirá su [guía oficial](https://pnpm.io/continuous-integration#github-actions).
`preview` se limita a validación local, como señala la [guía de despliegue de Vite](https://vite.dev/guide/static-deploy).

**Alternativas consideradas:** despliegue manual, filtros de rutas que omitan cambios o considerar
satisfactorio un despliegue sin destinos configurados; contradicen la obligación de entrega.
Actualmente no hay repositorio Git inicializado ni workflows de entrega en este espacio.
Eso exige preparación de entorno al implementar, no una elección arquitectónica pendiente.

## R-10. Cobertura, seguridad y límites

**Decisión:** el estado de menú/aviso y los datos editoriales son lógica de interfaz; no habrá
reglas de negocio, persistencia ni llamadas a operaciones de la plataforma. Para RNF-11 se registra
“no aplicable a lógica de negocio en este incremento”, conservando el mínimo del 90 % de líneas
y el reporte de ramas para cuando esa lógica exista. No se informa una cobertura ficticia del 100 %.

**Justificación:** cumple la constitución sin confundir pruebas de navegador con cobertura de
negocio. RNF-06 se valida en el destino HTTPS real; el HTTP local no constituye evidencia de su
cumplimiento. CloudFront documenta la [política de comunicación con visitantes](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-https-viewers-to-cloudfront.html).
Los RF y demás RNF del sistema permanecen vigentes para sus incrementos.

**Alternativas consideradas:** iniciar backend/base de datos para mostrar portada, afirmar que
las categorías satisfacen RF-09/RF-10 o medir cobertura de negocio sobre contenido editorial;
se descartan por ampliar el alcance o atribuir cumplimiento sin evidencia.
