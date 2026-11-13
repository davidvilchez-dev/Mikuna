# Mikuna · Inicio público

**Diseño vigente:** una página continua con Inicio, Sobre nosotros, Cómo funciona,
Para restaurantes y Sé repartidor, seguida del footer. El listado de Restaurantes se retiró
por instrucción del usuario; Pedir ahora y Empezar a pedir muestran el aviso existente. El menú es
persistente y usa anclas a cada sección. En escritorio desde 1280 × 600 px los bloques se
ajustan a la altura disponible; en móvil crecen para conservar legibilidad. Se mantienen
la portada, los botones y los seis colores originales. Sé repartidor usa fondo melocotón y tarjetas
blancas para distinguirse de Para restaurantes. Playwright continúa pospuesto.
Las descripciones de vistas independientes que siguen corresponden al diseño anterior.

Diseño público de la plataforma: inicio, Restaurantes en Ayacucho, Cómo funciona, Para restaurantes
y Sé repartidor, con navegación y footer compartidos. Utiliza React, TypeScript y Tailwind CSS,
las fotos aportadas por el usuario y exclusivamente los seis colores de marca. La portada conserva
sus ajustes visuales. No completa ningún RF de negocio.

Las páginas usan fragmentos (`#restaurantes`, `#como-funciona`, `#para-restaurantes`,
`#se-repartidor` y `#inicio`), sin dependencias nuevas. Pedir ahora y Empezar a pedir abren
Restaurantes. Las cuentas, altas, cartas y categorías de portada muestran el aviso existente.
La vista de restaurantes contiene seis ejemplos visuales, con buscador y filtros locales.

**Etapa actual: diseño.** Playwright queda pospuesto por instrucción del usuario. No instalarlo,
repararlo ni ejecutarlo durante esta etapa. La suite histórica descrita debajo necesita adaptación
al nuevo alcance antes de la validación final. El build actualizado pasó el 2026-11-13.
Ver [ampliacion-visual.md](../specs/001-inicio-publico/ampliacion-visual.md).

## Ejecutar localmente

Versiones: Node **24.14.0**, pnpm **12.4.2**, React **19.3.1**, TypeScript **6.0.3**,
Vite **8.3.8**, Tailwind **4.3.5** y Playwright **1.65.0**. El lock fija las dependencias.

```powershell
Set-Location 'C:\BACK\Mikuna\frontend'
pnpm.cmd install --frozen-lockfile
pnpm.cmd dev
```

Abrir la dirección que muestre Vite. La portada no necesita backend ni credenciales.
Para revisar el artefacto de producción:

```powershell
pnpm.cmd build
pnpm.cmd preview --host 127.0.0.1 --port 4173 --strictPort
```

Abrir `http://127.0.0.1:4173/`. Este servidor local no acredita HTTPS de producción.

## Verificar

```powershell
pnpm.cmd lint
pnpm.cmd typecheck:e2e
pnpm.cmd build
pnpm.cmd exec playwright install chromium
pnpm.cmd test:e2e
pnpm.cmd test:e2e:report
```

La suite utiliza el mismo `dist` del build y seis proyectos Chromium:

| Proyecto | Ventana |
| --- | --- |
| mobile-320 | 320 × 568 |
| mobile-390 | 390 × 844 |
| tablet-768 | 768 × 1024 |
| tablet-1024 | 1024 × 768 |
| desktop-1440 | 1440 × 900 |
| desktop-1536 | 1536 × 1024 |

Para una sola ventana: `pnpm.cmd test:e2e --project=desktop-1536`.
Las capturas de diagnóstico, trazas e informes se guardan en directorios ignorados.
Las primeras capturas requieren revisión contra la referencia; no hay bases visuales aprobadas.
La ampliación real del texto al 200 % y el anuncio con lector de pantalla requieren revisión manual.

**Estado de esta ejecución:** lint, tipos de aplicación, build y comprobación estructural de
React satisfactorios. Playwright está declarado y fijado en el lock, pero su instalación no
pudo completarse en el entorno restringido. El comando de pruebas y los tipos e2e fallaron por
esa dependencia ausente. Tampoco pudo arrancar el navegador alternativo por errores GPU del
entorno. Las pruebas de interacción, capturas y aceptación visual quedan pendientes.
Ver [el registro de validación](../specs/001-inicio-publico/validation.md).

`pnpm-workspace.yaml` permite usar el pnpm ya instalado (`pmOnFail: ignore`) y avisa si las
dependencias están desincronizadas (`verifyDepsBeforeRun: warn`), en vez de intentar instalarlas
al lanzar cada script. No relaja las comprobaciones de una instalación explícita. Una vez
disponible la red, ejecutar `pnpm.cmd install --frozen-lockfile` antes de repetir las pruebas.

## Organización y recursos

- `src/features/home/`: contenido tipado, manifiesto de recursos, componentes y controlador del aviso.
- `src/index.css`: los seis tokens de marca y adaptación a móvil, tableta y escritorio.
- `src/assets/branding/` y `src/assets/home/`: diez copias íntegras de los originales.
- `tests/e2e/home.spec.ts`: escenarios US1–US4.
- `scripts/`: validación de configuración de entrega y verificación de HTTPS.

El [contrato de recursos](../specs/001-inicio-publico/contracts/visual-assets.md) registra las
rutas originales de `docs/diseno/` y `docs/seis_categorías/`. `home-assets.ts` conserva medidas,
descripciones y SHA-256. Las tres imágenes del hero y las seis imágenes de categorías son
independientes; el logo conserva sus colores y proporciones. Los originales no se modifican.

| Color | Hexadecimal | Uso |
| --- | --- | --- |
| Índigo | `#11094C` | Títulos, navegación y bordes |
| Naranja | `#FF5A1F` | Acciones y destacados |
| Marfil | `#FDF8F4` | Sección principal |
| Melocotón | `#FDE8D8` | Fondo de las alitas |
| Gris | `#464B53` | Descripción |
| Blanco | `#FFFFFF` | Encabezado, categorías y detalles de contraste |

## Integración y entrega

[El workflow](../.github/workflows/frontend.yml) valida PR y cada integración a `main` sin
filtros de rutas: instalación con lock, lint, tipos e2e, build y pruebas Chromium. Conserva
informes y entrega el mismo `dist` verificado a S3, invalida CloudFront y comprueba la URL.
Usa acciones fijadas a SHA y credenciales temporales OIDC. No se ha ejecutado en GitHub.

Se deben configurar estas cinco variables del repositorio, con valores reales:

| Variable | Valor requerido |
| --- | --- |
| `AWS_REGION` | Región del destino |
| `AWS_DEPLOY_ROLE_ARN` | ARN del rol asumible por GitHub OIDC |
| `MIKUNA_SITE_BUCKET` | Bucket S3 existente para el sitio |
| `MIKUNA_CLOUDFRONT_DISTRIBUTION_ID` | Distribución CloudFront existente |
| `MIKUNA_PUBLIC_URL` | Origen público HTTPS, sin ruta, credenciales, consulta ni fragmento |

La ausencia de variables hace fallar la entrega de forma explícita. El rol necesita permisos
acotados para listar/escribir objetos del bucket e invalidar/consultar su distribución. La
política de confianza debe verificar `aud` y el `sub` real del repositorio y la rama `main`:
no asumir un formato universal del `sub`, pues GitHub admite identificadores inmutables.
CloudFront debe usar el bucket privado, un certificado válido y redirección de HTTP a HTTPS.
La configuración real y la publicación siguen pendientes; no se creó infraestructura ni
se publicó sobre un destino inventado.

RNF-11: este incremento contiene presentación y estado de interfaz, sin lógica de negocio.
La cobertura de negocio es **no aplicable**, sin asignar un porcentaje ficticio. Se mantiene
para incrementos con negocio el mínimo del **90 % de líneas** y el reporte de ramas sin umbral.
