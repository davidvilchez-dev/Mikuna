# Implementation Plan: Cuentas y acceso de Mikuna

**Branch**: `002-cuentas-acceso` — identificador de la funcionalidad devuelto por Spec Kit; no existe una rama Git materializada porque este workspace no tiene `.git`.
**Date**: 2026-11-13
**Spec**: [spec.md](spec.md)
**Input**: RF-01 a RF-04, cinco aclaraciones funcionales y las respuestas de planificación DT-01 a DT-03. Este comando produce diseño; no implementa ni despliega la aplicación.

## Summary

Integrar registro, inicio/cierre de sesión, recuperación y perfil propio sobre la interfaz pública existente. Se mantienen React/TypeScript, JDK 25, Spring Boot 4.1.2 y PostgreSQL con JDBC/SQL. Una aplicación Java modular expone la API y puede arrancar también como worker de correo o procedimiento privado de alta administrativa. La autenticación emplea tokens opacos persistidos con vigencia absoluta de ocho horas, cookies protegidas y autorización con cuenta vigente. La recuperación dura treinta minutos y confirma atómicamente cambio de contraseña, consumo e invalidación de accesos.

La preparación se limita a PostgreSQL, RabbitMQ, el adaptador Resend, seguridad y pruebas del incremento. No se incorpora Redis, catálogo, solicitud/aprobación de restaurantes, pedidos, pagos ni entregas. RF-04 solo puede aceptarse parcialmente: aquí se verifica pertenencia del perfil y permisos sobre cuentas; la autorización de los otros recursos conserva sus dependencias futuras.

## Technical Context

**Language/Version**: Java/JDK 25; TypeScript 6.0.3; Node 24.14.0; pnpm 12.4.2. JDK local observado: 25.0.1. Se conserva el wrapper Maven 3.10.0; Maven local 3.9.14 es compatible y puede ejecutar los mismos objetivos sin modificar el wrapper.

**Primary Dependencies**: React/React DOM 19.3.1, Vite 8.3.8, Tailwind y plugin 4.3.5 según lock; Spring Boot 4.1.2, Spring Framework 7.0.10, Spring Security 7.1.2, Flyway 12.4.0 y driver PostgreSQL 42.7.14 según BOM. Sustituir starter JPA y su variante de test por `spring-boot-starter-jdbc` y `spring-boot-starter-jdbc-test`; mantener webmvc, validation, security, actuator y Flyway. Añadir `spring-boot-starter-amqp` para el circuito de correo, con versión administrada por Boot; `bcprov-jdk18on:1.87` explícito para Argon2id. Adaptador HTTP de Resend con cliente del JDK, sin SDK nuevo.

**Storage**: PostgreSQL 18.7, base principal para cuentas, credenciales, recuperaciones, límites, auditoría y outbox. Flyway versiona el esquema; no usar JPA/Hibernate, H2 ni generación automática de tablas. RabbitMQ 4.4.0 para transportar identificadores de comunicaciones. Imágenes exactas y digests se registran al implementar, sin cambiar las versiones aprobadas ni usar `latest`.

**Testing**: JUnit Jupiter 6.0.3, Surefire/Failsafe 3.5.6 y Testcontainers 2.0.5 administrados por Boot. Módulos Testcontainers 2: `testcontainers-postgresql`, `testcontainers-rabbitmq`, `testcontainers-junit-jupiter`; `@DynamicPropertySource` evita otro starter. JaCoCo 0.8.16; Vitest y `@vitest/coverage-v8` 5.1.0 con entorno Node para reglas TypeScript. Playwright 1.65.0 existente para aceptación final y seis ventanas. No instalar ni ejecutar navegador durante esta planificación.

**Target Platform**: Web adaptable; desarrollo Windows; API/worker Java en Docker/Linux y PostgreSQL/RabbitMQ; frontend estático en S3/CloudFront. Un mismo origen HTTPS sirve páginas y `/api/*`; API a través de ALB. No se convierte Vite preview en servidor de producción.

**Project Type**: Aplicación web frontend/backend existente, con módulos Java y modos de arranque dentro de un único artefacto; no se agrega un microservicio ni proyecto frontend.

**Performance Goals**: Conservar metas originales de RNF-01/RNF-02 para su evaluación posterior; no inventar un SLA de cuentas ni ejecutar carga de catálogo inexistente. Las operaciones de acceso deben conservar sus invariantes entre instancias. Recuperación: 30 minutos; credenciales: 8 horas absolutas; límites temporales exactos en [data-model.md](data-model.md).

**Constraints**: Un correo normalizado, una cuenta y un rol; autoservicio solo CLIENT/RESTAURANT/COURIER. Alta ADMIN privada por responsable de despliegue. Perfil propio con nombre editable, sin cambio de correo/rol/estado. Argon2id 19456 KiB, t=2, p=1, sal 16 bytes, salida 32 bytes. Token opaco aleatorio de 32 bytes, SHA-256 persistido. Cookie HttpOnly/Secure y CSRF. Sin sesiones en memoria de API ni secretos en URL de petición, almacenamiento web, logs o mensajes AMQP.

**Scale/Scope**: Cinco historias, 34 escenarios, 18 requisitos locales y siete resultados de éxito de la spec; cuatro roles; cinco pantallas de cuentas y seis ventanas existentes. Los aproximadamente 10000 usuarios son referencia global, no carga validada aquí. Conservar las cinco secciones públicas y footer; listado de restaurantes retirado.

Las elecciones pendientes se resolvieron mediante las tres respuestas del usuario registradas en [research.md](research.md). Entradas de entorno reales, credenciales, digests de imágenes y destino de despliegue no son políticas por adivinar: se suministran y verifican al implementar. Un dato obligatorio ausente debe hacer fallar la configuración, nunca generar una credencial por defecto.

## Constitution Check

La revisión previa a fase 0 confirmó que no se podían seleccionar silenciosamente DT-01 a DT-03. Se investigaron y consultaron propuestas concretas; el usuario eligió las tres recomendadas. La fase 1 parte de esas respuestas. La revisión posterior evalúa el diseño completo, no acredita implementación o ejecución.

| Gate | Antes de investigación | Después de fase 1 |
| --- | --- | --- |
| I. Modularidad | Conservar aplicación modular, worker y contratos | Conforme: `accounts`, `communications` y composición; cuentas solo invoca contratos públicos de comunicaciones, sin dependencia inversa. |
| II. Responsabilidades | Presentación separada de reglas y persistencia; JDBC/SQL y correo asíncrono vigentes | Conforme: controladores delgados, servicios transaccionales, repositorios SQL, adaptadores HTTP/AMQP; reemplazar JPA del esqueleto. |
| III. Seguridad | RNF-07 público/protegido, pertenencia, hashing, recuperación temporal; DT-01 pendiente de consulta | Conforme en diseño tras respuesta: Argon2id, token revocable, consulta de estado, CSRF, HTTPS y denegación por defecto. Credenciales y recuperación protegidas; solo registro privado ADMIN. |
| IV. Consistencia | Identificar invariantes de alta y recuperación aunque no haya pedidos | Conforme: unicidad SQL, filas bloqueadas, consumo único y transacciones; envío asíncrono mediante outbox y efectos idempotentes. |
| V. Pruebas/cobertura | No reutilizar la excepción visual de ausencia de negocio; DT-03 requería consulta | Conforme en diseño tras respuesta: >=90 % de líneas Java y TS por separado, global ponderado y ramas informadas; pruebas unitarias, integración y recorridos. |
| VI. Trazabilidad | Conservar códigos RF/RNF; no atribuir cumplimiento a pantallas | Conforme: matriz de este plan enlaza escenarios, contratos y evidencia futura; RF-04 queda parcial. |
| Consultas pendientes | DT-01, DT-02 y DT-03 identificadas, sin elegir por defecto | Resueltas por el usuario; alternativas descartadas en research. No quedan decisiones de alcance necesarias para tareas. |
| Preparación acotada | No arrastrar infraestructura de dominios futuros | Conforme: sin Redis, réplica de lectura, autoscaling completo, pagos, mapas, imágenes remotas ni notificaciones push. |

**Resultado: gates de diseño conformes, sin excepciones constitucionales.** La autorización y las decisiones técnicas se consideran resueltas; los objetivos RF/RNF siguen pendientes de implementación y evidencia. Docker Engine, destinos y credenciales reales deben existir antes de ejecutar las comprobaciones correspondientes.

## Project Structure

### Documentation (this feature)

```text
specs/002-cuentas-acceso/
├── spec.md
├── existing-state.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── api.md
│   ├── ui.md
│   └── recovery-delivery.md
└── checklists/requirements.md
```

`tasks.md` se generará mediante `$speckit-tasks`; no se crea en este comando. Los contratos Markdown describen métodos, payloads, esquemas, errores e invariantes verificables; no requieren añadir una herramienta OpenAPI.

### Source Code (repository root)

Estructura propuesta para implementación; estos archivos de aplicación todavía no se crean:

```text
backend/
├── pom.xml
├── Dockerfile
├── src/main/java/com/mikuna/backend/
│   ├── BackendApplication.java
│   ├── accounts/
│   │   ├── api/                  # Contratos internos, controladores y DTO de acceso/perfil
│   │   ├── application/          # Casos de uso y coordinación transaccional
│   │   ├── domain/               # Invariantes, reglas y puertos
│   │   └── infrastructure/       # JDBC, hashing, filtro de token y bootstrap privado
│   ├── communications/
│   │   ├── api/                  # Programar/cancelar comunicación en la transacción actual
│   │   ├── application/          # Dispatch, consumo, idempotencia y reintentos
│   │   └── infrastructure/       # SQL, RabbitMQ, cifrado y Resend
│   └── configuration/            # Modos api/worker/admin-bootstrap y seguridad
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/             # Migraciones de cuentas/comunicaciones
└── src/test/java/com/mikuna/backend/
    ├── accounts/                # Unitarias, contratos y concurrencia SQL
    └── communications/          # Integración con broker/proveedor simulado
frontend/
├── src/
│   ├── App.tsx                  # Mapa cerrado de rutas y contenedor compartido
│   ├── index.css                # Conservar tokens y composición pública
│   ├── shared/
│   │   ├── navigation/          # History API, popstate y rutas tipadas
│   │   └── http/                # fetch, CSRF y errores Problem Details
│   └── features/
│       ├── home/                # Reutilizar componentes y acciones existentes
│       ├── public-pages/        # Conservar cinco secciones y navegación por anclas
│       └── accounts/
│           ├── pages/           # Registro, inicio, recuperar, restablecer, perfil
│           ├── components/      # Campos/errores y marco de cuenta con estilos existentes
│           ├── application/     # Estado de acceso, guards y coordinadores comprobables
│           ├── validation/      # Reglas del contrato, incluidas en cobertura
│           └── accounts.css
├── tests/unit/accounts/
├── tests/e2e/                   # Adaptar suite pública y añadir recorridos de cuentas
└── vitest.config.ts
infra/compose.accounts.yml        # Solo PostgreSQL y RabbitMQ para desarrollo/validación
.github/workflows/frontend.yml   # Extender verificación/entrega existentes
scripts/check-business-coverage.mjs
```

**Structure Decision**: ampliar las carpetas frontend/backend existentes. Compartir encabezado y footer sin copiar su diseño; las nuevas páginas solo añaden contenido de cuentas. El mismo jar arranca en modo API, worker sin HTTP o bootstrap privado sin HTTP/consumidores. El worker pertenece a la arquitectura existente y no introduce otro dominio desplegable independiente.

## Design Decisions

### Autenticación, autorización y transacciones

- `SecurityFilterChain` sin HttpSession ni login/formulario por defecto; autenticación de cookie opaca y `STATELESS`. La persistencia de credenciales revocables no constituye una sesión en memoria de la API.
- Token `__Host-mikuna_access`: 256 bits aleatorios, ocho horas absolutas, `Secure; HttpOnly; SameSite=Lax; Path=/`, sin Domain. Conservar exclusivamente verificador SHA-256 y metadatos. No devolverlo en JSON ni guardarlo en localStorage/sessionStorage. La cuenta y el token se comprueban en la principal en cada operación protegida.
- Cookie CSRF separada y cabecera `X-XSRF-TOKEN`; escrituras JSON con origen permitido, incluidos registro, login y recuperación. El acceso sin autenticación a un recurso protegido devuelve 401; con credencial válida pero estado/pertenencia no permitidos, 403. Obtener CSRF fresco tras login/logout y recuperación de un error CSRF; no reintentar escrituras ambiguas automáticamente.
- Roles públicos `CLIENT`, `RESTAURANT`, `COURIER`; `ADMIN` solo por modo privado. No se considera autoritativo un rol o estado enviado por el navegador. Todos pueden usar su perfil; ninguno puede usar el flujo propio para consultar o editar a otro titular.
- `credentials_version` une cuenta y credencial. Login verifica password fuera del bloqueo prolongado y, antes de emitir acceso, vuelve a comprobar versión/hash/estado dentro de transacción. Reset bloquea cuenta y recuperación, confirma consumo, cambia hash, incrementa versión y revoca accesos. Así no se emite un token con contraseña anterior después de un reset concurrente.
- Perfil: propietario y estado se comprueban en la transacción de escritura; se rechazan campos desconocidos y protegidos. Un `UPDATE` exclusivo del nombre conserva invariantes. Ediciones concurrentes válidas del nombre usan último commit confirmado; no se añade un editor colaborativo ni se sobrescriben campos no editables.

### Recuperación y límites

La [máquina de estados](data-model.md) conserva una recuperación vigente por cuenta; pedir otra cancela el envío pendiente anterior. Restablecer con el mismo enlace concurrentemente solo puede confirmar una vez. El plazo se valida al actualizar; un bloqueo no permite aceptar un enlace vencido mientras se esperaba. Obtener hora vigente tras adquirir el bloqueo, sin depender de la hora de apertura de la pantalla.

El límite de correo y el de origen se serializan mediante claves de sujeto en PostgreSQL. Correo/origen se representan con HMAC-SHA256 y clave externa, incluidos correos inexistentes, para no guardar identidades ficticias legibles. Se bloquean sujetos en un orden estable. Las ventanas excluyen los eventos con antigüedad de quince minutos o más. La sexta recuperación bloqueada no cancela la vigente ni genera comunicación. El décimo fallo produce la pausa; los intentos bloqueados no la extienden. Los máximos por origen son 100 accesos y 30 recuperaciones en quince minutos.

Crear la recuperación y `communications.enqueue(...)` en una misma transacción JDBC. El publicador del outbox usa confirmaciones RabbitMQ; el worker recibe solo identificador/intento y envía por Resend con clave de idempotencia estable. Un intento inicial y hasta tres reintentos con esperas de 1, 5 y 15 minutos tras el fallo anterior; ningún intento empieza con recuperación cancelada, consumida o vencida. Los secretos temporales de envío se cifran y borran al terminar. Detalles en [recovery-delivery.md](contracts/recovery-delivery.md).

### Interfaz y navegación

Cinco rutas: `/cuenta/registro`, `/cuenta/iniciar-sesion`, `/cuenta/recuperar`, `/cuenta/restablecer` y `/cuenta/perfil`; la portada permanece en `/` con sus anclas. History API y enlaces nativos, sin router nuevo. Un único contenedor conserva header/footer, foco y menú. El código que observa secciones solo se ejecuta para la página pública.

Registro no inicia sesión automáticamente: confirma la cuenta y ofrece entrar. Login lleva al perfil; la recuperación exitosa ofrece login sin autenticar. Al recargar, la UI consulta el acceso vigente y obtiene el perfil del titular; un error de red se diferencia de ausencia de acceso. Un 401 retira datos privados; 403 indica restricción/permiso y no revela otro perfil. Los fallos de servidor nunca se convierten en guardados locales exitosos. El enlace de recuperación transporta la prueba en el fragmento del navegador y la retira antes de recursos/analítica; el envío al servidor es JSON por HTTPS.

El [contrato UI](contracts/ui.md) fija integración de todos los botones actuales, campos, estados, accesibilidad, colores y seis ventanas. Mantener Pedir ahora, Empezar a pedir y categorías con aviso; no reintroducir listado de restaurantes.

### Alta administrativa privada

El responsable ejecuta el mismo jar en modo `admin-bootstrap`, sin servidor HTTP, endpoints ni consumers. Entrada interactiva por terminal: correo, nombre, identidad operacional y contraseña oculta; no contraseñas en argumentos, historial o variables mostradas. El entorno restringido aporta la identidad operacional confiable; el procedimiento documenta su procedencia. Usa el servicio de cuentas y sus mismas validaciones/hashing/unicidad, rol ADMIN fijado en servidor y auditoría de quién/cuándo/cuenta creada en la misma transacción. Un duplicado nunca promueve ni sustituye otra cuenta.

## Validation and Delivery

### Trazabilidad y evidencia prevista

| Fuente / requisitos | Diseño y contratos | Escenarios de la spec | Evidencia necesaria al implementar |
| --- | --- | --- | --- |
| RF-01; ACC-001 a ACC-005 | Alta, token, roles y límites; api.md; procedimiento privado | US1.1 a US1.7; US2.1 a US2.6 | E2E de tres altas públicas y alta ADMIN privada; recarga/expiración/cierre; hashes y unicidad PostgreSQL. |
| RF-02; ACC-006 a ACC-009 | Recuperación, comunicación/outbox, idempotencia y vencimiento | US3.1 a US3.9 | Recepción de correo, restablecimiento real, pruebas inválidas/expiradas/reutilizadas, dos consumos concurrentes y broker/proveedor caídos. |
| RF-03; ACC-002/ACC-010/ACC-011 | Perfil propio y allowlist de nombre | US4.1 a US4.5 | Persistencia tras recarga/login; lectura por cada rol; inmutabilidad de rol/correo/estado y perfiles ajenos sin cambios. |
| RF-04/RNF-07; ACC-003/ACC-005/ACC-012/ACC-013 | Filtro de acceso, estado vigente y propietario | US1.4; US2.3 a US2.5; US4.4/US4.5; US5.1 a US5.5 | Matriz por cuatro roles, dos titulares, estado habilitado/restringido; 401/403 directos; token manipulado y antigua credencial rechazada tras commit. |
| ACC-014 / constitución III | Argon2id, verificadores, cifrado temporal, logs y cookies | US1 a US4; US3.3 | Inspección de tablas/logs/respuestas; contraseñas Unicode y >72 bytes; cookies, CSRF y ausencia de secretos en trazas y AMQP. |
| ACC-015 a ACC-018 | ui.md, cliente HTTP y reutilización | US1.7; US3.2/US3.5/US3.7; US4.2/US4.3; US5.6/US5.7 | Cinco pantallas integradas; seis ventanas, teclado y zoom 200 %; regresión pública; éxito solo tras commit y errores de red corregibles. |
| RNF-06 | Mismo origen HTTPS, `/api/*` sin caché, cookies y reset | SC-006 | Certificado y HTTP redirigido/rechazado en entorno real; HTTPS hasta ALB y conexión privada protegida. HTTP local no es evidencia de entrega. |
| RNF-11 | JUnit/Vitest y cobertura real por lenguaje | SC-007 | >=90 % líneas de negocio Java y TS; cubiertas/totales y ramas; informe global ponderado, conjunto medido y exclusiones revisadas. |
| RNF-12 | Pipeline integrado de frontend, API y worker | SC-007 | PR/main: compilación, pruebas, cobertura y build; mismo artefacto desplegado automáticamente si pasa; fallos impiden publicar. |
| RNF-02/RNF-04/RNF-10 | Acceso compartido, logs sin secretos y métricas existentes | Límites del incremento en spec | Verificar invariantes con dos instancias para cuentas; carga 1/3, caída de instancia y panel global quedan para evaluación posterior, sin declararlos satisfechos. |

No se crean pruebas de pedidos/restaurantes/entregas inexistentes para simular RF-04 completo. La futura matriz deberá extenderse al introducir cada recurso. RNF-01/RNF-03/RNF-05/RNF-08/RNF-09 conservan la verificación diferida documentada en spec.

### Estrategia de pruebas

1. Unitarias Java: normalización/validación, privilegios, estado, pertenencia, credenciales, vencimiento y reglas de límites con `Clock` inyectable. No esperar quince minutos reales ni cambiar el reloj de producción.
2. Integración: PostgreSQL 18.7 real con Flyway, sin H2, para altas concurrentes, reset único, login frente a reset, perfil frente a restricción y límites desde dos instancias. Barreras/latches y conexiones independientes; comprobar filas tras commit. No confundir llamadas secuenciales con concurrencia.
3. Seguridad HTTP: cadena Spring Security real, cookies/CSRF, allowlist de rutas, métodos/DTO estrictos, ausencia de autenticación básica por defecto, fallos 401/403/429/503 y ninguna modificación ante denegación.
4. Comunicaciones: RabbitMQ real en Testcontainers y servidor HTTP controlado para Resend (del JDK, sin WireMock adicional). Duplicar mensajes y simular timeout después de aceptación, errores permanentes, 429/5xx, broker no disponible, caída de worker, cancelación y vencimiento. Validar el mismo payload/clave, presupuesto de intentos y borrado de material cifrado.
5. TypeScript: Vitest en Node para validadores, normalización, coordinadores, guards, expiración y errores; `coverage.include` abarca todos los archivos de negocio, aunque no los importe una prueba. El servidor sigue siendo autoritativo.
6. E2E Playwright al retomar aceptación final: app y API reales, persistencia y comunicaciones controladas, cuatro roles y seis ventanas. Actualizar expectativas obsoletas de la suite pública (footer/anclas/cuentas). Revisar manualmente zoom real y lector de pantalla. Para validar el adaptador real de correo, añadir prueba controlada de recepción en buzones de aceptación autorizados; un proveedor simulado no es la única evidencia del correo completo.

No escribir tests que solo reflejen constantes o render de texto sin probar decisiones. Los fallos existentes de navegador y Docker se informan como entorno pendiente, nunca como escenarios aprobados.

### Cobertura, integración y entrega

- JaCoCo fusiona ejecuciones unitarias/integración y verifica >=0.90 de líneas del conjunto de negocio Java; Vitest verifica >=90 % del negocio TypeScript. Informar ramas sin umbral. El agregado es `100 * (coveredJava + coveredTS) / (totalJava + totalTS)`, sin promedio de porcentajes ni mezclar presentación para diluir reglas.
- La revisión identifica conjunto medido, cubiertas/totales, exclusiones justificadas y fuentes no importadas. Validadores dentro de componentes también cuentan; preferir extraerlos a archivos comprobables. Si un lenguaje no contiene negocio, justificarlo expresamente; no inventar 100 %.
- Extender el workflow existente con JDK 25, instalación desde lock, Maven `verify`, Vitest, build y aceptación final. Fijar acciones por SHA y versiones/digests de servicios al implementar. Publicar informes aun ante fallos. No se ejecutan ni modifican workflows en este comando.
- En `main`, después de todas las verificaciones, desplegar el mismo `dist` y la misma imagen Java evaluados, en API y worker. Migrar esquema con identidad de despliegue y permisos mínimos; no exponer migraciones ni bootstrap por HTTP. No añadir una aprobación manual del job que contradiga RNF-12.
- Reutilizar S3/CloudFront, ALB/EC2, OIDC y configuración elegidos. Ampliar solo comportamiento `/api/*`, origen HTTPS sin caché, acceso al PostgreSQL principal y RabbitMQ/worker que requiere recuperación. No desplegar autoscaling, Redis o réplicas anticipadamente.
- Los valores reales de destino, dominio, IAM, certificado y secretos se suministran como configuración; no inventarlos ni convertir su ausencia en éxito. Separar pruebas locales de evidencia HTTPS/CI/CD real.

La [guía quickstart](quickstart.md) fija comandos y resultados esperados después de implementar. Hasta entonces esos comandos son objetivos de validación planificados, no una afirmación de que los scripts nuevos existan o pasen hoy.

## Complexity Tracking

No hay excepciones constitucionales. El outbox y las dos tablas de entrega son preparación necesaria para recuperación asíncrona consistente; no constituyen una plataforma genérica de eventos. Tokens opacos y límites en PostgreSQL evitan JWT/Redis/refresh tokens. History API y `fetch` evitan dependencias visuales. El worker y el modo privado reutilizan el mismo artefacto Java. La fase siguiente es `$speckit-tasks`.
