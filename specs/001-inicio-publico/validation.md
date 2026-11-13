# Validación del incremento 001 · Inicio público

## Márgenes laterales de las secciones informativas · 2026-11-13

La captura del usuario mostró que `flex: 1` expandía el contenedor de la cabecera y anulaba el
margen esperado. Se corrigió su padre a grid y se retiró ese crecimiento. Cómo funciona,
Para restaurantes y Sé repartidor comparten el ancho de 89 % del inicio (16 px de margen en
móvil), incluyendo alineación de cabecera, prestaciones y cierres. Sin cambios en portada,
botones, fotos o paleta. `pnpm.cmd build` pasó (54 módulos). Sin pruebas de navegador;
la compilación no acredita aceptación visual del ajuste.

## Simplificación y separación visual · 2026-11-13

Por instrucción del usuario se retiraron el listado de Restaurantes, sus datos de ejemplo y
enlaces. Pedir ahora y Empezar a pedir conservan su diseño y muestran el aviso existente.
Sé repartidor se diferencia de Para restaurantes mediante fondo melocotón, tarjetas blancas y
línea superior naranja. La portada y sus estilos base no cambian.
`pnpm.cmd build` pasó (TypeScript y Vite, 54 módulos). No se instalaron dependencias ni se
ejecutaron Playwright o pruebas de navegador. La revisión visual queda pendiente.
Los apartados inferiores registran los estados previos; el recorrido actual tiene cinco secciones.

## Recorrido continuo y Sobre nosotros · 2026-11-13

Se sustituyeron las vistas independientes por seis secciones continuas: Inicio, Sobre nosotros,
Cómo funciona, Para restaurantes, Sé repartidor y Restaurantes. El footer permanece al final.
El encabezado es persistente, los enlaces son anclas y la navegación señala la sección visible.
Se ajustó el diseño nuevo a la altura disponible desde 1280 × 600 px, con crecimiento natural
en móvil y cuando el contenido necesita más espacio. No se recorta ni oculta el contenido.
La portada conserva sus estilos originales; se restaura explícitamente el blanco de Pedir ahora
y de la acción principal, sin alterar sus tamaños, fondos o bordes.

La comprobación de navegador y la revisión visual siguen pospuestas. No se instalaron paquetes,
no se ejecutó Playwright y no se modificó la suite. `pnpm.cmd build` pasó después de los ajustes
finales: TypeScript y Vite, 61 módulos. Permanece el aviso previo de diferencia entre
`node_modules` y el lock, sin modificar dependencias ni políticas.

## Actualización de diseño · 2026-11-13

Se implementó la [ampliación visual](ampliacion-visual.md): cuatro páginas, navegación activa,
footer común, imágenes por sección y ajustes específicos pedidos por el usuario. Se conservaron
el CSS de la portada y los seis tokens existentes. Restaurantes usa ejemplos de la referencia,
identificados como tales, con búsqueda/filtros locales sin backend ni pedidos.

`pnpm.cmd build` pasó: TypeScript y Vite, 59 módulos. El aviso existente de diferencia entre
`node_modules` y el lock permanece; no se instaló nada ni se alteró el lock.
No se ejecutaron lint, Playwright, tipos e2e, pruebas de navegador ni publicación en esta etapa.
La suite anterior necesita adaptación porque espera ausencia de footer y navegación con avisos.
La revisión visual del usuario y la aceptación final siguen pendientes.

El registro inferior documenta la versión inicial; sus restricciones de una sola página,
sin footer y trece disparadores quedan sustituidas por la ampliación autorizada.

**Fecha:** 2026-11-12. **Estado:** página implementada; aceptación en navegador y entrega
pendientes. No se declara cerrado el incremento completo.

El usuario autorizó continuar con el resto del trabajo si Playwright fallaba y comunicar el
resultado al finalizar. Esa indicación permite avanzar, pero no convierte las pruebas pendientes
en satisfactorias. La constitución y la lista `checklists/requirements.md` se conservaron intactas.

## Evidencia obtenida

| ID | Comprobación ejecutada | Resultado y límite |
| --- | --- | --- |
| E-01 | `pnpm.cmd lint` | Satisfactorio, incluidos componentes, configuración, suite y scripts. |
| E-02 | `pnpm.cmd build` (`tsc -b && vite build`) | Satisfactorio: tipos de aplicación y build de producción, 39 módulos. No comprueba los tipos e2e. |
| E-03 | Render estático de `HomePage` con React y el cargador SSR de Vite | Satisfactorio: título y descripción exactos, seis categorías, trece controles futuros, diez imágenes, dos secciones y estado ARIA inicial. No ejecuta efectos, eventos, temporización ni cálculo de diseño. |
| E-04 | Comparación SHA-256 de originales y copias | Diez de diez coincidentes. Logo, tres fotografías del hero y seis propias de categorías; ningún original modificado. |
| E-05 | Peticiones al preview del build en `127.0.0.1:4173` | HTML del índice y diez PNG compilados responden HTTP 200 con MIME correcto; bytes de imágenes coincidentes con `dist`. No acredita HTTPS público. |
| E-06 | Sintaxis Node de los dos scripts de entrega y diez casos del validador de configuración | Satisfactorios: configuración ficticia válida, cinco entradas ausentes y URL HTTP/con credenciales/con ruta/inválida. No se hicieron llamadas de publicación a AWS. |
| E-07 | `pnpm.cmd test:e2e --project=desktop-1536 --grep US1` | Falló antes de ejecutar escenarios: el comando `playwright` no está instalado. Ningún escenario se declara aprobado o fallido por comportamiento de la página. |
| E-08 | `pnpm.cmd typecheck:e2e` | Falló por `TS2307: Cannot find module '@playwright/test'`, con errores derivados de tipos ausentes. Pendiente repetir con la dependencia instalada. |
| E-09 | Arranque alternativo de Chrome y Chromium headless | Falló por errores GPU del entorno (`GPU process isn't usable`, salidas -1073741790/-1073741515). No se obtuvieron capturas ni validación de las seis ventanas. |
| E-10 | Integridad de documentos de gobierno y comprobación de hooks | Constitución y checklist conservan sus SHA-256 iniciales; `.specify/extensions.yml` no existe, por lo que no hay hooks posteriores que ejecutar. |

Las comprobaciones locales auxiliares y sus salidas están en `frontend/test-results/`, ignorado
por el control de versiones. La suite reproducible está en
[`frontend/tests/e2e/home.spec.ts`](../../frontend/tests/e2e/home.spec.ts): **14 escenarios**
configurados para seis proyectos (84 combinaciones previstas, ninguna ejecutada en esta sesión).
Contiene capturas de diagnóstico de página completa y avisos; no hay capturas base aprobadas.

## Restricción de Playwright y siguiente ejecución

`@playwright/test@1.65.0` está declarado y fijado en el lock. La descarga de dependencias falló
con restricción de red del entorno (`OS 10013`). La instalación offline completa tampoco pudo
resolver la metainformación de política de instalación de Playwright. Se recuperaron de la caché
local las dependencias existentes de la aplicación; no se desactivaron las políticas de pnpm.
Por ello pnpm avisa de la diferencia entre `node_modules` y el lock.

En una sesión con acceso disponible, desde `frontend/`:

```powershell
pnpm.cmd install --frozen-lockfile
pnpm.cmd exec playwright install chromium
pnpm.cmd lint
pnpm.cmd typecheck:e2e
pnpm.cmd build
pnpm.cmd test:e2e
pnpm.cmd test:e2e:report
```

Después deben revisarse los resultados y corregirse los incumplimientos que aparezcan. La
revisión manual sigue incluyendo referencia visual V-01 a V-10, seis ventanas, transparencias,
zoom real de texto al 200 %, teclado y anuncio/repetición con lector de pantalla. Ni el render
estático ni la compilación sustituyen esa aceptación. Ver [quickstart.md](quickstart.md).

## Trazabilidad de requisitos locales

«Implementado» indica código presente y comprobaciones estáticas disponibles; la columna de
pendientes limita explícitamente lo verificado. Los identificadores INICIO provienen de la
instrucción del usuario y no crean códigos RF de la propuesta.

| Requisito | Implementación y evidencia | Aceptación pendiente |
| --- | --- | --- |
| INICIO-001 | Entrada pública sin formularios, backend ni geolocalización; E-02/E-03/E-05 y US1. | Abrir anónimamente en las seis ventanas. |
| INICIO-002 | `HomePage` contiene encabezado, hero y categorías; sin footer ni listados. E-03, US2. | Confirmación en navegador. |
| INICIO-003 | Logo original en `SiteHeader`, proporciones registradas; E-04/E-05. | Aspecto y nitidez visibles. |
| INICIO-004 | Seis opciones en orden, Inicio activo y subrayado naranja; US1/US3. | Apariencia y opciones del menú. |
| INICIO-005 | Título exacto en dos fragmentos, índigo y naranja; E-03, US1/US4. | Dos líneas y jerarquía de escritorio. |
| INICIO-006 | Descripción exacta y token gris; E-03, US1/US4. | Color calculado y legibilidad. |
| INICIO-007 | Dos acciones del hero, flecha primaria y Pedir ahora; US1/US4. | Tratamiento visual y disposición. |
| INICIO-008 | `HeroFoodComposition`, alitas, pizza y hamburguesa independientes; E-04/E-05, US1. | Composición, proporciones y ausencia de colisiones. |
| INICIO-009 | Título de categorías y seis rótulos en orden; E-03, US2. | Lectura en las seis ventanas. |
| INICIO-010 | Seis imágenes específicas, con rótulos separados; E-04/E-05, US2. | Reconocimiento visual y separación. |
| INICIO-011 | Seis tokens exactos en `src/index.css`; US4. | Comprobación de colores calculados. |
| INICIO-012 | CSS ajustado a la referencia, suite con tres tolerancias; US4. | V-01 a V-10, geometría y comparación visual completa. |
| INICIO-013 | Panel nativo, estado accesible, Escape y cierre al cruzar 1280 px; US3. | Teclado, foco, orientación y cambio de ancho. |
| INICIO-014 | Hero apilado bajo 1280 px y categorías 2/3/6; US4. | Seis ventanas sin overflow ni colisiones. |
| INICIO-015 | Textos HTML, imágenes descritas, controles nativos y foco CSS; E-03, US3/US4. | Medidas 44 × 44, teclado, lector y zoom real al 200 %. |
| INICIO-016 | Logo e Inicio enlazan a `#inicio`; US3. | Desplazamiento y foco en navegador. |
| INICIO-017 | Cuatro enlaces futuros conectados al controlador común; E-03, US3. | Activación, mensaje, URL y desplazamiento. |
| INICIO-018 | Tres acciones conectadas al mismo aviso; E-03, US3. | Activación y conservación de vista. |
| INICIO-019 | Seis categorías sin filtros, rutas ni persistencia; E-03, US2/US3. | Activación, aviso y conservación de vista. |
| INICIO-020 | `FoodImage` sustituye una imagen fallida sin retirar textos/controles; US4. | Interceptar imágenes y comprobar uso y distribución. |

## Historias y criterios de éxito

| Historia | Estado |
| --- | --- |
| US1 | Encabezado y hero implementados; contenido y recursos comprobados estáticamente; dos escenarios e2e preparados, pendientes. |
| US2 | Seis categorías integradas al final; orden y recursos comprobados; dos escenarios e2e pendientes. |
| US3 | Navegación y aviso único implementados: 5000 ms activos, reinicio, pausas combinadas y cierre; seis escenarios e2e y revisión asistida pendientes. |
| US4 | Adaptación y alternativa de imágenes implementadas; cuatro escenarios e2e por ventana preparados; aceptación visual pendiente. |

| Criterio | Evidencia disponible | Estado de aceptación |
| --- | --- | --- |
| SC-001 | E-03/E-04/E-05: contenido y recursos completos. | Pendiente verificar disponibilidad en 6/6 ventanas. |
| SC-002 | Tokens, composición CSS y pruebas geométricas escritas. | Pendiente 10/10 puntos visuales, proporciones y colores. |
| SC-003 | Breakpoints, tamaños fluidos y escenarios US4. | Pendiente 6/6 ventanas, orientación y superposición. |
| SC-004 | E-03: exactamente seis categorías, dos secciones y cero formularios/footer. | Comprobación estructural satisfactoria; revisión visible pendiente. |
| SC-005 | E-03: trece accesos; controlador y seis escenarios US3 preparados. | Pendiente interacción, tiempo, pausas, foco y anuncio. |
| SC-006 | Controles nativos, foco visible y menú accesible en código. | Pendiente recorrido real, medidas y tecnología de asistencia. |
| SC-007 | Alternativa de error y prueba de interceptación preparadas. | Pendiente ejecución con imágenes bloqueadas. |
| SC-008 | E-03/E-05: entrada servida, sin formularios ni solicitud de datos. | Pendiente apertura anónima en 6/6 ventanas. |

## Entrega y RNF

[El workflow](../../.github/workflows/frontend.yml) está preparado para PR y cada integración a
`main`. Fija las acciones por SHA, instala desde el lock, ejecuta controles, conserva informes
y despliega el mismo artefacto verificado a S3/CloudFront mediante OIDC. Se realizó revisión
estática; no se ejecutaron GitHub Actions, publicación ni comprobaciones de URL pública.

No existe `.git` en este workspace. No se dispone de repositorio GitHub vinculado, permisos OIDC,
bucket, distribución ni URL reales. Las cinco variables están ausentes en el entorno:
`AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`, `MIKUNA_SITE_BUCKET`,
`MIKUNA_CLOUDFRONT_DISTRIBUTION_ID` y `MIKUNA_PUBLIC_URL`. No se inventaron valores.
T035 y T037 quedan pendientes. El validador de configuración falla explícitamente ante datos
ausentes, y el workflow no publica si falla alguna prueba anterior.

- **RNF-06:** HTTPS público y redirección desde HTTP pendientes de destino y despliegue reales.
- **RNF-07:** el inicio permanece público según la aclaración aprobada; no hay operaciones
  personales o de gestión en este incremento. No se declara completada la seguridad del backend.
- **RNF-11:** no aplicable a cobertura de lógica de negocio: solo hay presentación, contenido
  estático y estado transitorio de interfaz. No existen reglas de pedidos, importes, pagos,
  autorización o concurrencia de negocio, y no se asigna un porcentaje ficticio. Se mantiene
  **al menos 90 % de líneas** y **reporte de ramas sin umbral obligatorio** para incrementos con
  lógica de negocio. La interfaz conserva su obligación de pruebas, actualmente pendientes.
- **RNF-12:** automatización preparada; pendiente evidencia de ejecución CI y entrega efectiva.

Ningún RF de cuentas, restaurantes, catálogo, carrito, pedidos o pagos se declara implementado.
Los códigos RF/RNF de la propuesta conservan su significado y alcance original.

## Trabajo restante

### Ajuste visual solicitado por el usuario

Se redujo la composición principal de comida y se adaptaron alturas, tipografía y espacios
al alto disponible en escritorio (desde 1280 px de ancho y 600 px de alto), para que encabezado,
hero y categorías entren en la primera pantalla. Corresponde a INICIO-008, INICIO-012 e INICIO-014;
no incorpora funcionalidades. Se conserva el crecimiento del contenido en pantallas pequeñas.
`pnpm.cmd build` volvió a pasar después del cambio. La comprobación visual en navegador sigue
pendiente; no se repitieron los intentos de instalación o arranque fallidos.

Las tareas T017, T021 (integración realizada, ejecución US2 pendiente), T026, T027, T032, T035,
T036 y T037 permanecen abiertas. Las tareas de redacción de pruebas indican escenarios escritos;
no acreditan ejecución ni un ciclo rojo/verde, que no pudo observarse por la dependencia ausente.
Las tareas de implementación indican código compilable; la aceptación en navegador conserva
los límites anteriores. Los servidores y navegadores de diagnóstico iniciados por el agente
se cierran al terminar la ejecución.
