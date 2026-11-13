# Quickstart de validación: Inicio público

La página y su suite están implementadas. Esta guía permite repetir la validación; sus resultados
esperados no son evidencia de ejecución. Consultar [validation.md](validation.md) para los resultados
obtenidos y las comprobaciones pendientes. En el entorno de esta ejecución no pudo instalarse
Playwright ni arrancar un navegador alternativo; el usuario autorizó continuar con el resto.

## Prerrequisitos

- Node 24.14.0 y pnpm 12.4.2, versiones observadas en el entorno; PowerShell usa `pnpm.cmd`.
- Implementación de [ui-home.md](contracts/ui-home.md), los nueve recursos de
  [visual-assets.md](contracts/visual-assets.md) y el logo original incorporados al frontend.
- `@playwright/test@1.65.0`, `playwright.config.ts`, `tsconfig.e2e.json` y la suite de inicio
  materializados por las tareas, con los seis nombres de proyecto definidos en el plan.
- Puerto local 4173 libre. No hacen falta backend, base de datos, cuenta de usuario ni claves AWS
  para probar localmente la página.

## Preparación

La herramienta fue confirmada por el usuario y ya está fijada en el manifiesto y el lock.
Instalar las dependencias sin modificar ese lock y preparar Chromium:

```powershell
Set-Location 'C:\BACK\Mikuna\frontend'
pnpm.cmd install --frozen-lockfile
pnpm.cmd exec playwright install chromium
```

Las tres fotografías del hero de `docs/diseno/`, las seis de `docs/seis_categorías/` y el logo
ya están incorporados conforme al contrato, con diez huellas SHA-256 coincidentes con los
originales. No requieren generación. No utilizar cajas vacías ni la captura entera como
sustitutos para dar el diseño por terminado.

`frontend/pnpm-workspace.yaml` usa el pnpm instalado y permite ejecutar los scripts con advertencia
si falta una dependencia. No sustituye la instalación completa: actualmente la advertencia señala
Playwright ausente, por lo que sus tipos y pruebas no pueden ejecutarse todavía. No se desactivaron
las políticas de instalación para resolver la restricción de red.

## Comprobaciones reproducibles

```powershell
Set-Location 'C:\BACK\Mikuna\frontend'
pnpm.cmd install --frozen-lockfile
pnpm.cmd lint
pnpm.cmd typecheck:e2e
pnpm.cmd build
pnpm.cmd test:e2e
pnpm.cmd test:e2e:report
```

**Resultado esperado:** lint, tipos, build y suite exitosos. Playwright inicia el preview del
`dist` construido; no se lanza un segundo servidor en el mismo puerto. El informe agrupa los seis
proyectos y enlaza las capturas o trazas pertinentes. Cada proyecto conserva el mismo navegador
y escala de ejecución para sus comparaciones visuales.

Para ejecutar solo la comparación principal o inspeccionar el flujo con navegador visible:

```powershell
Set-Location 'C:\BACK\Mikuna\frontend'
pnpm.cmd exec playwright test --project=desktop-1536
pnpm.cmd exec playwright test --project=mobile-390 --headed
```

Si se desea una inspección manual del build, después de construir y sin una suite utilizando el
puerto, ejecutar:

```powershell
Set-Location 'C:\BACK\Mikuna\frontend'
pnpm.cmd preview --host 127.0.0.1 --port 4173 --strictPort
```

Abrir `http://127.0.0.1:4173/`. Este servidor es local de validación, no el despliegue de producción.

## Escenarios y evidencia esperada

| Escenario | Procedimiento observable | Resultado |
| --- | --- | --- |
| Entrada y contenido | Abrir sin cuenta ni permisos de ubicación y recorrer la página. | Textos exactos, logo, tres alimentos y seis categorías; final del contenido en categorías. |
| Navegación local | Bajar por el contenido y activar logo e Inicio. | Volver al comienzo de la misma página. |
| Accesos futuros | Activar cada uno de los cuatro enlaces, tres acciones y seis categorías; abrir antes el panel si corresponde. | Un aviso con el texto exacto; URL y desplazamiento conservados; ningún flujo de negocio. |
| Repetición del aviso | Activar de nuevo antes de expirar. | Un único aviso y reinicio de sus 5 000 ms activos; anuncio accesible renovado. |
| Tiempo y pausa | Controlar el reloj; pausar con puntero, foco y documento oculto, luego reanudar. | Visible antes de agotar 5 000 ms activos; oculto al agotarlos; la pausa conserva tiempo restante. |
| Cierre y foco | Cerrar el aviso y, por separado, cerrar el panel con Escape. | Cierre sin perder el foco útil ni desplazar la página; panel con estado accesible correcto. |
| Imágenes fallidas | Interrumpir las solicitudes de imágenes antes de abrir la página. | Permanecen título, descripción, rótulos, orden y controles; no hay elementos tapados. |
| Adaptación | Ejecutar las seis ventanas. | Sin scroll horizontal; categorías 2/3/6 columnas y misma secuencia de lectura. |
| Fidelidad | Comparar la captura a 1536 × 1024 con la referencia y comprobar colores y proporciones. | V-01 a V-10, seis tokens y tres tolerancias geométricas satisfechos. |

El reloj y la interceptación de red se implementan dentro de la suite de Playwright. No se sustituyen
por esperas largas ni por desactivar manualmente el servidor. Las verificaciones con aviso visible
incluyen pantalla estrecha, menú y última fila de categorías para detectar controles cubiertos.

## Revisión manual breve

- Revisar capturas en 320 × 568, 390 × 844, 768 × 1024, 1024 × 768, 1440 × 900 y 1536 × 1024 px.
- Comprobar transparencias sobre blanco, marfil y melocotón, composición de las tres fotografías,
  nitidez, logo sin deformar y ausencia de rótulos incrustados en los bitmaps.
- Ampliar realmente el texto al 200 % en el navegador; recorrer contenido, menú, categorías y
  acciones. No usar `deviceScaleFactor` o una transformación de la página como sustituto del zoom.
- Recorrer con Tab/Shift+Tab y activar con teclado; probar Escape y cierre del aviso.
- Con tecnología de asistencia disponible, comprobar el anuncio del mensaje, su repetición,
  los nombres de controles y la conservación del foco. Registrar navegador y herramienta usados.
- Aprobar las primeras imágenes base solo después de compararlas con la referencia y la spec.
  No actualizar automáticamente una base para ocultar una diferencia fallida.

La aceptación visual considera los recursos preparados con la referencia, no una identidad
fotográfica píxel por píxel. Las proporciones de escritorio se miden a escala normal; la ampliación
permite crecer a los bloques conservando la legibilidad.

## Validación de integración y entrega

[El workflow implementado](../../.github/workflows/frontend.yml) instala las versiones fijadas,
ejecuta los controles y conserva el mismo `dist` para publicar. Aún no se ha ejecutado en GitHub.
En Linux, instala Chromium y sus dependencias del sistema con el
comando oficial de Playwright `pnpm exec playwright install --with-deps chromium`.

Para el destino se requieren las variables `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`,
`MIKUNA_SITE_BUCKET`, `MIKUNA_CLOUDFRONT_DISTRIBUTION_ID` y `MIKUNA_PUBLIC_URL`, además del
repositorio y los permisos OIDC configurados. La ausencia de una entrada debe fallar claramente;
no debe convertir un despliegue omitido en una ejecución satisfactoria.

**Evidencia de entrega:** ejecución por cada integración a `main`, pruebas exitosas, publicación
del mismo artefacto, invalidación CloudFront y revisión de la URL pública. Verificar que HTTPS
presenta un certificado válido y HTTP redirige a HTTPS, conforme a la arquitectura. El HTTP local
no acredita RNF-06. No se ejecutó un despliegue durante esta implementación: faltan repositorio
Git/GitHub, valores del destino y permisos OIDC reales.

## Registro de aceptación

Vincular revisión de código, versiones, ejecución CI, informe Playwright, capturas y revisión
manual con SC-001 a SC-008. Registrar RNF-11 como no aplicable a lógica de negocio de este incremento,
con justificación; conservar el 90 % de líneas y reporte de ramas para el negocio posterior.
No declarar RF de cuentas, catálogo, pedidos o pagos completados por la portada.
