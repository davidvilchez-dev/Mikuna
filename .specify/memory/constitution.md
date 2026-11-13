<!--
Sync Impact Report — material temporal de revisión; retirar antes del commit.
Cambio de versión: plantilla sin versión adoptada → 1.0.0.
Motivo: primera constitución del proyecto; no existe una versión normativa anterior.
Principios sustituidos: los cinco espacios genéricos de la plantilla se reemplazan por
I. Modularidad por dominio; II. Separación de responsabilidades; III. Seguridad verificable;
IV. Consistencia de operaciones concurrentes; V. Pruebas automatizadas y cobertura;
VI. Trazabilidad de requisitos (principio adicional).
Secciones incorporadas: Alcance, arquitectura y requisitos de referencia;
Desarrollo incremental y criterios de aceptación; reglas de Governance.
Secciones normativas eliminadas: ninguna; se retiran ejemplos y marcadores de plantilla.
Decisiones consultadas y confirmadas por el usuario el 2026-11-12:
- RNF-11: cobertura de líneas >= 90 %; informar ramas sin umbral obligatorio.
- RNF-07: inicio, acceso a cuentas y RF-09/RF-10 públicos;
  operaciones personales y de gestión protegidas.
Seguimiento diferido: TODO(POLITICAS_DE_NEGOCIO) y TODO(DECISIONES_TECNICAS),
a consultar antes de planificar el incremento que dependa de cada decisión.
Plantillas, comandos y archivos de aplicación modificados: ninguno.
-->

# Mikuna Constitution

## Core Principles

### I. Modularidad por dominio

**Origen:** propuesta, secciones 1.1, 2.3, 3.2 y 3.3.1; solicitud de principios verificables.

- El backend DEBE mantener módulos con responsabilidades identificables de acceso y usuarios,
  restaurantes y catálogo, pedidos, pagos, entregas, seguimiento, notificaciones y administración.
  Cada incremento DEBE identificar los módulos que modifica y los contratos entre ellos.
- La base arquitectónica DEBE ser una aplicación Spring Boot modular, con el worker asíncrono
  descrito en la propuesta. La replicación de instancias DEBE conservar la misma lógica funcional.
  Introducir microservicios o sustituir esta base requiere una decisión consultada y documentada.
- Como regla constitucional de modularidad, los módulos DEBEN exponer sus operaciones mediante
  contratos definidos y evitar dependencias circulares. Un módulo NO DEBE modificar directamente
  los datos privados de otro sin pasar por el contrato responsable de sus invariantes.

**Verificación:** revisión de responsabilidades, dependencias y contratos de los módulos afectados,
acompañada de pruebas de sus reglas y colaboraciones. Esto permite ampliar la solución por
incrementos sin acoplar todas las funcionalidades.

### II. Separación de responsabilidades

**Origen:** arquitectura de la propuesta, secciones 3.1 a 3.3; regla constitucional solicitada.

- React con TypeScript DEBE gestionar presentación e interacción. Java con Spring Boot DEBE
  ejecutar las reglas de negocio, la autorización y la coordinación de las operaciones.
  Las validaciones del navegador NO DEBEN sustituir las comprobaciones del backend.
- La entrada HTTP/WebSocket, la lógica de negocio, la persistencia y las integraciones externas
  DEBEN tener responsabilidades separadas. Las reglas de negocio NO DEBEN depender de componentes
  visuales ni quedar implementadas únicamente en controladores o adaptadores de proveedores.
- El backend DEBE recalcular y validar importes, permisos y transiciones de estado. NO DEBE
  considerar autoritativos los precios, roles o estados enviados por el cliente.
- Los adaptadores de pagos, mapas, imágenes y comunicaciones DEBEN permitir sustituir el proveedor
  sin reescribir las reglas de negocio. Notificaciones y correo DEBEN procesarse de forma
  asíncrona mediante RabbitMQ y el worker, según la propuesta.

**Verificación:** revisión de los límites anteriores, pruebas de negocio sin servicios externos
reales y pruebas de integración de los adaptadores. No se impone un patrón de capas o una
biblioteca adicional que la propuesta no haya seleccionado.

### III. Seguridad verificable

**Origen:** RF-01 a RF-06, RNF-06 a RNF-08 y sección 3.3.1; aclaración del usuario del 2026-11-12.

- Toda comunicación cliente/plataforma DEBE utilizar HTTPS; las solicitudes HTTP DEBEN
  redirigirse o rechazarse. La autenticación DEBE usar tokens sin sesiones en memoria de la API.
- Conforme a la aclaración del usuario, el inicio, el acceso a cuentas —registro, inicio de sesión
  y recuperación de contraseña— y las consultas de restaurantes y catálogo de RF-09/RF-10 son
  públicos. Las operaciones personales y de gestión DEBEN estar protegidas. Cada contrato DEBE
  declarar su carácter público o protegido; no se permiten excepciones públicas implícitas.
- RNF-07 DEBE aplicarse al 100 % de los accesos a operaciones protegidas: rechazar los intentos
  sin autenticación o sin permisos. La autorización DEBE comprobar rol, estado de cuenta y relación
  con el recurso, según RF-04. Se DEBEN probar los rechazos 401/403 correspondientes y los accesos
  entre usuarios y restaurantes distintos. El perfil NO DEBE permitir cambiar el propio rol.
- Solo los restaurantes aprobados DEBEN recibir pedidos. El acceso público al registro NO DEBE
  permitir eludir la aprobación ni adquirir privilegios administrativos.
- Los datos sensibles de tarjetas NO DEBEN almacenarse en la plataforma; el pago DEBE delegarse
  a Stripe mediante comunicación cifrada y únicamente en su entorno de pruebas.
- Como reglas constitucionales de seguridad, las contraseñas DEBEN almacenarse mediante hashes
  adaptativos con sal; los mecanismos de recuperación DEBEN ser temporales y de un solo uso;
  los secretos NO DEBEN incluirse en el código ni exponerse en respuestas o registros. La selección
  de algoritmos y parámetros queda sujeta a consulta en el incremento de acceso.

**Verificación:** matriz de permisos y pruebas automatizadas por rol, cuenta y pertenencia;
pruebas de las rutas públicas declaradas; comprobación HTTPS; revisión del esquema, los registros
y el flujo de pago en sandbox. La delimitación pública anterior es una aclaración del usuario a
RNF-07, no una afirmación de que la propuesta enumerara esas rutas.

### IV. Consistencia de operaciones concurrentes

**Origen:** RF-13 a RF-25, RF-31, RNF-05 y secciones 1.3, 2.2, 2.3 y 3.3.1.

- Las operaciones críticas de generación de pedidos, cambios de estado, pagos y asignación de
  entregas DEBEN definir sus invariantes y comprobarlas en el backend y la persistencia.
  Como regla constitucional, las escrituras que deban confirmarse juntas en PostgreSQL DEBEN usar
  transacciones y comprobar el estado esperado; una comprobación seguida de una escritura
  desprotegida no es suficiente.
- La asignación DEBE usar la actualización condicional en PostgreSQL prevista en la propuesta.
  Ante solicitudes simultáneas, una entrega DEBE tener como máximo un repartidor activo y solo
  una de las aceptaciones concurrentes DEBE prosperar. RF-23 DEBE preservar esa unicidad al reasignar.
  Los bloqueos en memoria de una instancia NO DEBEN ser la única protección de estas reglas.
- El flujo nominal DEBE respetar: Pendiente → Aceptado → En preparación → Listo para recoger →
  Asignado → Recogido → Entregado. Desde Pendiente se permite Rechazado por el restaurante o
  Cancelado por su cliente antes de la aceptación. El rechazo de una asignación activa la
  reasignación, no el rechazo del pedido. No se permiten saltos o retrocesos no especificados.
- El pago DEBE autorizarse al confirmar, capturarse al aceptar y liberar la reserva al rechazar
  o cancelar antes de la aceptación. El resultado DEBE quedar asociado al pedido. Los webhooks
  repetidos DEBEN producir un único efecto de negocio, conforme a la propuesta.
- Como regla constitucional de consistencia, los reintentos de una misma operación lógica NO
  DEBEN duplicar pedidos, cobros o asignaciones. Las carreras entre aceptación y cancelación y
  los fallos entre persistencia y pasarela DEBEN tener un tratamiento definido y probado antes
  de aceptar el incremento. No se presupone una transacción atómica entre PostgreSQL y Stripe.
- RF-31 DEBE garantizar una única calificación de 1 a 5 por pedido propio entregado, incluso
  ante solicitudes simultáneas.

**Verificación:** pruebas de integración con solicitudes realmente concurrentes y comprobación del
estado persistido, incluidos múltiples contendientes para una entrega, aceptación frente a
cancelación y duplicación de eventos de pago. Los mecanismos concretos de reintento y recuperación
se consultan al planificar el incremento correspondiente.

### V. Pruebas automatizadas y cobertura

**Origen:** RNF-11, secciones 1.4, 2.3 y 3.3.1; métrica confirmada por el usuario el 2026-11-12.

- La lógica de negocio DEBE tener pruebas automatizadas con cobertura de líneas de al menos
  **90 %**. La cobertura de ramas DEBE informarse, sin umbral obligatorio. La medición DEBE
  abarcar la lógica de negocio existente, también en el frontend cuando este la contenga.
- El reporte DEBE identificar el código medido, líneas cubiertas y totales, porcentaje y
  exclusiones justificadas. No se permite excluir reglas, validaciones o transiciones de negocio
  para alcanzar el umbral ni diluirlo con código generado. La agregación entre lenguajes y las
  herramientas se definen mediante consulta antes del primer incremento con lógica de negocio.
- Cada incremento DEBE incluir pruebas unitarias de sus reglas, pruebas de integración de las
  colaboraciones que introduce y pruebas de extremo a extremo de los flujos principales que
  habilita. Seguridad, concurrencia y pagos DEBEN incluir los casos negativos y fallos aplicables.
- La integración continua DEBE ejecutar las pruebas y publicar la cobertura de negocio. Un
  resultado inferior al 90 % o una prueba fallida DEBE impedir aceptar el incremento y desplegarlo.
  Un inicio sin lógica de negocio DEBE registrar esta medición como no aplicable, con justificación;
  NO DEBE reportarse como cobertura del 100 % ni como cumplimiento completo de RNF-11.

**Verificación:** resultados reproducibles de pruebas y reporte de cobertura en CI. El porcentaje
no sustituye la comprobación de las reglas y los criterios de aceptación.

### VI. Trazabilidad de requisitos

**Origen:** códigos de las secciones 1.1, 1.2 y 1.4; regla constitucional solicitada por el usuario.

- Los identificadores **RF-01 a RF-33** y **RNF-01 a RNF-12** DEBEN conservarse exactamente.
  No se permite renumerarlos, reutilizarlos ni inventar códigos de la propuesta para requisitos
  nuevos. Los resúmenes de esta constitución NO DEBEN sustituir el detalle de la fuente.
- Cada incremento DEBE vincular sus requisitos aplicables con criterios de aceptación, tareas,
  cambios y evidencia de verificación. La relación DEBE poder recorrerse en ambos sentidos y
  distinguir cumplimiento pendiente, parcial y verificado.
- Cada decisión DEBE indicar si procede de la propuesta, de una aclaración del usuario o de una
  regla constitucional. Las recomendaciones DEBEN identificarse como tales y no convertirse en
  obligaciones ni requisitos de producto sin una decisión explícita.
- Un requisito NO DEBE declararse cumplido solo por tener una pantalla, una interfaz simulada,
  un diseño o una prueba aislada. Los RNF DEBEN contar con su evidencia medible, incluida la
  configuración y carga usadas para obtenerla.

**Verificación:** revisión de trazabilidad en cada incremento y ausencia de requisitos declarados
cumplidos sin evidencia. La página de inicio se identifica por la instrucción del usuario,
sin atribuirle un RF inexistente.

## Alcance, arquitectura y requisitos de referencia

### Fuente y naturaleza de las reglas

La fuente primaria es [Propuesta_de_Proyecto.docx](../../Propuesta_de_Proyecto.docx), consultada
el 2026-11-12. La ruta solicitada `docs/Propuesta_de_Proyecto.docx` no existe en el espacio de
trabajo; se utilizó el documento del mismo nombre presente en la raíz, sin moverlo ni modificarlo.

Son **requisitos explícitos de la propuesta** sus RF, RNF, flujo de estados, alcance y arquitectura.
Son **decisiones del usuario** el nombre Mikuna, el inicio como primer incremento, la entrega
posterior por incrementos acotados y las dos aclaraciones de cobertura y acceso público anteriores.
Son **reglas constitucionales** las condiciones de revisión y verificación añadidas aquí para
hacer comprobables los principios solicitados. **DEBE**, **NO DEBE** y **DEBEN** expresan
obligaciones; una **recomendación** no bloquea la aceptación por sí misma.

Mikuna es una plataforma web de delivery multi-restaurante para clientes, restaurantes,
repartidores y administradores. Según la sección 2.4, quedan fuera las aplicaciones móviles
nativas, múltiples pasarelas, transacciones financieras reales, contabilidad, inventario avanzado,
optimización avanzada de rutas, operación fuera del ámbito urbano considerado, otros tipos de
comercio, reembolsos y conciliación contable.

### Arquitectura explícita de la propuesta

| Responsabilidad | Base que se debe conservar |
| --- | --- |
| Presentación | React + TypeScript; archivos estáticos en Amazon S3 distribuidos por CloudFront. |
| API y cómputo | Java + Spring Boot; REST y WebSocket cuando corresponda; Docker sobre Linux en EC2; tokens sin sesiones en memoria. |
| Persistencia | Amazon RDS for PostgreSQL con JDBC/SQL; escrituras y decisiones transaccionales en la base principal; réplica para lecturas que toleren retraso. |
| Caché y tiempo real | Redis, con ElastiCache como servicio objetivo; consultas frecuentes, última ubicación y propagación de eventos entre instancias. |
| Ubicación persistida | PostgreSQL registra la ubicación del repartidor al recoger y entregar; las actualizaciones frecuentes se mantienen en Redis. |
| Tareas asíncronas | RabbitMQ por AMQP en Docker sobre EC2; worker Java + Spring Boot en Docker para notificaciones y correo. |
| Servicios externos | Stripe sandbox; Cloudinary para imágenes; MapTiler con datos compatibles con OpenStreetMap; FCM para push y Resend para correo. |
| Distribución y disponibilidad | Dominio en Namecheap, certificado de ACM, CloudFront, ALB y EC2 Auto Scaling; ALB en al menos dos zonas y distribución de instancias entre dos zonas. |
| Entrega y evaluación | GitHub y GitHub Actions para compilación, pruebas y despliegue; CloudWatch para métricas y k6 desde un equipo independiente para carga. |

La capacidad inicial reducida, el aumento de recursos durante pruebas y la activación de la réplica
cuando corresponda siguen las consideraciones académicas de costo de la propuesta. No modifican
los objetivos de aceptación. Los aproximadamente **10 000 usuarios son una referencia de diseño**
de la sección 2.2; **1 000 usuarios concurrentes son el objetivo de validación de RNF-01**.
La ejecución incremental no obliga a desplegar toda esta infraestructura en el primer incremento,
pero no permite declarar satisfechos sus RNF sin implementarla y medirlos.

### Registro de requisitos funcionales explícitos

Los siguientes resúmenes conservan los códigos originales. El texto íntegro de la sección 1.1
prevalece sobre cualquier abreviación de esta tabla.

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

### Objetivos y evidencia de requisitos no funcionales

Esta tabla conserva los objetivos de las secciones 1.2 y 1.4. Las aclaraciones de RNF-07 y RNF-11
proceden del usuario y se registran expresamente, sin renumerar los requisitos.

| Código | Objetivo obligatorio | Evidencia requerida |
| --- | --- | --- |
| RNF-01 | Al menos el 95 % de consultas de restaurantes, catálogo y estado del pedido en menos de 2 s con 1 000 usuarios concurrentes. | Informe k6 con 1 000 usuarios virtuales y p(95) < 2 000 ms. |
| RNF-02 | Con 3 instancias atender al menos el doble de usuarios que con 1, sin cambiar la lógica funcional. | Dos corridas comparables con el mismo umbral e historial de escalamiento. |
| RNF-03 | Atender desde caché al menos el 80 % de consultas repetidas de restaurantes y catálogo. | Porcentaje de aciertos y comparación de tiempos con y sin caché. |
| RNF-04 | Continuar atendiendo si cae una instancia y otras siguen disponibles. | Detener una instancia durante carga y comprobar la continuidad del servicio. |
| RNF-05 | No asignar simultáneamente una entrega a más de un repartidor. | Integración concurrente: una aceptación exitosa y una única asignación activa persistida. |
| RNF-06 | HTTPS para toda comunicación cliente/plataforma; redirigir o rechazar HTTP. | Solicitud HTTP, comprobación HTTPS y certificado válido; la sección 3.3.1 prevé redirección. |
| RNF-07 | Denegar el 100 % de accesos sin autenticación o permisos a operaciones protegidas; rutas públicas delimitadas por el usuario en el principio III. | Pruebas automatizadas por rol y recurso, rechazos 401/403 y acceso a rutas públicas declaradas. |
| RNF-08 | No almacenar datos sensibles de tarjetas; delegar su procesamiento a la pasarela mediante cifrado. | Revisión del esquema sin campos de tarjeta y flujo con la pasarela en sandbox. |
| RNF-09 | Un usuario nuevo completa el pedido desde búsqueda hasta confirmación en menos de 5 min y sin ayuda. | Prueba cronometrada con cinco personas y prueba de extremo a extremo del flujo completo. |
| RNF-10 | Visualizar CPU, tiempo de respuesta y tasa de errores durante operación y carga. | Captura del panel de monitoreo durante la prueba de carga. |
| RNF-11 | Pruebas automatizadas con al menos 90 % de líneas de negocio; informar ramas sin umbral, según decisión del usuario. | Reporte reproducible generado en integración continua, con alcance y exclusiones. |
| RNF-12 | Cada cambio integrado a la rama principal ejecuta pruebas y, si pasan, se despliega sin intervención manual. | Historial de ejecuciones de GitHub Actions con pruebas y despliegue automático. |

Adicionalmente, la sección 3.3.1 fija para k6 una **tasa de errores inferior al 1 %**. Este umbral
DEBE conservarse en las pruebas de carga, con su procedencia arquitectónica, sin inventar otro RNF.
Cada informe DEBE registrar versión evaluada, datos, duración, configuración e instancias para
permitir reproducir y comparar el resultado. La duración y el perfil de carga se consultarán al
preparar las pruebas; no se atribuyen a la propuesta valores que esta no especifica.

## Desarrollo incremental y criterios de aceptación

### Secuencia y alcance de los incrementos

1. **Primer incremento: página de inicio.** Se DEBE especificar y construir primero el inicio en
   React con TypeScript, con alcance visual e interacciones expresamente delimitados. La política
   pública confirmada se aplica a esta página. Su entrega NO DEBE presentarse como implementación
   del catálogo, autenticación, pedidos o pagos si esos flujos aún no existen. El uso de datos
   simulados, cuando se acuerde, DEBE identificarse en la especificación y en la evidencia.
2. **Incrementos funcionales posteriores.** Cada uno DEBE seleccionar un conjunto acotado de RF,
   sus dependencias y RNF aplicables. El tamaño, orden y criterios de aceptación se acuerdan en su
   especificación; esta constitución no presume un orden de implementación para los 33 RF.
3. **Evaluación acumulativa.** Lo aún no construido DEBE permanecer trazado como pendiente.
   Las pruebas de carga y del flujo completo se realizan cuando existan los componentes necesarios;
   la planificación DEBE identificar el incremento que aportará cada evidencia pendiente.

La instrucción de construir primero el inicio es una regla de secuencia, no una solicitud de
implementarlo durante esta elaboración de la constitución.

### Condiciones para aceptar cada incremento

- Especificación con alcance, exclusiones, criterios comprobables y trazabilidad a RF/RNF o a la
  instrucción de inicio. Las dependencias y decisiones pendientes DEBEN ser visibles.
- Revisión de modularidad, responsabilidades, seguridad y consistencia en los componentes
  afectados, con justificación de los puntos no aplicables.
- Pruebas del incremento y regresión de los flujos afectados exitosas; cobertura de negocio de
  al menos 90 % de líneas y reporte de ramas cuando exista esa lógica.
- Evidencia de los RNF aplicables, o registro de su verificación pendiente y del incremento
  previsto para completarla. No se permite omitir una comprobación necesaria para el flujo que
  se declara terminado ni tratar una verificación pendiente como cumplimiento.
- Integración y despliegue automáticos conforme a RNF-12. Las evidencias DEBEN vincularse a la
  versión entregada. Esta constitución no crea ni ejecuta la infraestructura de entrega.

### Recomendaciones y decisiones aún no definidas

**Recomendaciones no vinculantes:** documentar contratos REST con OpenAPI, registrar las decisiones
arquitectónicas mediante ADR, usar TDD y automatizar las comprobaciones de dependencias modulares.
La propuesta no exige esas prácticas o herramientas concretas; su adopción se decide en la
planificación y no se utiliza por sí sola como condición de aceptación.

**TODO(POLITICAS_DE_NEGOCIO):** consultar y registrar, antes del incremento afectado, los límites
de la zona urbana y cobertura, las fórmulas de envío y comisión, si el carrito admite productos
de varios restaurantes, los criterios de elección de repartidor y espera sin candidatos, y el
aprovisionamiento de cuentas privilegiadas. La propuesta no fija esas políticas y esta constitución
no les asigna valores por defecto. La consulta de cada punto se difiere al incremento que lo necesite.

**TODO(DECISIONES_TECNICAS):** consultar y registrar, antes del incremento afectado, las versiones
de plataforma, bibliotecas y herramientas no fijadas; formato y ciclo de vida de tokens y recuperación;
herramientas y agregación de cobertura; tratamiento de fallos y reintentos de pagos y mensajes;
caducidad e invalidación de caché; frecuencia de ubicación; perfil de carga y parámetros de
escalamiento y costo. No se presupone JWT, un ORM, una biblioteca de estado o un patrón distribuido
específico. Las decisiones que contradigan una base explícita requieren resolver el conflicto
mediante el procedimiento de enmienda.

## Governance

Esta constitución rige las especificaciones, planes, tareas, revisiones y entregas de Mikuna.
No autoriza a reducir silenciosamente el alcance ni los umbrales de la propuesta. Ante un conflicto
con la fuente o una ambigüedad, se DEBE consultar al responsable del proyecto y registrar la
resolución con su procedencia antes de implementar la decisión afectada.

Toda enmienda DEBE incluir motivo, reglas y RF/RNF afectados, impacto en incrementos y evidencia,
aprobación explícita del responsable del proyecto y, cuando corresponda, plan de adaptación de lo
ya construido. Las recomendaciones solo adquieren carácter obligatorio mediante una decisión
documentada; si cambian estas reglas, se requiere una enmienda.

El versionado DEBE seguir SemVer: MAJOR para eliminar o redefinir obligaciones de forma
incompatible; MINOR para añadir principios, secciones u obligaciones compatibles; PATCH para
aclaraciones y correcciones sin cambiar obligaciones. La adopción inicial es **1.0.0**. Las fechas
DEBEN expresarse en ISO 8601 y cada enmienda DEBE actualizar la fecha de modificación y su informe
de impacto. La fecha de ratificación original se conserva.

Cada revisión de incremento DEBE comprobar los seis principios, la trazabilidad y la evidencia de
calidad. Los incumplimientos DEBEN resolverse antes de aceptar el alcance afectado; una excepción
no puede rebajar el 90 % de líneas ni otro requisito explícito sin una enmienda aprobada que
identifique el cambio de alcance. Los TODO de decisiones futuras no autorizan a escoger valores
silenciosamente ni a omitir requisitos.

**Version**: 1.0.0 | **Ratified**: 2026-11-12 | **Last Amended**: 2026-11-12
