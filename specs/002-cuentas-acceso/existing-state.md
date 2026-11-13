# Revisión de fuentes y estado existente: cuentas y acceso

**Fecha**: 2026-11-13. **Relacionada**: [spec.md](spec.md).

Revisión estática del espacio de trabajo antes de especificar. No se ejecutaron pruebas funcionales ni se modificó la aplicación. Los detalles técnicos siguientes describen evidencia existente y restricciones heredadas; no son un diseño de implementación nuevo.

## Fuentes contrastadas

- [Propuesta_de_Proyecto.docx](../../Propuesta_de_Proyecto.docx): leído el texto de `word/document.xml`, incluidas las tablas de RF/RNF, verificación, alcance y arquitectura. RF-01 menciona los cuatro actores; RF-02 recuperación autónoma; RF-03 perfil sin cambio de rol; RF-04 rol, estado y relación con recurso. RF-05/RF-06 separan solicitud/aprobación de restaurantes y limitan la recepción de pedidos a aprobados.
- [Constitución v1.0.0](../../.specify/memory/constitution.md): revisados los seis principios, arquitectura, aclaraciones y TODO de políticas/decisiones. Registro, inicio y recuperación públicos; operaciones personales/gestión protegidas; hashes adaptativos con sal, recuperación temporal de un uso y secretos protegidos; tokens sin sesiones en memoria; cobertura de líneas >= 90 % y ramas informadas sin umbral.
- [Especificación 001](../001-inicio-publico/spec.md): revisada su sección Clarifications. Los avisos eran una solución temporal del incremento visual, no cuentas implementadas.
- [Ampliación visual](../001-inicio-publico/ampliacion-visual.md), [validación](../001-inicio-publico/validation.md) y [contexto](../../docs/CONTEXTO_PARA_CONTINUAR.md): las decisiones recientes sustituyen la descripción inicial sin footer y la ampliación intermedia con listado de restaurantes. Estado vigente: Inicio, Sobre nosotros, Cómo funciona, Para restaurantes, Sé repartidor y footer; sin listado, buscador ni ejemplos de restaurantes. Diseño público existente no equivale a aceptación funcional.

## Inventario comprobado

| Área / archivos inspeccionados | Qué existe | Qué falta respecto al incremento |
| --- | --- | --- |
| [App.tsx](../../frontend/src/App.tsx), [HomePage.tsx](../../frontend/src/features/home/HomePage.tsx) | Montaje del inicio continuo, cinco secciones, encabezado, footer y aviso común | Pantallas y navegación de cuentas; conexión con servidor e identidad autenticada. |
| [SiteHeader.tsx](../../frontend/src/features/home/components/SiteHeader.tsx), [SiteFooter.tsx](../../frontend/src/features/home/components/SiteFooter.tsx) | Logo, navegación pública, menú adaptable y enlace “Crear cuenta” del footer con aviso | Inicio/perfil/cierre reales; registro funcional. Reutilizables adaptando sus contratos al nuevo contexto. |
| [HeroSection.tsx](../../frontend/src/features/home/components/HeroSection.tsx) | Botones existentes y composición visual de portada; Crear cuenta muestra aviso | Sustituir solo el comportamiento de acceso a cuentas; conservar composición, escala y botones aprobados. |
| [ForRestaurantsPage.tsx](../../frontend/src/features/public-pages/ForRestaurantsPage.tsx) | “Registrar mi restaurante”, “Ya tengo cuenta” y texto que condiciona recibir pedidos a aprobación | Los botones usan aviso. No hay creación de cuenta, solicitud ni aprobación. El texto no resuelve POL-02. |
| [CourierPage.tsx](../../frontend/src/features/public-pages/CourierPage.tsx) | “Quiero ser repartidor”, “Ya tengo cuenta” y “Quiero sumarme” con aviso | No hay cuentas, disponibilidad ni entregas. Reutilizar accesos para la identidad de repartidor. |
| [index.css](../../frontend/src/index.css), [public-pages.css](../../frontend/src/features/public-pages/public-pages.css), [continuous-home.css](../../frontend/src/features/public-pages/continuous-home.css) | Seis tokens, tipografía, botones, foco, márgenes y adaptación pública | Adaptación de formularios y estados; no existe sistema funcional de errores de cuentas. |
| [BackendApplication.java](../../backend/src/main/java/com/mikuna/backend/BackendApplication.java), [application.properties](../../backend/src/main/resources/application.properties) | Punto de arranque; configuración únicamente del nombre de aplicación | No hay controladores ni reglas de cuentas, configuración de seguridad propia, repositorios, esquema/migraciones de usuarios o recuperación, ni conexión configurada con correo. |
| [pom.xml](../../backend/pom.xml) | Java 25 y Spring Boot 4.1.2 declarados; dependencias de seguridad, validación, web, JPA, Flyway y PostgreSQL, entre otras | Son dependencias, no autenticación ni persistencia implementadas. La constitución/propuesta exigen JDBC/SQL: tener JPA en el esqueleto no autoriza cambiar esa base ni adoptar un ORM por defecto. Revisar el desajuste al planificar. |
| [BackendApplicationTests.java](../../backend/src/test/java/com/mikuna/backend/BackendApplicationTests.java) | Única prueba de carga de contexto declarada | No prueba RF-01 a RF-04, permisos, persistencia, recuperación ni concurrencia. No se ejecutó aquí. |
| [package.json](../../frontend/package.json), [home.spec.ts](../../frontend/tests/e2e/home.spec.ts), [frontend.yml](../../.github/workflows/frontend.yml) | Dependencias de presentación, suite de inicio y flujo de validación/publicación de frontend | No hay pruebas de cuentas ni cobertura de su negocio. El historial documentado aplaza Playwright y avisa de expectativas antiguas; la automatización no acredita ejecuciones ni despliegue del nuevo incremento. |

Se enumeraron los archivos fuente del backend y frontend y se buscaron referencias de autenticación, acceso, contraseñas, seguridad, persistencia, tokens, perfiles, registro y aprobación. No se encontraron implementaciones de negocio de cuentas; la referencia a aprobación encontrada es contenido visual. No se trataron artefactos compilados, cachés de dependencias o páginas de ejemplo externas como código de la aplicación.

## Estado de los requisitos antes del incremento

| Requisito | Estado observado | Límite de la evidencia |
| --- | --- | --- |
| RF-01 | Pendiente | Solo accesos visuales; ninguna cuenta puede crearse ni iniciar sesión mediante un flujo de producto implementado. |
| RF-02 | Pendiente | No hay solicitud, correo, recuperación temporal ni restablecimiento. |
| RF-03 | Pendiente | No hay perfil consultable ni editable. |
| RF-04 | Pendiente | No hay operaciones propias con autorización por rol/estado/pertenencia. Una dependencia de seguridad o su comportamiento automático no satisface RF-04. |
| RNF-06/RNF-07 | No verificados para cuentas | El inicio público existente y los scripts no prueban cifrado entregado ni protección de las nuevas operaciones. |
| RNF-11/RNF-12 | Pendientes para cuentas | La justificación previa de ausencia de lógica de negocio no se podrá reutilizar cuando exista negocio de cuentas. Faltan pruebas, cobertura y ejecución de entrega del nuevo alcance. |

## Decisiones conservadas y límites

- Reutilizar identidad pública, logo, recursos y seis colores; no reintroducir el listado retirado. Mantener los avisos de pedidos/categorías hasta sus propios incrementos.
- Aplazamiento de Playwright durante diseño registrado por el usuario. Esta especificación no instala herramientas ni ejecuta navegador; la aceptación funcional futura requiere evidencia de sus recorridos y regresiones.
- No existe `.git` en el espacio de trabajo. No se creó rama ni se intentó inicializar un repositorio. `.specify/extensions.yml` no existe: no hay hooks previos que ejecutar; debe comprobarse nuevamente al cerrar la especificación.
- Los archivos no definen el alta de administradores ni el momento y vínculo de la cuenta de restaurante respecto a solicitud/aprobación. Se registran POL-01 y POL-02; no se deducen de imágenes, dependencias o textos promocionales.
- Algoritmos, parámetros y ciclo de vida de tokens/recuperación, cobertura y tratamiento de reintentos están sujetos a consulta constitucional. Se trasladan como DT-01 a DT-03, sin seleccionar bibliotecas ni valores silenciosamente.
