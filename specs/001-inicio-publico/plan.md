# Implementation Plan: Inicio público de Mikuna

> **Ampliación 2026-11-13:** diseñar las cuatro páginas de `docs/categorias/` y un footer
> compartido según [ampliacion-visual.md](ampliacion-visual.md). Se reutilizan React, los seis
> tokens, el aviso y el encabezado; navegación por fragmentos sin dependencias nuevas.
> `features/public-pages/` contiene vistas y estilos; `assets/public-pages/`, las fotos.
> La portada conserva sus ajustes. Playwright y aceptación de navegador quedan pospuestos.

**Branch**: `001-inicio-publico` (contexto de feature; no hay repositorio Git inicializado)
**Date**: 2026-11-12
**Spec**: [spec.md](spec.md)

**Input**: Especificación aclarada en `specs/001-inicio-publico/spec.md`.

## Summary

Implementar una sola página pública con el encabezado, el hero y las seis categorías definidos en
la referencia. Conservar React, TypeScript, Vite y Tailwind ya configurados; separar el contenido
editorial de los componentes. Utilizar las nueve fotografías transparentes aportadas por el usuario:
tres de `docs/diseno/` para el hero y seis de `docs/seis_categorías/` para las categorías. Conservar
el logo original y dibujar los fondos con los seis colores exactos.

Logo e Inicio vuelven al comienzo; los 13 accesos a funciones futuras muestran el aviso confirmado,
sin rutas ni operaciones de negocio. La verificación utiliza Playwright, confirmado por el usuario,
y revisión visual/accesible. La entrega prevista es estática en S3 y CloudFront mediante GitHub
Actions. La ejecución actual produce diseño y documentación; no implementa la página, imágenes,
pruebas, workflow o infraestructura.

## Technical Context

**Language/Version**: TypeScript 6.0.3; HTML/CSS; Node 24.14.0 y pnpm 12.4.2 observados localmente.

**Primary Dependencies**: React/React DOM 19.3.1; Vite 8.3.8; Tailwind CSS y `@tailwindcss/vite`
4.3.5, según el lock. Conservar el plugin y el import existentes; no reinstalar Tailwind.

**Storage**: Archivos estáticos locales incluidos en el build; estado efímero de menú y aviso.
Sin base de datos, caché de negocio, sesión o persistencia en el navegador para esta portada.

**Testing**: Playwright Test 1.65.0 como única dependencia nueva de pruebas, confirmada el
2026-11-12. Seis proyectos Chromium contra el artefacto construido; TypeScript/ESLint existentes
más revisión manual de fidelidad, ampliación real de texto y anuncio accesible.

**Target Platform**: Web adaptable; Windows local y Linux en CI. Navegadores modernos compatibles
con Tailwind v4. La matriz mínima automatizada usa Chromium; sus ventanas no equivalen a certificar
Safari móvil o dispositivos físicos. La compatibilidad base se referencia en
[research.md](research.md) y la [documentación oficial](https://tailwindcss.com/docs/compatibility).

**Project Type**: Frontend web estático dentro de un proyecto frontend/backend. El backend
Java/Spring Boot existente queda fuera de este incremento y no es necesario para abrir la portada.

**Performance Goals**: Interacciones locales sin navegación, petición de negocio o recarga;
estabilidad de distribución y ausencia de desbordamiento en las seis ventanas. No se inventa un
SLA de carga de portada. RNF-01 conserva su objetivo de consultas con 1 000 usuarios para los
incrementos que habiliten esas operaciones; esta página no constituye su evidencia.

**Constraints**: Solo encabezado, hero y categorías; seis colores exactos; nueve PNG de comida
ya disponibles y logo intacto; aviso único de 5 000 ms activos; controles de
44 × 44 px como mínimo; acceso público; cero RF de negocio declarados completados.

**Scale/Scope**: Una página, cuatro historias, 20 requisitos locales, ocho resultados de éxito,
seis categorías, 13 accesos con aviso y seis ventanas de aceptación: 320 × 568, 390 × 844,
768 × 1024, 1024 × 768, 1440 × 900 y 1536 × 1024 px.

## Constitution Check

La revisión inicial confirmó el alcance y los seis principios antes de adoptar soluciones.
La fase 0 resolvió las dos elecciones que requerían consulta: Playwright y recursos transparentes.
La revisión posterior comprueba este diseño; un resultado favorable no equivale a haber ejecutado
las pruebas ni a declarar cumplidos los RNF en producción.

| Principio o control | Revisión inicial | Después del diseño de fase 1 |
| --- | --- | --- |
| I. Modularidad | Conforme: una feature visual acotada, sin alterar módulos de negocio. | Conforme: componentes y contenido separados bajo `features/home`, con contratos explícitos. |
| II. Separación de responsabilidades | Conforme: presentación y estado de interfaz; sin reglas de pedidos o pagos. | Conforme: HomePage coordina; componentes presentan; datos editoriales y recursos son independientes. |
| III. Seguridad | Conforme: entrada pública y ausencia de datos personales, pago o secretos. | Conforme en diseño: controles sin rutas falsas, recursos locales, despliegue HTTPS y credenciales temporales en CI. |
| IV. Concurrencia de negocio | No aplica: no hay pedidos, cobros o asignaciones. | Sin ampliación: aviso único y limpieza de temporizadores; no se atribuye cumplimiento de RNF-05. |
| V. Pruebas y cobertura | Conforme: plan de pruebas requerido; sin lógica de negocio en el alcance. | Conforme: Playwright confirmado, revisión manual e integración continua; RNF-11 no aplicable a negocio en esta portada, sin cobertura ficticia. |
| VI. Trazabilidad | Conforme: origen en instrucción del usuario; requisitos INICIO locales. | Conforme: contratos y matriz de verificación cubren INICIO-001 a INICIO-020 y SC-001 a SC-008. |
| Consultas técnicas | No se adoptó una herramienta nueva sin consultar. | Resueltas y registradas en research.md; no hay elecciones funcionales abiertas para este incremento. |
| Alcance incremental | Inicio primero; autenticación, catálogo, pedidos y pagos posteriores. | Conservado: sin restaurante ficticio, formulario de acceso, footer o sección Cómo funciona. |

**Resultado de los gates de diseño: conforme, sin excepciones constitucionales.** Las condiciones
de entorno de despliegue deben satisfacerse antes de integrar una entrega; su ausencia no permite
marcar RNF-06/RNF-12 como verificados ni saltar el despliegue silenciosamente.

## Project Structure

### Documentation (this feature)

```text
specs/001-inicio-publico/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── tasks.md                 # Desglose posterior al plan
├── contracts/
│   ├── ui-home.md
│   └── visual-assets.md
└── checklists/
    └── requirements.md
```

`tasks.md` corresponde al desglose de implementación de `$speckit-tasks`, posterior al plan.

### Source Code (repository root)

La siguiente estructura es **propuesta para la implementación**, no archivos creados por este
comando. Los archivos existentes se reutilizan; los nuevos se materializarán a partir de tareas.

```text
frontend/
├── index.html                         # Idioma y título de Mikuna
├── package.json                       # Playwright, scripts y versión de pnpm
├── pnpm-lock.yaml                     # Instalación reproducible
├── vite.config.ts                     # Conservar integración actual de Tailwind
├── playwright.config.ts               # Seis ventanas; servidor del build
├── tsconfig.e2e.json                  # Comprobación de tipos de configuración y pruebas
├── src/
│   ├── main.tsx
│   ├── App.tsx                        # Entrada de HomePage
│   ├── index.css                      # Tailwind, seis tokens y estilos globales mínimos
│   ├── assets/
│   │   ├── branding/logo-mikuna.png    # Copia íntegra del original
│   │   └── home/                      # Nueve recursos transparentes del contrato
│   └── features/home/
│       ├── HomePage.tsx
│       ├── home-content.ts
│       ├── home-assets.ts              # Recursos, dimensiones, origen y huellas
│       ├── home-types.ts
│       ├── useComingSoonNotice.ts
│       └── components/
│           ├── SiteHeader.tsx
│           ├── HeroSection.tsx
│           ├── HeroFoodComposition.tsx
│           ├── CategorySection.tsx
│           ├── ComingSoonNotice.tsx
│           └── FoodImage.tsx
└── tests/e2e/home.spec.ts
.github/workflows/frontend.yml         # Validación y entrega automática futuras
```

**Structure Decision**: extender el frontend existente con una sola feature. Retirar las
importaciones de imágenes inexistentes y el contenido del scaffold al sustituir `App.tsx`;
retirar los estilos antiguos de `App.css` y su importación para evitar colisiones. No se crean
rutas de negocio, servicios HTTP ni una abstracción global de componentes sin uso real.

## Design Decisions

- **Estilos:** seis tokens `@theme` y clases completas de Tailwind. Dos columnas de categorías
  base, tres desde 768 px y seis desde 1280 px; composición y navegación horizontal desde 1280 px.
  Pila tipográfica local sin serifas, sin nuevos paquetes de fuentes o iconos.
- **Recursos:** copiar las tres fotografías del hero y las seis fotografías específicas de las
  categorías según el contrato, sin generar imágenes nuevas ni sustituir la hamburguesa de
  categoría por la del hero. Mantener sus originales y registrar integridad en `home-assets.ts`.
  El fondo melocotón y los marcos se dibujan en la interfaz.
- **Estado:** modelo efímero de [data-model.md](data-model.md). Un aviso, cinco segundos activos,
  pausa, cierre, nueva activación y limpieza; ningún estado se persiste o envía.
- **Interfaz pública:** [contracts/ui-home.md](contracts/ui-home.md) fija semántica, mensajes,
  navegación y conducta del menú. [contracts/visual-assets.md](contracts/visual-assets.md)
  fija imágenes, procedencia y preparación. No se requiere un contrato REST para esta página.

## Validation and Delivery

### Verificación del artefacto

Playwright arranca `vite preview` sobre el `dist` ya construido en `127.0.0.1:4173`, con puerto
estricto. Los proyectos se llaman `mobile-320`, `mobile-390`, `tablet-768`, `tablet-1024`,
`desktop-1440` y `desktop-1536`. La configuración no considera `preview` un servidor de producción.

La suite verifica textos y orden, 13 avisos, URL y desplazamiento conservados, aviso repetido y
expiración con reloj controlado, menú y teclado, imágenes fallidas, tamaños activables, overflow,
colores calculados, geometría y capturas. Los tipos de pruebas y configuración se comprueban con
`tsconfig.e2e.json`; el build actual solo cubre sus proyectos configurados.

Las capturas iniciales se revisan contra la referencia y V-01 a V-10 antes de aprobarlas como
bases. No se aceptan automáticamente por haberlas generado. La ampliación real al 200 % y la
lectura del aviso con tecnología de asistencia tienen una comprobación manual documentada.
Se guardan informes, capturas y trazas de fallos vinculados a la revisión evaluada, sin versionar
salidas de ejecución como si fueran código. La guía está en [quickstart.md](quickstart.md).

### Integración y despliegue

- PR y cada cambio integrado en la rama principal prevista `main`: instalación con lock, lint,
  comprobación de tipos de pruebas, build y Playwright; publicar evidencia incluso si falla.
- Si pasan en `main`, desplegar ese mismo `dist` en el bucket estático de S3 e invalidar CloudFront.
  No filtrar por rutas, no recompilar otro artefacto para desplegar y no exigir aprobación manual
  del job de entrega. Una falla de control impide publicar.
- Usar OIDC y un rol limitado al repositorio, rama y recursos del sitio. Las acciones de CI se
  fijan a revisiones verificadas al escribir el workflow; no se guardan claves AWS en el código.
- El entorno requiere `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`, `MIKUNA_SITE_BUCKET`,
  `MIKUNA_CLOUDFRONT_DISTRIBUTION_ID` y `MIKUNA_PUBLIC_URL`. Son entradas de configuración reales,
  no valores que este plan pueda inventar. Un valor ausente hace fallar el job de despliegue.
- Antes de integrar la entrega deben existir el repositorio Git/GitHub, sus permisos y el destino
  S3/CloudFront con dominio y certificado configurados según la propuesta. Se comprueba HTTPS,
  certificado válido y redirección HTTP en el sitio entregado. No se aprovisiona ni publica aquí.

Esta entrega automatizada corresponde al frontend del incremento. Los controles y despliegue del
backend se incorporarán antes de integrar su funcionalidad; no se declara completo el pipeline
ni los requisitos no funcionales de toda la plataforma a partir de esta portada.

### Aplicación de RNF-11

El alcance no contiene lógica de negocio. Registrar explícitamente esa justificación y verificar
la interfaz con los controles anteriores; no producir un porcentaje de negocio del 100 % a partir
de datos editoriales. El objetivo del 90 % de líneas y reporte de ramas sin umbral permanece vigente.
Si se propusiera introducir negocio, se debe revisar alcance y medición antes de incorporarlo.

## Complexity Tracking

No se requieren excepciones. Se conserva una página y una feature, dos estados locales y un solo
ejecutor de pruebas. No se añaden microservicios, API, router, almacén global o biblioteca visual.
