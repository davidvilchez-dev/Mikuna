# Investigación técnica: cuentas y acceso

**Fecha**: 2026-11-13. **Estado**: fase 0 completada. El usuario confirmó las propuestas recomendadas de DT-01, DT-02 y DT-03 durante la planificación. Todas las versiones que ya existían se conservan. Las alternativas siguientes registran la evaluación y no quedan abiertas.

## Base confirmada

Se conservan JDK 25, Spring Boot 4.1.2, PostgreSQL y acceso JDBC/SQL; React/React DOM 19.3.1, TypeScript 6.0.3, Vite 8.3.8, Tailwind 4.3.5, Node 24.14.0, pnpm 12.4.2 y Playwright 1.65.0. El BOM de Boot administra JUnit Jupiter 6.0.3, Testcontainers 2.0.5 y Surefire/Failsafe 3.5.6. El wrapper Maven apunta a 3.10.0; Maven local 3.9.14 también cumple los requisitos de Boot. No se cambia ninguna versión existente durante esta planificación.

Las cinco aclaraciones de negocio de [spec.md](spec.md) se reutilizan sin volver a consultarlas. Recuperación: 30 minutos; acceso: pausa de 15 minutos tras 10 fallos consecutivos en 15 minutos; recuperación: máximo 5 solicitudes por correo en 15 minutos. La política comercial del restaurante es independiente del estado de su cuenta.

## DT-01 confirmado: autenticación y protección

**Decisión confirmada por el usuario, 2026-11-13**: tokens opacos aleatorios de 256 bits, verificador SHA-256 en PostgreSQL y cookie de acceso `Secure`, `HttpOnly`, `SameSite=Lax`, host-only. Caducidad absoluta de 8 horas, sin renovación automática ni refresh token. El cierre revoca el token actual; la recuperación revoca todos los accesos. Cada operación protegida consulta la cuenta y credencial vigentes en la base principal. Sin `HttpSession` ni estado autoritativo en memoria de una instancia. El frontend y `/api/*` comparten origen público; CSRF explícito en escrituras, también en formularios públicos.

Contraseñas: Argon2id con memoria inicial de 19456 KiB, 2 iteraciones, paralelismo 1, sal de 16 bytes y salida de 32 bytes, identificador de algoritmo conservado con el hash. Requiere añadir `org.bouncycastle:bcprov-jdk18on:1.87` con versión explícita: ni el BOM de Boot ni el de Spring Security la administran para el consumidor. La [distribución oficial](https://www.bouncycastle.org/download/bouncy-castle-java/) ofrece esa versión estable para JDK 1.8 y posteriores. Alternativa: PBKDF2-HMAC-SHA256 con 600000 iteraciones, sal de 16 bytes y salida de 32 bytes, sin nueva dependencia criptográfica.

**Justificación**: la consulta vigente y revocación inmediata ya son necesarias para RF-04. Un token opaco evita introducir firma y rotación de claves de JWT sin eliminar esa consulta. Ocho horas es una propuesta de producto, no una obligación de la documentación. Los algoritmos propuestos admiten contraseñas de 128 caracteres sin el límite de 72 bytes de bcrypt.

**Alternativas consideradas**: JWT con lista de revocación y consulta de estado, sesiones de servidor en memoria, refresh tokens y bcrypt; añaden responsabilidades o contradicen las invariantes del incremento.

Fuentes: [requisitos de Boot](https://docs.spring.io/spring-boot/system-requirements.html), [autenticación sin sesión](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), [CSRF en SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa), [almacenamiento de contraseñas](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [implementación Argon2 de Spring](https://raw.githubusercontent.com/spring-projects/spring-security/7.1.2/crypto/src/main/java/org/springframework/security/crypto/argon2/Argon2PasswordEncoder.java).

## DT-02 confirmado: límites por origen y recuperación asíncrona

**Decisión confirmada por el usuario, 2026-11-13**: conservar los límites por correo ya aprobados y añadir máximos de 100 intentos de acceso y 30 solicitudes de recuperación por dirección de origen en cualquier ventana de 15 minutos. Determinar el origen solo desde la conexión o proxies configurados como confiables. Los límites compartidos se mantienen en PostgreSQL; no se necesita incorporar Redis a este incremento.

Correo: un intento inicial y hasta tres reintentos, con esperas de 1, 5 y 15 minutos tras el fallo anterior, para fallos temporales de red, 429 y 5xx. No reintentar errores permanentes ni enlaces consumidos, sustituidos o vencidos. Mantener la misma recuperación, payload e idempotency key; ningún reintento extiende los 30 minutos. Tras agotar intentos, registrar resultado terminal sin secretos y permitir otra solicitud pública dentro de los límites.

**Justificación**: RabbitMQ, worker y Resend ya están elegidos. La creación de recuperación y su intención de envío deben confirmarse juntas en PostgreSQL; un outbox transaccional permite publicar después sin perder el correo si falla el broker. RabbitMQ transportará solo el identificador de la comunicación; la prueba de recuperación necesaria para el envío se protegerá cifrada en la intención persistida y se eliminará al terminar o vencer. Es una deducción de las invariantes, no una garantía de entrega exactamente una vez entre servicios.

**Alternativas consideradas**: envío síncrono desde la petición, publicar antes del commit, guardar tokens legibles o reintentar indefinidamente; contradicen las decisiones previas o impiden verificar fallos acotados.

Fuentes: [AMQP en Boot](https://docs.spring.io/spring-boot/reference/messaging/amqp.html), [confirmaciones y acknowledgements de RabbitMQ](https://www.rabbitmq.com/docs/confirms), [idempotencia de Resend](https://resend.com/docs/dashboard/emails/idempotency-keys), [bloqueos de PostgreSQL](https://www.postgresql.org/docs/18/explicit-locking.html).

## DT-03 confirmado: preparación mínima y cobertura

**Decisión confirmada por el usuario, 2026-11-13**: conservar herramientas existentes y añadir JaCoCo 0.8.16 para Java; Vitest 5.1.0 y `@vitest/coverage-v8` 5.1.0 para reglas y validadores de TypeScript; Testcontainers PostgreSQL/RabbitMQ/JUnit administrados por Boot para las integraciones necesarias. Se adoptan PostgreSQL 18.7 y RabbitMQ 4.4.0 como versiones iniciales que aún no estaban fijadas, con imágenes fijadas por digest al implementar. Sustituir starter JPA por JDBC y su variante de test para seguir la arquitectura ya aprobada; no adoptar un ORM.

Cobertura de negocio: al menos 90 % de líneas por cada lenguaje con negocio y porcentaje global ponderado por líneas cubiertas/totales; ramas informadas sin umbral. Incluir validadores y reglas aunque no sean importados por pruebas; excluir solo presentación, configuración sin reglas y código generado con justificación revisable. El umbral por lenguaje es una decisión adicional confirmada expresamente por el usuario, no una cifra que se atribuya a la propuesta original.

**Justificación**: permite probar negocio sin navegador; Playwright permanece como ejecutor de recorridos de aceptación. No se añaden otra biblioteca visual, router, gestor global de estado, ORM, H2, Redis, SDK de correo, microservicios ni infraestructura de otros dominios. La regla de aplazamiento del navegador durante diseño se conserva: no se instala ni ejecuta Playwright al planificar.

**Alternativas consideradas**: medir TypeScript solo mediante cobertura Chromium obliga a depender del navegador y de conversión de source maps; promediar porcentajes o medir solo Java permite ocultar lógica sin verificar.

Fuentes: [BOM de Boot](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html), [Testcontainers PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/), [JaCoCo: versiones y soporte](https://www.jacoco.org/jacoco/trunk/doc/changes.html), [Vitest 5.1.0](https://registry.npmjs.org/vitest/5.1.0), [cobertura Vitest](https://vitest.dev/guide/coverage.html), [PostgreSQL 18](https://www.postgresql.org/docs/18/index.html), [versiones RabbitMQ](https://www.rabbitmq.com/release-information).

## Estado de entorno

La revisión de herramientas no equivale a ejecutar pruebas. Docker CLI existe, pero su daemon no respondió; las integraciones con Testcontainers requerirán habilitarlo en el entorno de implementación. No se instaló ninguna dependencia ni se ejecutó el wrapper. No hay repositorio Git inicializado ni hooks de extensión registrados.

## R-04. Persistencia, transacciones y límites compartidos

**Decisión**: usar `JdbcClient`/`JdbcTemplate`, SQL parametrizado, Flyway y transacciones Spring contra PostgreSQL principal. Separar cuenta, credencial de acceso, recuperación, auditoría de alta administrativa, límites y comunicaciones. Guardar solo verificadores de tokens en sus tablas de seguridad. La única copia reversible necesaria para entregar un enlace se cifra con AES-256-GCM en la comunicación pendiente, con clave externa, nonce único y borrado al terminar. RabbitMQ recibe identificadores, nunca contraseñas ni enlaces.

**Justificación**: preservar unicidad y consumo de recuperación mediante restricciones y bloqueo de filas hace que las reglas sobrevivan a reinicios y múltiples instancias. El outbox se confirma en la misma transacción que la recuperación; solo se marca publicado tras confirmación del broker. Esto evita depender de una transacción distribuida con RabbitMQ o Resend. La entrega sigue siendo al menos una vez; la clave de idempotencia de Resend y el registro de intento evitan efectos repetidos dentro del plazo de recuperación.

**Alternativas consideradas**: JPA por estar declarado en el esqueleto, H2 para integración, Redis para todo control y listas en memoria. No son necesarias para el alcance ni sustituyen la base JDBC/SQL elegida.

Fuentes: [JDBC de Spring](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html), [bloqueos de PostgreSQL](https://www.postgresql.org/docs/18/explicit-locking.html), [selección de trabajo con SKIP LOCKED](https://www.postgresql.org/docs/18/sql-select.html), [cifrado autenticado del JDK 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html). Los proveedores documentan los mecanismos; la combinación es una decisión de diseño inferida de las invariantes de Mikuna.

## R-05. Interfaz sin ampliar el conjunto de herramientas visuales

**Decisión**: navegación de cinco pantallas mediante History API y un mapa de rutas cerrado; enlaces nativos y manejo de `popstate`. Reutilizar encabezado, footer, recursos, tokens y controles pertinentes. Extraer el contenedor compartido preservando su DOM/clases visuales y mantener las cinco secciones públicas en su recorrido continuo. React Context contiene únicamente identidad/estado de carga; el token permanece en cookie HttpOnly. El cliente HTTP usa `fetch` nativo.

**Justificación**: las rutas son pocas y no requieren router, biblioteca de formularios ni almacén de estado nuevos. La navegación pública conserva sus fragmentos, y el host entrega el índice solo para rutas de página conocidas; no se debe convertir un error de API en HTML. El contrato de interfaz especifica foco, retorno al inicio, errores y expiración.

**Alternativas consideradas**: sustituir toda la interfaz, convertir las anclas actuales en un hash-router o guardar credenciales en localStorage. Cambian decisiones vigentes o exponen responsabilidades innecesarias.

Fuente: [History API](https://developer.mozilla.org/en-US/docs/Web/API/History_API), complementada por la inspección de los componentes existentes. No se introduce una dependencia nueva.

## R-06. Fronteras modulares, entrega y gates

**Decisión**: una aplicación backend modular y un único artefacto, con modos `api`, `worker` y `admin-bootstrap`. `accounts` depende del contrato público de `communications`; este último no lee ni modifica tablas privadas de cuentas. La cuenta cancela comunicaciones mediante ese contrato al sustituir o consumir una recuperación, dentro de la misma transacción. El worker opera solo sobre comunicaciones y el proveedor. No se añade un microservicio o proyecto Maven independiente.

Extender el flujo de GitHub Actions existente para verificar frontend/backend e integrar la misma imagen Java en API y worker; solo desplegar después de pruebas y cobertura correctas. El cambio mínimo de hosting da al mismo origen público un comportamiento `/api/*` hacia ALB por HTTPS, sin caché de respuestas ni cookies, conservando estáticos en S3/CloudFront. No se aprovisionan réplicas, autoscaling completo o dominios de negocio futuros durante este incremento.

**Justificación**: reutiliza la arquitectura ya aprobada y habilita correo asíncrono y revocación inmediata sin dependencia circular. Los puertos privados de operación y los contratos evitan convertir el rol administrador en acceso universal al perfil.

Fuentes: [HTTPS de CloudFront al origen](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-https-cloudfront-to-custom-origin.html), constitución y flujo existente. La conformidad es de diseño; HTTPS, cobertura, carga y despliegue reales necesitan su evidencia posterior.
