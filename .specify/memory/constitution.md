<!--
Sync Impact Report — material temporal de revisión; retirar antes del commit.
Cambio de versión: 1.0.0 → 2.0.0.
Motivo: enmienda mayor aprobada explícitamente por el usuario el 2026-11-13;
redefine obligaciones incompatibles de infraestructura, HTTPS, E2E y entrega para la etapa local.
Principios modificados:
- I. Modularidad por dominio → I. Monolito modular sencillo.
- II. Separación de responsabilidades: capas y Spring Data JPA; sin adaptadores anticipados.
- III. Seguridad verificable: HTTP local y cifrado para integraciones externas permitidas.
- IV. Consistencia de operaciones concurrentes: invariantes según el incremento y pagos aplazados.
- V. Pruebas automatizadas y cobertura → V. Pruebas acotadas y cobertura de negocio.
- VI. Trazabilidad de requisitos: distinguir cumplimiento local y verificación aplazada.
Principio añadido: VII. Planificación y avance incremental.
Secciones añadidas: configuración local; registro de aplazamientos; estado por RNF en esta etapa.
Secciones sustituidas: arquitectura obligatoria de la propuesta → arquitectura local ejecutable;
decisiones técnicas sujetas a consulta general → convenciones elegidas y decisiones rutinarias.
Obligaciones retiradas de esta etapa: infraestructura cloud, workers y servicios adicionales,
TLS local, E2E de navegador, CI/CD y despliegue como condición de aceptación.
Seguimiento: políticas de negocio no definidas, solo al abordar el incremento que las necesite;
infraestructura futura y verificaciones aplazadas, sin declararlas cumplidas.
Marcadores de plantilla pendientes: ninguno.
Plantillas, comandos y archivos de aplicación modificados: ninguno.
-->

# Mikuna Constitution

## Core Principles

### I. Monolito modular sencillo

- La arquitectura ejecutable DEBE constar de un frontend React con TypeScript, una única
  aplicación Java con Spring Boot y PostgreSQL, todos ejecutados localmente.
- El backend DEBE organizarse por funcionalidades. Cada módulo DEBE incorporar `model`,
  `repository`, `service`, `controller` y `dto` solo según sus necesidades; `security` y `exception`
  DEBEN mantenerse como responsabilidades comunes cuando corresponda.
- Los módulos y abstracciones DEBEN crearse cuando el incremento los necesite. NO DEBEN crearse
  estructuras vacías, interfaces sin utilidad, módulos futuros ni infraestructura anticipada.
- Las dependencias DEBEN evitar ciclos y respetar las reglas del módulo responsable de cada
  operación. No se exige una interfaz adicional cuando no aporta utilidad concreta.

**Verificación:** revisar estructura y dependencias de los módulos afectados. Esta base permite
entregar funcionalidad local sin preparar una plataforma distribuida.

### II. Separación de responsabilidades

- React con TypeScript DEBE gestionar presentación e interacción. El backend DEBE ejecutar
  las reglas de negocio, la autorización y la coordinación de las operaciones.
- Los controladores DEBEN gestionar HTTP, los servicios DEBEN contener las reglas de negocio
  y los repositorios DEBEN acceder a los datos mediante Spring Data JPA con PostgreSQL.
- Las validaciones del navegador NO DEBEN sustituir las del backend. El backend DEBE recalcular
  y validar importes, permisos y transiciones; NO DEBE confiar en precios, roles o estados
  enviados por el cliente.
- Las integraciones DEBEN separarse de las reglas de negocio cuando se implementen. No se exige
  diseñar adaptadores ni mecanismos de sustitución para proveedores o funciones futuras.

**Verificación:** revisar responsabilidades y probar las reglas y colaboraciones implementadas
sin depender de servicios externos reales en las pruebas unitarias.

### III. Seguridad verificable

- El desarrollo en localhost DEBE utilizar HTTP. NO DEBEN prepararse certificados, autoridades
  certificadoras ni TLS local. La comprobación de HTTPS del despliegue queda aplazada;
  RNF-06 NO DEBE declararse cumplido por una ejecución local.
- Las únicas integraciones externas permitidas son Cloudinary para imágenes y el servicio de
  correo electrónico. DEBEN prepararse solo cuando la funcionalidad actual las necesite y usar
  el cifrado correspondiente a su protocolo: HTTPS para APIs y TLS para transporte de correo.
- La autenticación DEBE usar tokens sin sesiones en memoria de la API. El inicio, el registro,
  el inicio de sesión, la recuperación de contraseña y RF-09/RF-10 son públicos, conforme a la
  aclaración del usuario del 2026-11-12. Las operaciones personales y de gestión DEBEN protegerse;
  cada contrato DEBE declarar si es público o protegido.
- RNF-07 DEBE aplicarse al 100 % de accesos protegidos: comprobar rol, estado de cuenta y relación
  con el recurso según RF-04, rechazar accesos sin autorización y probar los casos 401/403 y de
  pertenencia aplicables. El perfil NO DEBE permitir cambiar el propio rol.
- Solo los restaurantes aprobados DEBEN recibir pedidos. El registro público NO DEBE permitir
  eludir esa aprobación ni adquirir privilegios administrativos.
- Las contraseñas DEBEN almacenarse mediante hashes adaptativos con sal; la recuperación DEBE
  utilizar mecanismos temporales de un solo uso. Los secretos NO DEBEN exponerse en respuestas
  o registros; las credenciales externas reales NO DEBEN incluirse en commits.
- Los datos sensibles de tarjetas NO DEBEN almacenarse. La integración con la pasarela y la
  evidencia completa de RNF-08 quedan aplazadas, sin simular su cumplimiento.

**Verificación:** revisar validación, permisos, secretos y cifrado de las integraciones permitidas
que existan; probar rutas públicas y rechazos del incremento.

### IV. Consistencia de operaciones concurrentes

- Las operaciones críticas DEBEN definir y preservar sus invariantes en backend y persistencia
  cuando se implementen. Las escrituras que deban confirmarse juntas en PostgreSQL DEBEN usar
  transacciones y comprobar el estado esperado; comprobar y luego escribir sin protección
  NO DEBE ser la única garantía.
- La asignación DEBE usar actualización condicional en PostgreSQL. Una entrega DEBE tener como
  máximo un repartidor activo; solo una aceptación concurrente DEBE prosperar. RF-23 DEBE
  preservar esa unicidad al reasignar. Un bloqueo en memoria NO DEBE ser la única protección.
- El flujo nominal conserva Pendiente → Aceptado → En preparación → Listo para recoger →
  Asignado → Recogido → Entregado. Desde Pendiente se permite Rechazado por el restaurante o
  Cancelado por su cliente antes de la aceptación. Rechazar una asignación activa la reasignación,
  no el rechazo del pedido. NO DEBEN introducirse saltos o retrocesos no especificados.
- Los reintentos de una operación lógica NO DEBEN duplicar pedidos o asignaciones. Las carreras
  entre aceptación y cancelación DEBEN resolverse y probarse en el incremento correspondiente.
  RF-31 DEBE garantizar una única calificación de 1 a 5 por pedido propio entregado, incluso
  ante solicitudes simultáneas.
- Se conserva como requisito futuro de RF-16/RF-17 autorizar el pago al confirmar, capturarlo al
  aceptar, liberar la reserva al rechazar o cancelar antes de aceptar y asociar el resultado al
  pedido. La pasarela, sus webhooks, su idempotencia y su recuperación ante fallos quedan
  aplazados. NO DEBEN declararse pagos completos sin esa integración y su evidencia.

**Verificación:** pruebas de integración acotadas de las transacciones y carreras introducidas,
comprobando el estado persistido. No se requieren servicios distribuidos para esta garantía.

### V. Pruebas acotadas y cobertura de negocio

- Cada incremento DEBE utilizar pruebas unitarias y de integración acotadas a sus reglas y
  colaboraciones. El backend DEBE utilizar JUnit y Mockito según corresponda; los casos negativos
  de seguridad y consistencia DEBEN cubrirse cuando sean aplicables.
- Se conserva el objetivo de **al menos 90 % de cobertura de líneas de la lógica de negocio**
  de RNF-11 y el reporte de ramas sin umbral obligatorio, confirmados el 2026-11-12. La medición
  DEBE incluir la lógica de negocio existente, también en frontend cuando la contenga;
  NO DEBE extenderse artificialmente a toda la presentación visual.
- El reporte local DEBE identificar código medido, líneas cubiertas y totales, porcentaje y
  exclusiones justificadas. NO DEBEN excluirse reglas, validaciones o transiciones de negocio
  para alcanzar el umbral ni diluirlo con código generado. Sin lógica de negocio, la medición
  DEBE registrarse como no aplicable, sin atribuir 100 % ni cumplimiento completo de RNF-11.
- NO DEBE instalarse, configurarse ni ejecutarse Playwright. Las pruebas E2E automatizadas de
  navegador quedan aplazadas y NO DEBEN sustituirse por otra herramienta para el mismo trabajo.
- Se DEBEN verificar compilación, funcionamiento local y reglas relevantes del incremento.
  Las comprobaciones aprobadas NO DEBEN repetirse sin cambios, fallos o dudas concretas que
  lo justifiquen. CI/CD y publicación de cobertura en CI quedan aplazados.

**Verificación:** resultados reproducibles de las comprobaciones locales pertinentes y cobertura
de negocio. El porcentaje no sustituye comprobar reglas y criterios de aceptación.

### VI. Trazabilidad de requisitos

- Los identificadores **RF-01 a RF-33** y **RNF-01 a RNF-12** DEBEN conservarse exactamente.
  NO DEBEN renumerarse, reutilizarse ni inventarse códigos atribuidos a la propuesta.
- Cada incremento DEBE vincular requisitos aplicables, criterios, tareas, cambios y evidencia
  mediante referencias breves. DEBE distinguirse cumplimiento pendiente, parcial y verificado.
- Las decisiones DEBEN indicar si proceden de la propuesta, del usuario o de esta constitución.
  Las recomendaciones NO DEBEN convertirse en obligaciones sin una decisión explícita.
- Un requisito NO DEBE declararse cumplido solo por una pantalla, simulación, diseño o prueba
  aislada. Los RNF DEBEN disponer de evidencia medible; un aplazamiento NO equivale a cumplimiento.
- La infraestructura, servicios y verificaciones aplazados DEBEN permanecer identificados como
  pendientes, sin bloquear el desarrollo local con su preparación.

**Verificación:** revisar trazabilidad y estado de los requisitos del incremento. La página de
inicio deriva de la instrucción del usuario, sin atribuirle un RF inexistente.

### VII. Planificación y avance incremental

- Las especificaciones, planes y tareas DEBEN ser breves y concretos, limitados al incremento
  solicitado y sus dependencias necesarias. NO DEBE rediseñarse toda la plataforma en cada ejecución.
- Se DEBEN utilizar las tecnologías y decisiones ya elegidas. La documentación DEBE consultarse
  de forma puntual para implementar o resolver un problema concreto; NO DEBEN realizarse
  investigaciones generales ni comparaciones de alternativas.
- Las preguntas DEBEN centrarse en reglas de negocio realmente pendientes. Las decisiones
  rutinarias de implementación DEBEN resolverse con las convenciones acordadas.
- Cada incremento DEBE producir funcionalidad ejecutable localmente y conectar interfaz,
  backend y persistencia cuando corresponda a su alcance. Una simulación DEBE identificarse
  y NO DEBE presentarse como integración terminada.

**Verificación:** alcance acotado y evidencia de funcionamiento local de lo entregado.

## Alcance, arquitectura y requisitos de referencia

### Fuente y precedencia de la etapa local

La fuente original es [Propuesta_de_Proyecto.docx](../../Propuesta_de_Proyecto.docx), consultada
el 2026-11-12 en la raíz del repositorio; la ruta entonces solicitada bajo `docs/` no existía.
Sus RF/RNF se conservan como referencia. Esta enmienda registra las decisiones explícitas del
usuario del **2026-11-13** para una primera etapa funcional y local, que prevalecen sobre las
reglas anteriores que obligaban a preparar infraestructura cloud o pruebas E2E.

Se conservan el nombre Mikuna, el inicio como primer incremento y las aclaraciones de acceso
público y cobertura del 2026-11-12. **DEBE**, **NO DEBE** y **DEBEN** expresan obligaciones.
Las disposiciones futuras registradas como pendientes NO DEBEN interpretarse como trabajo actual.

Mikuna es una plataforma web de delivery multi-restaurante para clientes, restaurantes,
repartidores y administradores. Se mantienen fuera del alcance las aplicaciones móviles nativas,
múltiples pasarelas, transacciones financieras reales, contabilidad, inventario avanzado,
optimización avanzada de rutas, operación fuera del ámbito urbano considerado, otros tipos de
comercio, reembolsos y conciliación contable.

### Arquitectura local ejecutable y configuración

| Responsabilidad | Decisión vigente para esta etapa |
| --- | --- |
| Presentación | React + TypeScript ejecutado localmente; HTTP en localhost. |
| Backend | Una aplicación Java + Spring Boot organizada por funcionalidades. |
| Persistencia | PostgreSQL local mediante Spring Data JPA; base creada manualmente por el usuario. |
| Configuración | `src/main/resources/application.properties` del backend. |
| Integraciones permitidas | Cloudinary y correo (Resend según la propuesta), solo cuando sean necesarios y con cifrado. |

La conexión PostgreSQL, el puerto y, cuando correspondan, los parámetros de correo y Cloudinary
DEBEN centralizarse y quedar configurables en `src/main/resources/application.properties`.
El usuario completará los valores locales y creará manualmente la base de datos. Las credenciales
externas reales NO DEBEN incluirse en commits.

Durante esta etapa se DEBE utilizar `spring.jpa.hibernate.ddl-auto=update`. NO DEBEN añadirse
herramientas de migración por anticipado.

### Registro explícito de aplazamientos

- **Cloud y entrega:** NO DEBEN realizarse configuraciones, despliegues, investigaciones ni
  comparaciones sobre AWS u otros proveedores. Quedan aplazados infraestructura como código,
  balanceadores, escalamiento, réplicas, CDN, monitoreo cloud y CI/CD.
- **Infraestructura adicional:** Redis, RabbitMQ, workers separados, contenedores y demás
  infraestructura adicional quedan aplazados. El correo que se necesite NO DEBE exigirlos.
- **Otras integraciones:** Stripe sandbox, MapTiler y FCM quedan como referencias aplazadas
  de pagos, mapas y notificaciones push; no se permite integrarlos durante esta etapa.
  Cloudinary y correo se preparan exclusivamente cuando sean necesarios para la funcionalidad actual.
- **Verificaciones posteriores:** HTTPS del despliegue, E2E automatizadas de navegador,
  validación de infraestructura y evaluaciones de carga y disponibilidad correspondientes
  quedan pendientes. NO DEBEN prepararse certificados ni TLS local para anticiparlas.

La arquitectura cloud de la propuesta queda como antecedente aplazado, sin obligación de
prepararla durante esta etapa. Su abordaje posterior requiere una planificación explícita.

### Registro de requisitos funcionales explícitos

Los siguientes resúmenes conservan los códigos originales. El texto íntegro de la propuesta
prevalece sobre su abreviación, sujeto a las decisiones de etapa registradas arriba. La tabla
NO constituye una solicitud de implementar todos los RF ni sus dependencias en cada incremento.

| Código | Obligación de referencia |
| --- | --- |
| RF-01 | Registro e inicio de sesión de clientes, restaurantes, repartidores y administradores. |
| RF-02 | Recuperación segura de contraseña sin intervención administrativa. |
| RF-03 | Consulta y actualización del perfil sin modificar el rol. |
| RF-04 | Autorización por rol, estado de cuenta y relación con pedido, restaurante o entrega. |
| RF-05 | Solicitud de registro de restaurantes. |
| RF-06 | Aprobación o rechazo administrativo; solo restaurantes aprobados reciben pedidos. |
| RF-07 | Gestión de datos generales, horarios y disponibilidad del restaurante. |
| RF-08 | Alta, actualización y retiro de productos con categoría, precio, imagen y disponibilidad. |
| RF-09 | Búsqueda y consulta de restaurantes por criterios como nombre, categoría y ubicación. |
| RF-10 | Consulta de los productos disponibles de cada restaurante. |
| RF-11 | Agregar, modificar y eliminar productos del carrito antes de confirmar. |
| RF-12 | Registro y selección de dirección dentro de una zona de reparto cubierta. |
| RF-13 | Cálculo automático de subtotal, envío, comisión y total. |
| RF-14 | Registro del pedido pendiente validando restaurante, productos, cantidades y dirección. |
| RF-15 | Selección del método de pago al confirmar el pedido. |
| RF-16 | Pago externo: reservar al confirmar, cobrar al aceptar y liberar al rechazar o cancelar. |
| RF-17 | Registro del resultado y estado de pago asociado al pedido. |
| RF-18 | Aceptación o rechazo de pedidos pendientes por el restaurante. |
| RF-19 | Actualización desde preparación hasta listo para recoger. |
| RF-20 | Cancelación del pedido propio antes de su aceptación para preparación. |
| RF-21 | Disponibilidad del repartidor como condición para considerarlo en la asignación. |
| RF-22 | Asignación automática de una entrega lista a un repartidor habilitado y disponible. |
| RF-23 | Reasignación automática al rechazar el repartidor la entrega asignada. |
| RF-24 | Asignación manual administrativa cuando falla la automática. |
| RF-25 | Aceptación o rechazo de la entrega asignada y registro de recogida y finalización. |
| RF-26 | Registro y actualización de ubicación durante la entrega. |
| RF-27 | Consulta del estado del pedido por el cliente. |
| RF-28 | Visualización de la ubicación del repartidor durante la entrega. |
| RF-29 | Registro y presentación de avisos ante cambios relevantes de pedido o entrega. |
| RF-30 | Historial de las operaciones correspondientes a cada cliente, restaurante y repartidor. |
| RF-31 | Una única calificación de 1 a 5 para un pedido propio entregado. |
| RF-32 | Gestión administrativa de usuarios, restaurantes y repartidores. |
| RF-33 | Consulta administrativa de pedidos y entregas según su estado. |

### Objetivos y estado de requisitos no funcionales

Se conservan los objetivos originales y las aclaraciones del usuario. La evidencia local DEBE
distinguirse de la validación futura. Esta tabla define aplicabilidad, no certifica cumplimiento.

| Código | Objetivo conservado | Aplicación y evidencia durante esta etapa |
| --- | --- | --- |
| RNF-01 | Al menos el 95 % de consultas de restaurantes, catálogo y estado en menos de 2 s con 1 000 usuarios concurrentes. | Validación de carga aplazada; las comprobaciones funcionales locales no acreditan este objetivo. |
| RNF-02 | Con 3 instancias atender al menos el doble de usuarios que con 1, sin cambiar la lógica. | Escalamiento y corridas comparables aplazados. |
| RNF-03 | Atender desde caché al menos el 80 % de consultas repetidas de restaurantes y catálogo. | Caché y medición de aciertos aplazadas. |
| RNF-04 | Continuar atendiendo si cae una instancia y otras siguen disponibles. | Infraestructura redundante y prueba de caída aplazadas. |
| RNF-05 | No asignar simultáneamente una entrega a más de un repartidor. | Aplicable al implementar asignación; integración concurrente y estado persistido único. |
| RNF-06 | HTTPS cliente/plataforma y redirección o rechazo de HTTP en despliegue. | Aplazado para despliegue; desarrollo local por HTTP sin certificados ni TLS local. |
| RNF-07 | Denegar el 100 % de accesos sin autenticación o permisos a operaciones protegidas. | Aplicable; pruebas por rol, recurso, 401/403 y rutas públicas declaradas. |
| RNF-08 | No almacenar datos sensibles de tarjetas y delegar su procesamiento a una pasarela cifrada. | Prohibición de almacenamiento vigente; integración y evidencia completa aplazadas. |
| RNF-09 | Un usuario nuevo completa el pedido desde búsqueda hasta confirmación en menos de 5 min y sin ayuda. | Evaluación cronometrada con cinco personas cuando exista el flujo; E2E automatizadas aplazadas. |
| RNF-10 | Visualizar CPU, tiempo de respuesta y tasa de errores durante operación y carga. | Monitoreo cloud y evidencia del panel durante carga aplazados. |
| RNF-11 | Al menos 90 % de líneas de lógica de negocio y reporte de ramas sin umbral obligatorio. | Pruebas y reporte locales; publicación en CI aplazada y sin extender el umbral a toda la presentación. |
| RNF-12 | Cada cambio en la rama principal ejecuta pruebas y, si pasan, se despliega sin intervención manual. | CI/CD y su historial de evidencia aplazados; no bloquean la aceptación local. |

Los aproximadamente **10 000 usuarios** siguen siendo una referencia de diseño; **1 000 usuarios
concurrentes** son el objetivo futuro de RNF-01. Para la futura evaluación de carga se conservan
k6, p(95) < 2 000 ms y tasa de errores inferior al 1 % de la propuesta, sin preparar ni ejecutar
esa evaluación en esta etapa. Sus informes deberán registrar versión, datos, duración y
configuración para ser reproducibles; el perfil se definirá cuando corresponda abordarlo.

## Desarrollo incremental y criterios de aceptación

### Secuencia y alcance

El inicio conserva su condición de primer incremento, sin implicar reconstruirlo si ya existe.
Los incrementos posteriores DEBEN seleccionar únicamente el alcance solicitado y sus dependencias
necesarias. La página de inicio NO DEBE presentarse como catálogo, autenticación, pedidos o pagos
implementados si esos flujos no existen.

Cada incremento DEBE entregar funcionalidad ejecutable en local, conectando interfaz, backend
y persistencia cuando corresponda. Lo no construido o aplazado DEBE mantenerse pendiente con
una explicación breve, sin diseñar por anticipado toda la plataforma.

### Condiciones para aceptar cada incremento

- Especificación, plan y tareas breves, con alcance, criterios y referencias a RF/RNF aplicables
  o a la instrucción del usuario; dependencias y aplazamientos explícitos.
- Revisión de responsabilidades, seguridad, validación, autorización y consistencia de lo
  implementado, justificando lo que no aplique.
- Compilación y funcionamiento local comprobados; pruebas unitarias y de integración pertinentes
  aprobadas; al menos 90 % de líneas de negocio y reporte de ramas cuando exista esa lógica.
- Evidencia de los requisitos aplicables al flujo que se declara terminado. La verificación
  futura DEBE registrarse como pendiente, sin confundirse con cumplimiento.
- Los aplazamientos de esta etapa NO DEBEN generar tareas obligatorias de cloud, TLS local,
  infraestructura adicional, E2E de navegador ni CI/CD para aceptar el incremento.

### Reglas de negocio pendientes y decisiones rutinarias

Solo al abordar el incremento que las necesite se DEBEN consultar las políticas aún no definidas:
zona urbana y cobertura, fórmulas de envío y comisión, carrito con varios restaurantes, elección
de repartidor y espera sin candidatos, y aprovisionamiento de cuentas privilegiadas. No se les
asignan valores por defecto en esta constitución.

Las decisiones rutinarias de implementación DEBEN resolverse con las tecnologías y convenciones
acordadas, consultando documentación puntual cuando sea necesario. No se requiere abrir una
comparación de alternativas ni pedir confirmación de cada biblioteca, parámetro o abstracción.
OpenAPI, ADR o TDD son recomendaciones no vinculantes; NO DEBEN imponerse como preparación previa
ni como condiciones adicionales de aceptación.

## Governance

Esta constitución rige las especificaciones, planes, tareas, revisiones y entregas de Mikuna.
Las decisiones explícitas del usuario del 2026-11-13 autorizan esta etapa local y sustituyen las
obligaciones anteriores incompatibles. Los documentos anteriores que todavía exijan preparación
cloud o E2E NO DEBEN reintroducirlas como bloqueos; al planificar un incremento se DEBE aplicar
esta constitución y señalar la discrepancia pertinente sin ampliar el alcance.

Toda enmienda DEBE registrar motivo, reglas y RF/RNF afectados, impacto y aprobación explícita
del responsable del proyecto. Cuando cambie lo ya construido, DEBE indicar su adaptación necesaria.
Esta solicitud constituye la aprobación de la enmienda 2.0.0; no requiere una nueva confirmación.
Un conflicto nuevo sobre alcance o reglas de negocio DEBE consultarse cuando afecte al incremento.
Las decisiones rutinarias se resuelven conforme al principio VII.

El versionado DEBE seguir SemVer: MAJOR para eliminar o redefinir obligaciones incompatibles,
MINOR para añadir obligaciones compatibles y PATCH para aclaraciones sin cambio normativo.
Las fechas DEBEN expresarse en ISO 8601; cada enmienda DEBE actualizar versión y fecha de
modificación, conservar la ratificación original y adjuntar un informe temporal de impacto para
revisión, que se retirará antes del commit.

La revisión de cada incremento DEBE comprobar los siete principios y la evidencia aplicable.
Los incumplimientos del alcance implementado DEBEN resolverse antes de aceptarlo. Los servicios
y RNF aplazados NO DEBEN declararse cumplidos ni bloquear la entrega local por su preparación.
La infraestructura futura y las pruebas E2E se abordarán después mediante una decisión explícita
y la planificación correspondiente.

**Version**: 2.0.0 | **Ratified**: 2026-11-12 | **Last Amended**: 2026-11-13
