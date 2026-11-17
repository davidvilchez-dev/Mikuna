# Feature Specification: Registro e inicio de sesión de Mikuna

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-13

**Status**: Ready for planning

**Input**: RF-01 y autorización mínima de RF-04, conectados al inicio existente y ejecutables
íntegramente en local según la constitución 2.0.0. RF-02 y RF-03 quedan fuera del incremento.

## Clarifications

### Session 2026-11-13

- Q: ¿Un mismo correo podrá utilizarse para varios roles, por ejemplo cliente y repartidor? → A: No. Un correo identifica una cuenta con un único rol; para otro rol se necesita otro correo (opción A, confirmada por el usuario).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear una cuenta desde el inicio (Priority: P1)

Como visitante, quiero registrarme como cliente, restaurante o repartidor desde la página pública
para disponer de una cuenta persistente y acceder con mis credenciales.

**Why this priority**: Completa el primer flujo real de RF-01 conservando el inicio existente.

**Independent Test**: Desde el inicio, registrar cada tipo público con datos distintos y comprobar
que la cuenta persiste tras reiniciar la aplicación. La comprobación del navegador será manual.

**Acceptance Scenarios**:

1. **Given** el inicio público existente, **When** se elige «Crear cuenta» o el acceso de registro
   de restaurantes o repartidores, **Then** se abre el formulario correspondiente, se puede volver
   al inicio y sus secciones, imágenes y navegación siguen disponibles.
2. **Given** datos válidos y un correo no registrado, **When** se confirma el registro de cliente,
   restaurante o repartidor, **Then** se guarda exactamente una cuenta del tipo elegido y se
   confirma el resultado con un acceso claro para iniciar sesión.
3. **Given** datos incompletos, correo inválido, contraseña no admitida o correo ya registrado,
   **When** se envía el formulario, incluso seleccionando otro rol para el correo existente,
   **Then** se explica el error y no se crea una cuenta adicional ni se añade o cambia el rol.
4. **Given** un registro público, **When** se intenta indicar un rol administrativo, un estado
   privilegiado o una aprobación de restaurante, **Then** la solicitud se rechaza sin otorgarlos.
5. **Given** una cuenta nueva de restaurante, **When** termina el registro, **Then** se informa
   que su habilitación comercial está pendiente; registrarse no equivale a estar aprobado.

### User Story 2 - Iniciar y cerrar sesión (Priority: P1)

Como titular de una cuenta habilitada de cualquiera de los cuatro roles, quiero identificarme
y reconocer mi sesión sin acceder a funciones que todavía no están implementadas.

**Why this priority**: Hace utilizable el registro y permite demostrar el acceso de los cuatro roles.

**Independent Test**: Con cuentas existentes de cada rol, iniciar sesión desde el inicio, comprobar
nombre, rol y estado propios, y cerrar sesión; no requiere perfil editable ni paneles de negocio.

**Acceptance Scenarios**:

1. **Given** una cuenta habilitada de cliente, restaurante, repartidor o administrador,
   **When** se introducen correo y contraseña correctos, **Then** se inicia una sesión con vigencia
   limitada y se muestra su identidad, rol y estado, con una acción para cerrarla.
2. **Given** una cuenta de restaurante pendiente de aprobación comercial, **When** sus credenciales
   son válidas y la cuenta está habilitada para acceder, **Then** puede autenticarse y ver esa
   condición, sin adquirir aprobación ni permisos para recibir pedidos.
3. **Given** credenciales incorrectas o una cuenta no habilitada para acceder, **When** se intenta
   iniciar sesión, **Then** no se concede acceso; el error de credenciales no revela si el correo
   existe y no se exponen detalles internos.
4. **Given** una sesión abierta, **When** su titular la cierra o vence su vigencia, **Then** la
   interfaz deja de mostrarla como activa y exige autenticarse para volver a consultar su sesión.
5. **Given** una persona sin sesión, **When** visita el inicio o las pantallas de acceso,
   **Then** puede utilizarlos sin autenticación obligatoria.

### User Story 3 - Mantener los límites de acceso (Priority: P1)

Como responsable de Mikuna, quiero que cada sesión represente exclusivamente a su titular y sus
permisos vigentes, y que las cuentas administradoras se creen solo bajo control local autorizado.

**Why this priority**: RF-01 no puede aceptarse si permite suplantación o elevación de privilegios.

**Independent Test**: Preparar una cuenta administradora mediante el procedimiento local acordado
y comprobar acceso propio, rechazo de identidad ajena y denegación de credenciales de sesión
inválidas. La preparación de datos para pruebas no incorpora gestión de cuentas al producto.

**Acceptance Scenarios**:

1. **Given** una instalación local, **When** el responsable ejecuta el procedimiento controlado de
   alta administrativa con sus propios valores, **Then** obtiene una cuenta que puede iniciar
   sesión; no existe un alta administrativa pública ni una contraseña real predeterminada versionada.
2. **Given** una consulta de sesión sin credencial, con credencial manipulada o vencida,
   **When** se solicita información protegida, **Then** se deniega el acceso como no autenticado (401).
3. **Given** una cuenta que dejó de estar habilitada aunque conserva una credencial de sesión,
   **When** intenta utilizar el acceso protegido, **Then** se deniega por falta de autorización (403).
4. **Given** una sesión válida, **When** se altera desde el cliente el rol, estado o identificador
   de otra cuenta, **Then** no se elevan permisos ni se devuelve información de otra persona.

### Edge Cases

- Dos registros simultáneos con el mismo correo, incluidas diferencias de mayúsculas o espacios
  exteriores o la selección de roles diferentes: se crea como máximo una cuenta con un único rol
  y la otra solicitud recibe un error controlado, sin añadir o cambiar roles.
- Caída del backend o fallo al guardar: se informa que la operación no se completó, sin confirmar
  un registro ni una sesión inexistentes; el usuario puede reintentar.
- Envíos repetidos del formulario: no crean cuentas duplicadas ni confirman éxitos sin persistencia.
- Navegación directa a registro o acceso, recarga y retorno al inicio: las pantallas siguen
  disponibles y una sesión vencida no se presenta como válida.
- Restaurantes pendientes: autenticación y aprobación comercial se distinguen; esta entrega
  no implementa aprobación, recepción de pedidos ni pantallas administrativas de negocio.

## Requirements *(mandatory)*

### Functional Requirements

Las referencias siguientes desarrollan los RF originales sin renumerarlos ni atribuirles nuevos
códigos. Los criterios de cada historia y los casos límite son su evidencia de aceptación prevista.

- **RF-01 — Navegación:** conservar el inicio y conectar registro e inicio de sesión desde él;
  conectar también las llamadas de registro de restaurantes y repartidores. Mantener como futuras
  las acciones de catálogo, pedidos y demás funciones ajenas al incremento.
- **RF-01 — Registro público:** admitir cliente, restaurante y repartidor con nombre de titular,
  correo, contraseña y confirmación. El tipo de cuenta debe ser visible y no incluir administrador.
- **RF-01 — Validación:** exigir nombre no vacío de hasta 100 caracteres, correo de formato válido
  de hasta 254 caracteres y contraseña no vacía de hasta 128 caracteres, con confirmación
  coincidente. *(Decisión del usuario del 2026-11-17: el mínimo de 12 caracteres se suspende
  temporalmente para agilizar las pruebas; se restaurará antes de cerrar el proyecto.)*
  Aplicar también las validaciones en el sistema que procesa y guarda la cuenta.
- **RF-01 — Unicidad y persistencia:** tratar el correo sin espacios exteriores y sin distinguir
  mayúsculas para unicidad y acceso; conservar las cuentas tras reiniciar y garantizar unicidad
  también ante registros concurrentes. Un correo identifica una cuenta con un único rol;
  registrarlo con otro rol debe rechazarse sin modificar la cuenta existente. Para otro rol se
  necesita otro correo. No guardar ni devolver contraseñas legibles.
- **RF-01 — Acceso:** permitir inicio de sesión por correo y contraseña a los cuatro roles,
  mediante credenciales de sesión con vencimiento; informar errores sin revelar contraseñas,
  secretos ni la existencia de un correo en los fallos de autenticación.
- **RF-01 — Sesión propia:** mostrar únicamente nombre, correo, rol y estado del titular
  autenticado, así como cerrar su sesión en el navegador. Esta vista acredita el acceso y no
  constituye la consulta o edición de perfil de RF-03 ni un panel de negocio por rol.
- **RF-01 — Administradores:** proporcionar un procedimiento local controlado por el responsable
  para crear sus cuentas. No incorporar alta pública, asignación libre de ese rol ni una pantalla
  para gestionar administradores. La política fue confirmada por el usuario el 2026-11-13.
- **RF-04 — Autorización mínima:** determinar identidad, rol y estado con información confiable;
  rechazar accesos protegidos sin autenticación (401), cuentas no autorizadas (403), manipulación
  de privilegios y consulta de identidad ajena. Aplicar la comprobación de estado en cada acceso
  protegido. No construir operaciones futuras solo para demostrar permisos.
- **RF-04 — Delimitación:** la autorización de esta entrega se aplica a los accesos implementados.
  Las relaciones con pedidos, restaurantes operativos y entregas siguen pendientes; RF-04 no se
  declara completo. Registrar una cuenta de restaurante tampoco declara cumplidos RF-05/RF-06.

### Key Entities *(include if feature involves data)*

- **Cuenta:** identidad persistente con nombre de titular, correo único, contraseña protegida,
  un rol y estado de habilitación de acceso. Una cuenta de restaurante distingue además su
  condición comercial pendiente; no implica crear catálogo, pedidos ni administración comercial.
- **Credencial de sesión:** acredita temporalmente a una cuenta autenticada; permite identificar
  al titular y comprobar vigencia, rol y estado sin aceptar privilegios elegidos por el navegador.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001:** Los tres tipos de registro público pueden completarse desde el inicio; cada cuenta
  creada puede volver a autenticarse tras reiniciar la aplicación con sus datos conservados.
- **SC-002:** Los cuatro roles pueden iniciar y cerrar sesión con cuentas habilitadas; cada
  sesión muestra exclusivamente la identidad y estado de su titular.
- **SC-003:** El 100 % de los casos de aceptación de credenciales inválidas, vencidas, cuentas
  inhabilitadas, suplantación y elevación de privilegios se rechaza sin revelar datos ajenos.
- **SC-004:** En los casos de correo duplicado, incluidos envíos concurrentes, nunca se crea
  más de una cuenta para el mismo correo normalizado ni se añade o cambia su rol por otro registro.
- **SC-005:** El inicio y sus cinco secciones públicas siguen accesibles sin sesión; los accesos
  de cuentas conducen a funciones reales y los demás flujos pendientes no se presentan como listos.

## Assumptions

- **Decisión confirmada:** el usuario eligió crear las cuentas administradoras mediante un
  procedimiento local bajo su control, sin pantalla pública ni gestión de administradores.
- **Identidad confirmada:** una cuenta tiene un único rol y un correo no puede registrarse en
  varios roles. Para otro rol se necesita otro correo, según la respuesta A del usuario.
- **Supuestos de alcance:** clientes y repartidores se habilitan al registrarse, sin verificación por correo.
  Las cuentas de restaurante pueden autenticarse, pero su aprobación comercial queda pendiente
  de RF-06. Estos supuestos no autorizan a recibir pedidos ni ejecutar entregas en este incremento.
- **Convenciones propuestas:** nombre de titular, correo y contraseña son los datos mínimos;
  los límites de validación anteriores son convenciones de este incremento, no requisitos
  atribuidos a la propuesta original. No se solicitan documentos, imágenes ni datos comerciales.
- **Dependencias:** reutilizar el inicio existente y disponer de PostgreSQL local con una base
  creada manualmente por el usuario. Conectar interfaz, procesamiento y persistencia reales;
  no sustituirlos por simulaciones para dar el flujo por terminado.
- **Restricciones explícitas del usuario:** React, Spring Boot y PostgreSQL en local, HTTP en
  localhost, configuración del backend centralizada en `src/main/resources/application.properties`
  y `spring.jpa.hibernate.ddl-auto=update`. El usuario completa los valores locales.
  Son decisiones heredadas, no un diseño técnico nuevo de esta especificación.
- **Verificación local:** RNF-07 aplica a los accesos implementados; RNF-11 conserva al menos
  90 % de líneas de lógica de negocio y reporte de ramas sin umbral. Utilizar pruebas unitarias
  e integración acotadas, con JUnit y Mockito en backend según corresponda, compilación y
  comprobación funcional manual; no repetir comprobaciones aprobadas sin una causa concreta.
- **Fuera del incremento:** RF-02, RF-03, administración de cuentas, aprobación de restaurantes,
  catálogo, pedidos, pagos y entregas. No preparar cloud, contenedores, migraciones, CI/CD,
  TLS local, correo ni Cloudinary. No instalar, configurar ni ejecutar Playwright ni sustituirlo
  por otra automatización E2E de navegador. Los RNF aplazados conservan el estado pendiente de
  la constitución 2.0.0, sin bloquear la entrega local ni declararse cumplidos.
- **Trazabilidad de origen:** [propuesta original](../../Propuesta_de_Proyecto.docx), RF-01,
  RF-04 y límites RF-05/RF-06; [constitución 2.0.0](../../.specify/memory/constitution.md);
  solicitud y aclaración del usuario del 2026-11-13. No se modifica la especificación histórica
  del inicio: su comportamiento provisional de registro se reemplaza solo por este nuevo flujo.

- **Rutas públicas posteriores:** la spec 004 añade `GET /api/public/**` (solo lectura de restaurantes y
  catálogos, RF-09 y RF-10). Véase [catalog-api](../004-catalogo-consulta/contracts/catalog-api.md).
