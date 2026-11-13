# Mikuna: contexto para continuar

Actualizado: 2026-11-13. Resumen de traspaso; verificar los archivos actuales antes de editar.

## Última corrección: márgenes de las tres secciones informativas

- La captura `Resultado.png` mostró las cabeceras pegadas al borde. En `continuous-home.css`
  se retiró `flex: 1` del contenedor interior y se sustituyó su padre flex por grid con columna
  `minmax(0, 1fr)`. Cómo funciona, Para restaurantes y Sé repartidor respetan ahora el ancho
  de 89 % y el centrado del inicio, también en pantallas amplias, sin el límite de 1600 px.
- La portada, botones y colores se conservaron. `pnpm.cmd build` pasó (54 módulos).
  No se ejecutaron Playwright ni pruebas de navegador; la revisión visual sigue con el usuario.

## Última decisión: quitar el listado de Restaurantes

- El usuario pidió borrar Restaurantes porque recargaba la página. Se retiraron su sección,
  componente, ejemplos y enlaces del menú/footer. Para restaurantes permanece.
- Orden vigente: Inicio, Sobre nosotros, Cómo funciona, Para restaurantes, Sé repartidor y footer.
- Pedir ahora y Empezar a pedir conservan su diseño y el texto blanco; ahora muestran el aviso
  de próxima disponibilidad, sin enlaces a la sección retirada.
- Sé repartidor usa melocotón como fondo, tarjetas blancas y una línea naranja al inicio para
  diferenciarse del cierre marfil de Para restaurantes. Solo tokens de la paleta existente.
- `pnpm.cmd build` pasó (54 módulos). Sin Playwright, instalaciones ni pruebas de navegador.
- La portada se conserva. Las menciones inferiores a Restaurantes describen estados anteriores.

## Estado más reciente: una página continua

- El usuario pidió recorrer todo con scroll desde Inicio. Ya no se intercambian páginas.
  Orden: Inicio, Sobre nosotros (nuevo), Cómo funciona, Para restaurantes, Sé repartidor,
  Restaurantes y footer. Las seis secciones siempre están montadas.
- Menú y footer incluyen Sobre nosotros; Restaurantes está al final del menú y del contenido.
  El encabezado permanece visible y la opción activa acompaña el desplazamiento.
- Cada bloque se ajusta al alto disponible en escritorio desde 1280 × 600 px, descontando
  la altura real del encabezado. En móvil y con texto ampliado, el contenido crece sin recortes.
- El usuario reiteró no tocar la portada ni los botones. `index.css` se conserva; los ajustes
  nuevos están en `features/public-pages/continuous-home.css`. Se restaura el texto blanco de
  Pedir ahora y de la acción primaria de portada, sin modificar dimensiones o fondos.
- Nuevos `AboutSection.tsx` y `useSectionNavigation.ts`; retirado `usePublicPage.ts`.
  Sobre nosotros reutiliza las tres fotos de personas; no incorpora cifras o testimonios.
- Solo los seis colores aprobados. Playwright y pruebas de navegador siguen pospuestos.
- `pnpm.cmd build` pasó tras los últimos ajustes (61 módulos). La revisión visual queda pendiente.
- INICIO-027 a INICIO-030 y T045-T047 registran este ajuste en los documentos del incremento.
  Las menciones inferiores a vistas independientes describen el estado anterior.

## Última ampliación visual (prevalece sobre el resumen inicial inferior)

- Por instrucción del usuario se añadieron las cuatro páginas de `docs/categorias/` y un footer
  común. El menú ya navega a Inicio, Restaurantes, Cómo funciona, Para restaurantes y Sé repartidor
  mediante fragmentos. Pedir ahora y Empezar a pedir abren Restaurantes.
- Código nuevo: `frontend/src/features/public-pages/`. Se conservó el CSS existente de portada,
  incluida la pizza aprobada y el contorno de la hamburguesa. El footer se añade después.
- Fotos originales en `docs/categorias/imagenes_por_categoríaa/` (nombre real encontrado).
  Diez copias intactas con nombres simples y manifiesto en `frontend/src/assets/public-pages/`.
- Restaurantes: solo Ayacucho; sin «Cambiar». Seis ejemplos de la referencia, identificados como
  ejemplos, con búsqueda/filtros locales. Cartas, registros y acceso siguen mostrando el aviso.
- Para restaurantes: tres pasos numerados sin flechas. Cómo funciona: cierre melocotón con bolsa
  y acción de explorar. Sé repartidor: cierre índigo con recorrido ilustrado; diseños distintos.
- Usar exclusivamente los seis colores ya definidos. No añadir colores ni dependencias.
- El usuario reiteró: solo diseño, sin Playwright ni pruebas de navegador. No instalarlos,
  repararlos ni ampliar la suite ahora. La suite original requiere adaptación en la validación final.
- `pnpm.cmd build` pasó tras esta ampliación (TypeScript + Vite, 59 módulos). No se ejecutaron lint,
  tipos e2e, navegador o publicación. La revisión visual del usuario sigue pendiente.
- Alcance vigente y trazabilidad: `specs/001-inicio-publico/ampliacion-visual.md`, INICIO-021 a
  INICIO-026; T039-T043 realizadas, T044 pendiente. El registro inferior de trece avisos y ausencia
  de footer corresponde a la versión inicial, sustituida por esta solicitud.

## Proyecto y fuentes

- Workspace: `C:\BACK\Mikuna`. Plataforma web de delivery multi-restaurante.
- Frontend existente: React, TypeScript, Vite y Tailwind CSS. Backend previsto: Java/Spring Boot.
- Fuente de requisitos: `Propuesta_de_Proyecto.docx` y `.specify/memory/constitution.md`.
- Documentos del incremento: `specs/001-inicio-publico/` (spec, plan, tasks, contratos, quickstart,
  validation y checklist). Conservar los códigos RF/RNF; el inicio nace de una instrucción del
  usuario y usa identificadores locales INICIO, sin atribuirse un RF inexistente.
- Cobertura futura de negocio: al menos 90 % de líneas; informar ramas sin umbral obligatorio.

## Implementado

Inicio público: encabezado con logo y navegación, hero «Tu antojo, en camino.», descripción,
acciones Empezar a pedir / Crear cuenta y alitas con pizza y hamburguesa superpuestas.
Seis categorías: Hamburguesas, Pollo, Pizza, Comida peruana, Postres y Bebidas. El contenido
termina ahí. Autenticación, catálogo, restaurantes, pedidos y pagos son incrementos posteriores.

- Código: `frontend/src/features/home/`. Estilos: `frontend/src/index.css`.
- Logo e Inicio llevan a `#inicio`. Los otros 13 controles muestran «Esta función estará
  disponible próximamente», sin navegar ni ejecutar negocio. Aviso único de 5000 ms activos,
  con cierre, reinicio y pausas por puntero, foco y documento oculto.
- Recursos originales: `docs/diseno/` y `docs/seis_categorías/`. Diez copias íntegras en
  `frontend/src/assets/`, con manifiesto de medidas, procedencia y SHA-256.
- Paleta: índigo #11094C, naranja #FF5A1F, marfil #FDF8F4, melocotón #FDE8D8,
  gris #464B53 y blanco #FFFFFF.

## Ajustes visuales más recientes

- El usuario pidió que todo entre en la primera pantalla de escritorio, como en la referencia.
  Se compactaron alturas y espacios en CSS desde 1280 px de ancho y 600 px de alto. En móvil
  se mantiene desplazamiento vertical para conservar legibilidad.
- Después se amplió ligeramente la composición principal: su ancho de escritorio actual es
  `min(100%, 77dvh, 770px)`.
- Pizza y hamburguesa se redujeron un poco, con menos espacio blanco. La pizza fue aprobada
  por el usuario; conservarla salvo nueva instrucción.
- La hamburguesa ahora tiene un contorno blanco siguiendo su silueta mediante `drop-shadow`,
  con fondo transparente. El grosor se ajusta en `.food-burger` con `--burger-outline: 2.5px`.
  Esta última modificación no tuvo una nueva compilación; la anterior sí pasó.

## Validación y decisiones sobre las pruebas

- Lint y builds anteriores pasaron. Diez recursos coinciden con los originales por SHA-256.
  El render estático de React y la entrega HTTP local de imágenes también pasaron.
- Playwright 1.65.0 está declarado y fijado en el lock; hay 14 escenarios en seis ventanas.
  No pudo instalarse por restricciones de red/política de instalación en este entorno. La
  comprobación de tipos e2e falla por esa dependencia ausente. El navegador alternativo
  también falló por errores GPU. No hay pruebas e2e ni aceptación visual aprobadas.
- El usuario quiere posponer Playwright mientras construimos. No insistir en reinstalarlo,
  recuperar cachés o probar navegadores alternativos durante ajustes de diseño. Retomarlo
  en la validación final. Mientras tanto: revisión visual con el usuario, tipos, lint y build
  cuando sean pertinentes. No marcar como ejecutadas las pruebas pendientes.
- Mantener pruebas de lógica de negocio durante sus incrementos; posponer Playwright no
  elimina el requisito de cobertura ni valida pedidos/pagos futuros.
- `validation.md` detalla los límites de evidencia. `tasks.md`: 30 tareas marcadas y ocho
  pendientes: T017, T021 (integración hecha, prueba pendiente), T026, T027, T032, T035, T036, T037.

## CI y publicación

Existe `.github/workflows/frontend.yml` para validar y publicar el mismo artefacto a S3/CloudFront
con OIDC; no se ejecutó en GitHub. No se ha publicado ni verificado HTTPS público. Al comprobarlo
en esta sesión, no existía `.git` ni había destino AWS/GitHub configurado. Faltan valores reales
de AWS_REGION, AWS_DEPLOY_ROLE_ARN, MIKUNA_SITE_BUCKET, MIKUNA_CLOUDFRONT_DISTRIBUTION_ID y
MIKUNA_PUBLIC_URL. No inventar valores ni convertir una entrega pendiente en completada.

## Trabajar localmente

```powershell
Set-Location 'C:\BACK\Mikuna\frontend'
pnpm.cmd dev
```

Comprobaciones disponibles: `pnpm.cmd lint` y `pnpm.cmd build`. El entorno tenía las dependencias
de aplicación instaladas, pero Playwright pendiente; pnpm puede advertir que node_modules difiere
del lock. No desactivar políticas de instalación para ocultar ese problema.

El usuario prefiere cambios concretos y rápidos, sin ampliar el alcance ni detenerse repetidamente
por herramientas. No hay un nuevo incremento funcional solicitado todavía: esperar su siguiente
instrucción y conservar los ajustes visuales actuales.
