# Feature Specification: Cuentas y acceso de Mikuna

**Feature Branch**: No creada; no existe repositorio Git ni hook de creación en este espacio de trabajo.

**Feature Directory**: `specs/002-cuentas-acceso`

**Created**: 2026-11-13

**Status**: Clarified — cinco respuestas integradas; políticas de negocio resueltas. DT-01 a DT-03 conservan decisiones técnicas pendientes de consulta al planificar.

**Input**: User description: "Crear el siguiente incremento de Mikuna: cuentas y acceso, RF-01 a RF-04; revisar constitución, propuesta, aclaraciones y código; reutilizar la interfaz pública; definir historias y aceptación de frontend, backend y persistencia; cubrir errores, pertenencia y recuperación inválida o expirada; separar autorización verificable ahora de comprobaciones futuras y registrar políticas no definidas."

**Fuentes y precedencia**:

- [Constitución v1.0.0](../../.specify/memory/constitution.md), principios I a VI y decisiones pendientes.
- [Propuesta](../../Propuesta_de_Proyecto.docx), secciones 1.1, 1.2, 1.4, 2.3 y 3.3.1. Conserva los cuatro actores y los códigos RF/RNF originales.
- [Aclaraciones del inicio](../001-inicio-publico/spec.md#clarifications), [ampliación visual](../001-inicio-publico/ampliacion-visual.md) y [contexto vigente](../../docs/CONTEXTO_PARA_CONTINUAR.md). Las decisiones más recientes prevalecen sobre las descripciones históricas: cinco secciones públicas continuas y footer; listado de restaurantes retirado.
- Instrucción de este incremento: convierte los accesos a cuentas en flujos funcionales y exige registrar las políticas pendientes, sin resolverlas silenciosamente.
- [Revisión del código existente](existing-state.md), realizada el 2026-11-13. Hay presentación pública reutilizable y avisos temporales; no existen flujos funcionales de RF-01 a RF-04. Las dependencias declaradas no acreditan funcionalidades.

Los identificadores `ACC-*`, `POL-*`, `DT-*`, `US*` y `SC-*` son locales de este incremento. No crean ni renumeran RF/RNF de la propuesta. Todos los requisitos nuevos están pendientes de implementación y verificación.

## Clarifications

### Session 2026-11-13

- Q: ¿Cómo deben crearse la primera cuenta administrativa y las siguientes? (POL-01) → A: Opción A. El responsable del despliegue crea todas las cuentas administrativas mediante un procedimiento privado, con registro de cada alta y sin contraseñas predeterminadas compartidas. No se incluye una interfaz web de alta administrativa.
- Q: ¿Cuándo debe crearse la cuenta de restaurante y qué acceso debe tener antes de su aprobación? (POL-02) → A: Opción A. La cuenta se crea antes de la solicitud y permite inicio de sesión, perfil propio y recuperación aunque la solicitud esté pendiente o rechazada; las operaciones comerciales requieren aprobación. RF-05/RF-06 se mantienen para un incremento posterior.
- Q: ¿Una misma dirección de correo debe identificar una cuenta con un único rol o permitir varios roles? (RF-01) → A: Opción A. Un correo identifica una única cuenta con un solo rol. Para participar con otro rol se necesita otra cuenta con un correo distinto; no hay selección ni cambio de rol en este incremento.
- Q: ¿Cuánto tiempo debe ser válido el enlace para restablecer la contraseña? (RF-02) → A: Opción A. Treinta minutos desde que se emite la recuperación; el servidor comprueba el vencimiento al confirmar. Es de un solo uso y solicitar otro enlace invalida el anterior.
- Q: ¿Qué límites temporales deben aplicarse al inicio de sesión y a las solicitudes de recuperación? (RF-01/RF-02) → A: Opción B. Tras 10 fallos consecutivos de inicio de sesión en 15 minutos, pausar los intentos durante 15 minutos; admitir un máximo de 5 solicitudes de recuperación por correo cada 15 minutos. Aplicar los límites al correo normalizado, exista o no la cuenta, sin revelar su existencia; los controles adicionales por origen se concretan al planificar.

## User Scenarios & Testing *(mandatory)*

Cada historia cubre presentación, validación autoritativa del servidor y persistencia. Una pantalla simulada no satisface la aceptación. Los escenarios son criterios futuros; no se han ejecutado durante esta especificación. Se prueban de forma independiente con cuentas de prueba preparadas, sin depender de pedidos, restaurantes operativos o entregas. Las prioridades ordenan el trabajo; la protección de US5 es obligatoria desde la primera operación personal habilitada.

### User Story 1 - Crear una cuenta según el actor permitido (Priority: P1)

Como visitante cliente, restaurante o repartidor, quiero crear mi cuenta mediante el acceso correspondiente y conocer su resultado. El responsable del despliegue crea la primera cuenta administrativa y las siguientes mediante un procedimiento privado, con registro de cada alta y sin contraseñas predeterminadas compartidas; nunca por libre elección de privilegios en un registro público.

**Why this priority**: RF-01 habilita la identidad necesaria para las demás operaciones y debe impedir privilegios indebidos desde el alta.

**Independent Test**: Con identidades nuevas y duplicadas, recorrer el registro de cada actor y comprobar el resultado persistido. Probar el alta administrativa mediante el procedimiento privado de POL-01 y el rechazo del alta desde el registro público. Probar también el alta de restaurante antes de cualquier solicitud, su acceso personal y la ausencia de aprobación automática según POL-02; las cuentas preparadas solo permiten probar sus otros flujos.

**Acceptance Scenarios**:

1. **US1.1 — Given** un visitante cliente, restaurante o repartidor y datos válidos conforme a ACC-002, **When** completa el registro y luego inicia sesión, **Then** existe una única cuenta persistida con el actor autorizado, el inicio funciona y no recibe privilegios de otro actor.
2. **US1.2 — Given** campos obligatorios ausentes, correo mal formado, contraseña fuera de los límites o confirmación distinta, **When** se intenta el registro desde la pantalla o directamente ante el servidor, **Then** se rechaza, se indican los campos corregibles y no se crea una cuenta parcial ni se conservan contraseñas en la pantalla tras el envío.
3. **US1.3 — Given** una cuenta existente, **When** se vuelve a registrar su correo, incluso con espacios exteriores o diferencias de mayúsculas, **Then** se informa que no se pudo completar el registro y se ofrecen inicio y recuperación sin duplicar, sobrescribir ni cambiar el rol de la cuenta existente. El rechazo también se aplica si se intenta registrar ese correo con otro rol, incluso por el procedimiento privado de alta administrativa: no se crea otra cuenta ni se cambia o añade un rol a la existente. Dos registros simultáneos del mismo correo crean como máximo una cuenta.
4. **US1.4 — Given** un registro público, **When** se manipulan el actor, el rol, el estado, el identificador o una supuesta aprobación, **Then** el servidor rechaza los atributos no autorizados; no se crea un administrador ni un restaurante aprobado por esa manipulación.
5. **US1.5 — Given** una persona que representa un restaurante sin cuenta ni solicitud presentada, **When** registra su cuenta de restaurante, **Then** queda persistida una cuenta habilitada para inicio de sesión, perfil propio y recuperación, sin exigir ni generar una solicitud comercial y sin habilitar operaciones comerciales. Si posteriormente su solicitud está pendiente o rechazada, conserva esos accesos personales mientras su cuenta no esté restringida por una causa independiente. El procesamiento de solicitudes y la aprobación real pertenecen a RF-05/RF-06; los estados comerciales se preparan como datos de prueba cuando corresponda, sin construir su gestión en este incremento.
6. **US1.6 — Given** el responsable del despliegue con acceso al procedimiento privado, **When** crea la primera cuenta administrativa o una adicional y su titular inicia sesión, **Then** existe una cuenta persistida con ese rol, queda registrado quién realizó el alta, cuándo y para qué cuenta, y no se utilizan contraseñas predeterminadas compartidas ni se registran secretos. Un visitante y un administrador que solo disponga de acceso a la aplicación no pueden crear administradores por el registro público ni por una interfaz web de alta administrativa.
7. **US1.7 — Given** un fallo de conexión o persistencia durante el alta, **When** se presenta el resultado o se reintenta, **Then** no se anuncia éxito sin confirmación, se permite reintentar y no se generan cuentas duplicadas ni incompletas.

---

### User Story 2 - Iniciar y terminar el acceso personal (Priority: P1)

Como titular de cualquiera de los cuatro roles, quiero iniciar sesión con mi cuenta habilitada, reconocer que estoy conectado y cerrar mi acceso cuando termine.

**Why this priority**: Completa RF-01 y permite probar las operaciones protegidas sin esperar los módulos de negocio posteriores.

**Independent Test**: Preparar cuentas habilitadas y restringidas de los cuatro roles, entrar desde la interfaz pública, abrir el perfil y cerrar sesión; comprobar también solicitudes directas. Preparar un administrador no acredita su política de creación.

**Acceptance Scenarios**:

1. **US2.1 — Given** una cuenta habilitada de cada rol, **When** introduce su correo y contraseña correctos, **Then** se muestra su identidad y acceso al perfil propio; el rol y el estado proceden de la cuenta persistida. Al recargar, el acceso sigue el ciclo de vida documentado en DT-01.
2. **US2.2 — Given** un correo inexistente o una contraseña incorrecta, **When** intenta entrar, **Then** se presenta el mismo mensaje de credenciales inválidas, no se revela cuál dato falló, no se habilita acceso personal y no se altera la cuenta. Campos vacíos o un correo mal formado se señalan como errores de formato antes de intentar autenticar y también se rechazan en el servidor.
3. **US2.3 — Given** una cuenta restringida, **When** intenta iniciar sesión o reutiliza una autenticación previa, **Then** no obtiene acceso personal autorizado ni reactiva su cuenta. Una solicitud de restaurante ausente, pendiente o rechazada no constituye por sí sola una restricción de la cuenta: permite inicio, perfil propio y recuperación según POL-02.
4. **US2.4 — Given** autenticación ausente, alterada o expirada, **When** abre una pantalla personal o solicita una operación protegida, **Then** se exige iniciar sesión, el servidor deniega la operación con 401 y no devuelve información privada ni realiza cambios.
5. **US2.5 — Given** un titular autenticado, **When** cierra sesión y vuelve al perfil mediante recarga, botón atrás o una credencial capturada antes del cierre, **Then** el acceso terminado no permite nuevas operaciones protegidas y la interfaz retira los datos privados visibles. El cierre en el navegador actual está incluido; el cierre global en otros dispositivos se decide en DT-01.
6. **US2.6 — Given** intentos de acceso para un mismo correo normalizado, **When** se registra el décimo fallo consecutivo dentro de una ventana de 15 minutos, **Then** los intentos posteriores se pausan durante 15 minutos desde ese décimo fallo, incluso con una contraseña correcta. Un inicio exitoso anterior al umbral reinicia la secuencia de fallos; los intentos durante la pausa no la prolongan. Al cumplir los 15 minutos se permite reintentar, sin cambiar el estado persistente de la cuenta ni revelar si existe. Probar también nueve fallos, el décimo fallo y el final exacto de la pausa.

---

### User Story 3 - Recuperar una contraseña olvidada sin administrador (Priority: P1)

Como titular que conserva acceso a su correo, quiero recibir una oportunidad temporal de restablecer mi contraseña y volver a entrar sin solicitar ayuda administrativa.

**Why this priority**: RF-02 es el mecanismo autónomo para recuperar acceso y debe evitar que terceros se apropien de una cuenta.

**Independent Test**: Desde una cuenta preparada, solicitar la recuperación, recibir el correo, restablecer, entrar con la nueva contraseña y verificar que la anterior deja de funcionar; repetir con pruebas inválidas y vencidas, comprobando la persistencia.

**Acceptance Scenarios**:

1. **US3.1 — Given** la pantalla de recuperación pública, **When** se solicita para un correo existente o inexistente con formato válido y sin superar el límite de ACC-009, **Then** se muestra la misma confirmación genérica. Para la cuenta existente se gestiona el correo de recuperación sin administrador; para la inexistente no se crea cuenta ni se revela su inexistencia. Nunca se muestra el secreto de recuperación en la respuesta pública. Las solicitudes que superan el límite se tratan según US3.8 sin revelar si la cuenta existe.
2. **US3.2 — Given** una recuperación válida emitida hace menos de 30 minutos y no consumida ni sustituida, **When** el titular introduce y confirma una nueva contraseña válida, **Then** se confirma únicamente tras guardar el cambio, se consume la recuperación y se le permite ir al inicio de sesión. La contraseña anterior falla y la nueva funciona tras recargar o reiniciar el servicio; el cambio no modifica rol, estado ni aprobación.
3. **US3.3 — Given** una recuperación desconocida, manipulada, emitida hace 30 minutos o más, sustituida por otra o ya usada, **When** se abre o envía el restablecimiento, **Then** se muestra que no es válida o ya no está disponible, se ofrece solicitar otra y no se modifica ninguna contraseña. Un fallo se rechaza también al omitir la pantalla y acudir directamente al servidor.
4. **US3.4 — Given** una recuperación válida, **When** se envía una contraseña inválida o una confirmación distinta, **Then** no se guarda el cambio ni se consume la recuperación; se permite corregir mientras siga vigente.
5. **US3.5 — Given** la misma recuperación enviada dos veces simultáneamente, **When** se intenta confirmar, **Then** como máximo un restablecimiento tiene éxito y el otro se rechaza sin sobrescribir el resultado. Si falla la escritura, no se consume la recuperación sin guardar la contraseña ni se anuncia éxito.
6. **US3.6 — Given** una cuenta con autenticaciones anteriores, **When** se completa la recuperación, **Then** esas autenticaciones dejan de autorizar operaciones y el titular debe iniciar sesión con la nueva contraseña. Si la cuenta estaba restringida, sigue restringida.
7. **US3.7 — Given** un fallo temporal del envío de correo, **When** se solicita recuperar acceso, **Then** no se afirma que el correo fue entregado, no se publica el secreto y existe un tratamiento verificable de reintento o nueva solicitud conforme a DT-02, sin cambiar la contraseña por el fallo.
8. **US3.8 — Given** solicitudes para un mismo correo normalizado, **When** se presentan hasta cinco solicitudes en cualquier ventana de 15 minutos, **Then** se permite su tramitación y cada nueva recuperación emitida sustituye la anterior. Una sexta solicitud dentro de esa ventana no genera correo ni recuperación y no invalida el último enlace válido; la respuesta no revela si la cuenta existe. Cuando las solicitudes anteriores quedan fuera de la ventana de 15 minutos se vuelve a permitir solicitar, sin suspensión permanente. Probar quinta, sexta y límite temporal; los reintentos internos de entrega del mismo correo no cuentan como una nueva solicitud del usuario.
9. **US3.9 — Given** un correo vacío o mal formado, **When** se solicita recuperar acceso desde la pantalla o directamente ante el servidor, **Then** se señala el error de formato sin consultar ni revelar una identidad, no se envía un correo y no se genera una recuperación utilizable.

---

### User Story 4 - Consultar y actualizar únicamente mi perfil (Priority: P2)

Como titular autenticado de cualquiera de los cuatro roles, quiero consultar mi perfil y actualizar mis datos personales editables sin cambiar mi rol ni afectar a otra cuenta.

**Why this priority**: RF-03 aporta autogestión y una operación real para verificar la pertenencia exigida por RF-04.

**Independent Test**: Preparar dos cuentas por rol, consultar la propia, editar el nombre, recargar y volver a entrar. Comparar datos antes y después de solicitudes válidas, inválidas y dirigidas a la otra cuenta.

**Acceptance Scenarios**:

1. **US4.1 — Given** una cuenta autenticada y habilitada, **When** abre su perfil, **Then** ve su nombre, correo y rol reales, sin contraseñas, representaciones de credenciales ni secretos de recuperación. El rol y el correo son de lectura en este incremento.
2. **US4.2 — Given** el perfil propio, **When** actualiza su nombre con un valor válido y guarda, **Then** el servidor confirma después de persistir y el nuevo nombre permanece al recargar y al iniciar sesión nuevamente; correo, rol, estado e identidad se conservan.
3. **US4.3 — Given** un nombre vacío o fuera de límites, atributos no editables o un fallo de escritura, **When** intenta guardar, **Then** recibe un error corregible o de disponibilidad según corresponda, no se anuncia éxito y el perfil persistido conserva su último estado válido.
4. **US4.4 — Given** una cuenta A autenticada, **When** intenta consultar o modificar el perfil de B manipulando destinos, identificadores o datos enviados, **Then** el servidor deniega con 403 sin revelar datos de B ni modificar a A o B. Se comprueba entre cuentas del mismo rol, de roles diferentes y desde un administrador que utiliza el flujo de perfil propio.
5. **US4.5 — Given** el perfil propio, **When** se intenta enviar otro rol, estado, aprobación, correo o identificador aunque no exista control visual para ello, **Then** se rechaza la modificación y se conservan los valores autorizados. RF-32 no se habilita mediante esta pantalla.

---

### User Story 5 - Acceder solo a las operaciones autorizadas (Priority: P1)

Como participante de Mikuna, quiero que mi información y mis operaciones estén protegidas ante personas sin autenticación, cuentas restringidas y otros titulares.

**Why this priority**: RF-04 y RNF-07 condicionan la aceptación de todos los flujos anteriores; ocultar botones no protege información.

**Independent Test**: Ejecutar la matriz de permisos de este incremento con cuentas preparadas de los cuatro roles, estados permitidos y restringidos, credenciales inválidas y dos titulares distintos; comprobar respuestas y ausencia de cambios persistidos.

**Acceptance Scenarios**:

1. **US5.1 — Given** un visitante sin autenticación, **When** consulta las cinco secciones públicas o abre registro, inicio y recuperación, **Then** conserva acceso público. La declaración constitucional de RF-09/RF-10 públicos se conserva para su futuro incremento, sin reconstruir el listado retirado.
2. **US5.2 — Given** cada rol y estado de prueba, **When** ejecuta cada operación de la matriz, **Then** se cumple el permiso indicado tanto desde la interfaz como en solicitudes directas; una autenticación válida sin permiso obtiene 403 y una ausente o inválida obtiene 401.
3. **US5.3 — Given** un usuario que altera datos locales o envía rol, estado o titular ajenos, **When** intenta obtener acceso adicional, **Then** la decisión del servidor usa los datos autorizados vigentes y la pertenencia real; no concede el permiso solicitado.
4. **US5.4 — Given** una cuenta autenticada que pasa a estar restringida en los datos de prueba, **When** se realiza la siguiente consulta o actualización de perfil, **Then** se deniega con 403 incluso si la credencial se emitió mientras estaba habilitada. Puede terminar su acceso residual según la matriz. No se construye aquí una interfaz de gestión de estados.
5. **US5.5 — Given** una operación que no está declarada pública ni autorizada expresamente, **When** se intenta invocarla, **Then** se aplica denegación por defecto; no se crean operaciones ficticias de pedidos, aprobación o entregas para demostrar RF-04.
6. **US5.6 — Given** el inicio público en cualquiera de las seis ventanas de aceptación, **When** se activan los accesos de cuentas de ACC-016 y se recorren registro, inicio, solicitud de recuperación, restablecimiento y perfil, **Then** se alcanza el flujo correspondiente, se puede volver al inicio público y se conserva la identidad visual de ACC-015; los accesos a funcionalidades futuras mantienen su aviso. “Registrar mi restaurante” abre el registro de cuenta de restaurante y explica que la solicitud y aprobación comercial son posteriores, conforme a POL-02.
7. **US5.7 — Given** cualquiera de las cinco pantallas de cuentas, **When** se recorre con teclado, se provoca un error y se amplía el texto al 200 % en las seis ventanas, **Then** se pueden identificar, alcanzar y usar todos sus campos, mensajes y acciones conforme a ACC-017, sin recortes, pérdida de foco ni desplazamiento horizontal de página.

### Edge Cases

- Doble envío o reintento del registro: máximo una cuenta por identidad; no convertir un error incierto de red en éxito confirmado (US1.3, US1.7).
- Cuenta inexistente, credencial incorrecta, credencial vencida y cuenta restringida: distinguir denegaciones sin filtrar información sensible (US2.2 a US2.4).
- Restablecimiento en el límite de vencimiento: es válido solo antes de que transcurran 30 minutos desde la emisión. Probar el instante inmediatamente anterior, el vencimiento exacto y un instante posterior; al confirmar con 30 minutos o más se rechaza aunque la pantalla se abriera antes. Retrasos y reintentos de entrega de correo no prolongan la vigencia (US3.3).
- Recuperación reutilizada, intercambiada entre cuentas o enviada concurrentemente: ningún cambio sobre otra cuenta y como máximo un consumo exitoso (US3.3, US3.5).
- Nueva solicitud mientras un correo anterior se retrasa: la recuperación anterior no vuelve a habilitarse cuando ese correo llegue. Una solicitud bloqueada por el límite no invalida el enlace vigente ni crea otro; los fallos y reintentos internos de envío no reinician su vigencia ni consumen nuevas solicitudes de usuario (US3.8).
- Límites por correo normalizado: diferencias de mayúsculas o espacios exteriores no permiten eludirlos; se aplican también a correos inexistentes sin crear cuentas. El décimo fallo de acceso activa una pausa de 15 minutos que no se prolonga por intentos bloqueados; la sexta recuperación en una ventana de 15 minutos no se tramita. Las comprobaciones deben conservar su resultado al atenderse desde instancias distintas (US2.6, US3.8).
- Fallo entre actualización de contraseña y consumo de recuperación: ambos cambios deben confirmarse juntos o ninguno (US3.5).
- Cambios de rol, estado o pertenencia manipulados y edición masiva de atributos: no prevalecen sobre la cuenta autorizada (US1.4, US4.4, US4.5, US5.3).
- Navegación atrás, pestañas abiertas y expiración durante la edición: no mostrar guardado exitoso ni volver a conceder acceso por conservar una pantalla (US2.4, US2.5, US4.3).
- Contraseña con espacios: no recortarla ni modificarla silenciosamente; la normalización del correo no se aplica a credenciales (ACC-002).
- Recuperación de una cuenta restringida: cambiar la contraseña no habilita la cuenta ni aprueba un restaurante (US3.6).
- Si una cuenta no conserva acceso al correo, no existe en este incremento un mecanismo alternativo de recuperación ni un desbloqueo administrativo implícito.

## Requirements *(mandatory)*

### Functional Requirements

#### Identidad, registro y acceso

- **ACC-001 (RF-01)**: Permitir registro e inicio de sesión de los cuatro actores mediante las políticas aplicables. Clientes y repartidores cuentan con registro público según el supuesto A-01; las cuentas de restaurante también se registran públicamente antes de presentar la solicitud comercial según POL-02; el responsable del despliegue crea todas las cuentas administrativas mediante el procedimiento privado de POL-01, con registro de cada alta y sin contraseñas predeterminadas compartidas ni interfaz web de alta administrativa; la ausencia, revisión pendiente o rechazo de la solicitud comercial no impide inicio, perfil propio ni recuperación de una cuenta de restaurante habilitada. El alta no crea una solicitud ni concede aprobación comercial. No declarar RF-01 completo mientras falte alguno de sus actores. Aceptación: US1.1 a US1.7, US2.1.
- **ACC-002 (RF-01/RF-03)**: Aplicar en presentación y servidor los mismos datos básicos: nombre de 1 a 100 caracteres tras retirar espacios exteriores, correo válido de hasta 254 caracteres, contraseña de 12 a 128 caracteres sin recortarla y confirmación idéntica en registro/restablecimiento. Normalizar el correo retirando espacios exteriores y sin distinguir mayúsculas para su unicidad. Perfil editable: únicamente el nombre; correo y rol de lectura. Son supuestos de especificación A-02/A-03, no cifras ni campos atribuidos a la propuesta. Aceptación: US1.2, US1.3, US3.4, US4.1 a US4.3.
- **ACC-003 (RF-01/RF-04)**: Rechazar registros duplicados y atributos no autorizados; persistir una única cuenta por correo normalizado, con un solo rol y su estado autorizado, sin cuentas parciales. El correo ya registrado no puede reutilizarse para otro rol ni promoverse mediante un nuevo registro, incluido el procedimiento privado de alta administrativa. Participar con otro rol requiere otra cuenta con distinto correo; no hay selección ni cambio de rol. Las solicitudes concurrentes deben preservar la unicidad. Aceptación: US1.3, US1.4, US1.7.
- **ACC-004 (RF-01)**: Verificar las credenciales contra la cuenta persistida, identificar al titular y permitir acceso personal solo conforme a su estado. Los errores de identidad inexistente y contraseña incorrecta deben ser indistinguibles para el solicitante. Aceptación: US2.1 a US2.3.
- **ACC-005 (RF-01/RF-04)**: Permitir cerrar el acceso del navegador actual y hacer que credenciales expiradas, manipuladas, cerradas o invalidadas dejen de autorizar operaciones. El ciclo de vida requiere DT-01; conservar una vista no equivale a estar autenticado. Aceptación: US2.4, US2.5, US3.6.

#### Recuperación autónoma

- **ACC-006 (RF-02)**: Ofrecer solicitud pública de recuperación por correo con confirmación genérica para identidades existentes e inexistentes, sin intervención administrativa ni exposición del secreto. Validar presencia y formato del correo; la confirmación informa de la solicitud, no garantiza la entrega del correo. Aceptación: US3.1, US3.7, US3.9.
- **ACC-007 (RF-02)**: Exigir una prueba de recuperación válida durante 30 minutos desde su emisión, de un solo uso y vinculada exclusivamente a la cuenta destinataria. Comprobar vigencia al confirmar, invalidar las anteriores al emitir una nueva y rechazar pruebas desconocidas, modificadas, vencidas o consumidas sin cambiar credenciales. La vigencia se comprueba al confirmar: transcurridos 30 minutos o más se rechaza, aunque el correo se entregue tarde o la pantalla se abra antes del vencimiento. Los reintentos de envío de la misma recuperación no extienden el plazo; un nuevo enlace tiene su propia emisión e invalida el anterior. Aceptación: US3.2 a US3.5, US3.8.
- **ACC-008 (RF-02)**: Confirmar juntos el cambio de contraseña y el consumo de recuperación; impedir doble consumo concurrente. Tras éxito, rechazar contraseña anterior e invalidar autenticaciones previas sin cambiar rol, estado ni aprobación. No iniciar sesión automáticamente al restablecer. Aceptación: US3.2, US3.5, US3.6.
- **ACC-009 (RF-01/RF-02)**: Aplicar por correo normalizado una pausa de acceso de 15 minutos al alcanzar 10 fallos consecutivos dentro de una ventana de 15 minutos; una autenticación exitosa anterior al umbral reinicia esa secuencia. La pausa comienza en el décimo fallo y no se prolonga por intentos bloqueados. Admitir como máximo 5 solicitudes de recuperación en cualquier ventana de 15 minutos; las excedentes no generan correo, no crean recuperaciones ni invalidan la vigente. Aplicar estos límites exista o no la cuenta, con respuestas equivalentes, sin cambiar permanentemente su estado y con el mismo resultado entre instancias. Los reintentos internos de envío de una recuperación no cuentan como nuevas solicitudes. Los controles adicionales por origen y el tratamiento técnico de fallos/reintentos de correo quedan para DT-02. Aceptación: US2.6, US3.7, US3.8.

#### Perfil, autorización y protección

- **ACC-010 (RF-03)**: Permitir a cada titular autorizado consultar y actualizar exclusivamente sus datos editables, mostrando el resultado real persistido. No aceptar cambios parciales ante validación fallida ni exponer credenciales o secretos. Aceptación: US4.1 a US4.3.
- **ACC-011 (RF-03/RF-04)**: Rechazar todo intento de modificar rol, estado, aprobación, identificador o correo a través del perfil propio, incluidos atributos enviados sin controles visibles. Aceptación: US4.5.
- **ACC-012 (RF-04/RNF-07)**: Autorizar cada operación protegida del incremento por autenticación, rol permitido, estado vigente y pertenencia cuando corresponda, según la matriz. La cuenta de restaurante habilitada conserva acceso personal con solicitud ausente, pendiente o rechazada; la aprobación comercial no se deduce del rol ni de crear la cuenta. El servidor debe denegar por defecto y volver a comprobar el estado vigente; la interfaz refleja esa decisión. Aceptación: US2.3, US4.4, US5.2 a US5.5.
- **ACC-013 (RF-04/RNF-07)**: Denegar operaciones protegidas sin autenticación válida con 401 y con autenticación válida sin autorización con 403. La consulta o edición del perfil ajeno no devuelve información privada ni altera datos; un administrador tampoco obtiene esa facultad por el flujo de perfil propio. Aceptación: US2.4, US4.4, US5.2.
- **ACC-014 (RF-01/RF-02; constitución III)**: Proteger contraseñas mediante representaciones irreversibles adaptativas con sal; conservar pruebas de recuperación de modo que una lectura de su persistencia no permita utilizarlas. No exponer contraseñas, credenciales de acceso, recuperaciones ni secretos en respuestas generales o registros. La entrega de una credencial al autenticarse y del mecanismo de recuperación al destinatario son intercambios expresamente protegidos. Algoritmos y parámetros requieren DT-01. Aceptación: inspección de datos y registros de US1 a US4 y rechazos de US3.3.

#### Integración visual y resultados completos

- **ACC-015 (instrucción del usuario)**: Integrar las pantallas de registro, inicio, solicitud de recuperación, restablecimiento y perfil con el logo, tipografía, jerarquías, márgenes, botones, encabezado y footer pertinentes ya existentes. Usar exclusivamente índigo `#11094C`, naranja `#FF5A1F`, marfil `#FDF8F4`, melocotón `#FDE8D8`, gris `#464B53` y blanco `#FFFFFF`, también para errores. Conservar las cinco secciones públicas, el recorrido continuo y el listado de restaurantes retirado. Aceptación: US5.6, SC-005.
- **ACC-016 (RF-01/RF-02 e instrucción del usuario)**: Sustituir el aviso temporal solo en los accesos a cuentas: “Crear cuenta” del inicio/footer abre registro; “Ya tengo cuenta” abre inicio; “Quiero ser repartidor” y “Quiero sumarme” abren registro de repartidor. “Registrar mi restaurante” abre el registro de cuenta de restaurante y comunica que la solicitud y aprobación comercial son posteriores (POL-02); no envía una solicitud ni promete recepción de pedidos. Ofrecer “Iniciar sesión”, “Olvidé mi contraseña”, “Mi perfil” y “Cerrar sesión” según contexto. “Pedir ahora”, “Empezar a pedir” y categorías conservan su aviso; no crear catálogo, pedido ni nuevas promesas operativas. Aceptación: US5.6, SC-005.
- **ACC-017 (instrucción del usuario)**: Los cinco recorridos deben ser utilizables con teclado, foco visible, campos con etiquetas y errores asociados, sin depender solo del color; conservar controles de al menos 44 × 44 px. En 320 × 568, 390 × 844, 768 × 1024, 1024 × 768, 1440 × 900 y 1536 × 1024 px y texto ampliado al 200 %, no ocultar campos, mensajes o acciones ni producir desplazamiento horizontal de página. Se permite desplazamiento vertical. Aceptación: US5.7, SC-005.
- **ACC-018 (RF-01 a RF-04)**: Mostrar estados de envío, resultado y error recuperables; anunciar éxito únicamente tras confirmación del servidor y persistencia. Los fallos de conexión no deben duplicar altas ni presentar cambios no guardados como definitivos. Ningún flujo de aceptación puede resolverse solo con datos simulados o almacenamiento del navegador. Aceptación: US1.7, US3.2, US3.5, US3.7, US4.2, US4.3.

### Matriz de acceso verificable en este incremento

“Habilitada” y “restringida” describen si la cuenta tiene permiso de acceso personal, sin inventar un catálogo de estados administrativos. La cuenta de restaurante nace habilitada para el acceso personal antes de la solicitud; una solicitud ausente, pendiente o rechazada no la convierte en restringida (POL-02). Una restricción independiente de la cuenta sí deniega el acceso personal, sin afectar la posibilidad de recuperar credenciales. Los cuatro roles son cliente, restaurante, repartidor y administrador; ninguno hereda acceso al perfil ajeno en estos flujos.

| Operación | Sin autenticación válida | Cuenta habilitada: cualquiera de los cuatro roles | Cuenta restringida con credencial válida | Pertenencia / condición |
| --- | --- | --- | --- | --- |
| Leer secciones públicas | Permitido | Permitido | Permitido | No requiere cuenta. |
| Solicitar registro público | Permitido según actor admitido | Mismas validaciones públicas, sin promover la cuenta existente | Mismas validaciones públicas; no elude una restricción | Cliente/repartidor según A-01 y restaurante antes de su solicitud según POL-02; administrador excluido de autoservicio. |
| Crear administrador mediante el procedimiento privado | Denegado mediante acceso público | El rol de aplicación no autoriza este procedimiento | El rol de aplicación no autoriza este procedimiento | Solo el responsable del despliegue mediante su acceso privado; registro de cada alta y sin contraseñas predeterminadas compartidas (POL-01). |
| Iniciar sesión | Solo genera acceso tras credenciales y estado válidos | Permitido para la cuenta identificada | Sin nuevo acceso personal | Rol/estado proceden de la cuenta, no del solicitante. |
| Solicitar/confirmar recuperación | Público con prueba válida para confirmar | Permitido con las mismas condiciones | Puede recuperar credenciales sin habilitar la cuenta | El cambio solo afecta al titular de la recuperación. |
| Consultar/actualizar perfil propio | 401 | Permitido; edición limitada a ACC-002 | 403 | Titular autenticado = propietario. |
| Consultar/actualizar perfil ajeno mediante estos flujos | 401 | 403, también para administrador | 403 | Sin datos privados ni cambios. |
| Cambiar rol/estado/aprobación mediante el perfil | 401 | 403 | 403 | No hay operación autorizada de este tipo. |
| Cerrar acceso actual | 401 si la credencial es inválida; limpiar la vista local | Permitido | Permitido para terminar el acceso residual | Solo el acceso actual; no concede acceso al perfil. |

Los 401/403 corresponden a operaciones protegidas; los errores de formularios públicos se describen en sus historias sin imponer esos códigos. Cerrar el acceso residual es una excepción explícita a la restricción para permitir terminarlo, no una habilitación de operaciones personales.

### Límite de RF-04 y dependencias futuras

| Comprobación | Verificable ahora | Verificación posterior y dependencia |
| --- | --- | --- |
| Autenticación, rol reconocido y estado vigente | Todas las operaciones de cuentas disponibles y rechazo de datos manipulados; no existe jerarquía implícita de administrador | Cada operación nueva debe declarar sus roles y condiciones de estado; repetir la matriz ampliada. |
| Pertenencia del recurso | Perfil propio frente a perfil de otro titular, entre los cuatro roles | No extrapolar esta evidencia a pedidos, restaurantes o entregas. |
| Cuenta de restaurante frente a aprobación | Registro independiente de la solicitud; inicio, perfil y recuperación permitidos con solicitud ausente, pendiente o rechazada si la cuenta está habilitada; ninguna aprobación derivada del alta (POL-02) | RF-05/RF-06: solicitud, decisión administrativa y rechazo real de pedidos para restaurantes no aprobados. |
| Restaurante y catálogo ajenos | No hay operaciones de gestión para probar | RF-07/RF-08 y RF-18/RF-19: restaurante propio frente a otro restaurante y pedidos recibidos. |
| Pedido propio frente a ajeno | No hay pedidos disponibles | RF-14, RF-20, RF-27/RF-28, RF-30/RF-31: titular, estado y operación permitida; incluir intentos entre clientes distintos. |
| Entrega asignada frente a ajena | No hay entregas disponibles | RF-21 a RF-26 y RF-30: repartidor habilitado, disponibilidad y asignación vigente; incluir entregas de otro repartidor. |
| Facultades administrativas | Inicio/perfil propio y alta privada por el responsable del despliegue según POL-01; ningún permiso general inferido | RF-06, RF-24, RF-32/RF-33: cada facultad se autoriza y verifica al implementarse. |

No implementar pantallas, recursos simulados ni operaciones de esos módulos para atribuir cumplimiento total a RF-04. Su estado al aceptar este incremento será **parcial**, aunque las operaciones de cuentas estén todas verificadas. RF-01, RF-02 y RF-03 podrán verificarse solo cuando todos sus escenarios aplicables estén completos y las políticas resueltas.

### Requisitos de calidad y trazabilidad

| Fuente | Aplicación en este incremento | Aceptación / evidencia exigida |
| --- | --- | --- |
| RF-01 | ACC-001 a ACC-005, ACC-009, ACC-014, ACC-016, ACC-018 | US1/US2; SC-001/SC-004; alta de los cuatro actores conforme a las decisiones POL-01/POL-02. |
| RF-02 | ACC-006 a ACC-009, ACC-014, ACC-018 | US3; SC-002; correo recibido, cambio persistido, vencimiento, reutilización, concurrencia e invalidación comprobados. |
| RF-03 | ACC-002, ACC-010/ACC-011, ACC-018 | US4; SC-003; lectura y actualización real por cada rol, invariancia de atributos protegidos. |
| RF-04 | ACC-003, ACC-005, ACC-012/ACC-013 | US1.4, US2.3 a US2.5, US4.4/US4.5 y US5; SC-004; cumplimiento parcial limitado a cuentas, matriz futura pendiente. |
| RNF-06 | Comunicación cifrada en los recorridos y correo enlazado | SC-006; certificado válido y prueba de redirección/rechazo HTTP en el entorno entregado; no acreditarlo solo con ejecución local. |
| RNF-07 y aclaración constitucional | Rutas públicas declaradas; 100 % de accesos protegidos sin autenticación o permiso denegados | Matriz completa, US2.4, US4.4 y US5; SC-004; solicitudes directas y comprobación de ausencia de efectos. |
| RNF-11 y aclaración del usuario | Al menos 90 % de cobertura de líneas de lógica de negocio; ramas informadas sin umbral | SC-007; pruebas unitarias, integración y extremo a extremo de los flujos habilitados, informe reproducible con exclusiones justificadas y DT-03 resuelta. Ya no corresponde declarar la cobertura de negocio no aplicable. |
| RNF-12 | Pruebas automáticas y despliegue tras integración exitosa; fallos impiden entrega | SC-007; historial del proceso y versión desplegada. No confundir configuración existente con ejecución. |
| RNF-02/RNF-04 | Conservar acceso sin dependencia de la memoria de una sola instancia | Restricción heredada para planificar el acceso; comparación 1/3 instancias y caída de instancia pendientes de la evaluación de infraestructura posterior. No declarar estos RNF satisfechos. |
| RNF-10 | Conservar la obligación de observabilidad al integrar el backend | Evidencia global del panel durante carga pendiente de la evaluación de infraestructura; no convertir registro de errores locales en cumplimiento. |
| RNF-01/RNF-03 | Sus metas corresponden a consultas de restaurantes, catálogo y estado del pedido, ausentes aquí | Pendientes de esos incrementos y de carga; no aplicar artificialmente sus cifras a cuentas. |
| RNF-05/RNF-08/RNF-09 | Asignación única, tarjetas delegadas y pedido en menos de 5 minutos | Fuera de la evidencia de cuentas; pendientes de entregas, pagos y flujo completo de pedido, respectivamente. |
| Instrucción visual y constitución I/II/VI | ACC-015 a ACC-018; reutilización, separación de responsabilidades y trazabilidad | US5.6/US5.7, SC-005; revisión de integración y enlaces a criterios, tareas futuras, cambios y resultados. No modificar la base arquitectónica mediante esta especificación. |

El alcance funcional afecta cuentas, acceso y perfil; recuperación depende del envío de comunicaciones. No introduce gestión administrativa general ni amplía las operaciones de los otros dominios. El diseño técnico debe conservar los límites y las decisiones arquitectónicas de la constitución; su concreción corresponde a la planificación.

### Key Entities *(include if feature involves data)*

- **Cuenta**: identidad única, correo normalizado, nombre, rol autorizado, estado vigente y credencial protegida. Cada correo normalizado corresponde a una única cuenta con un solo rol, según la aclaración del usuario; no se comparte entre roles ni se selecciona un rol al entrar. Su existencia no equivale a aprobación de restaurante.
- **Perfil propio**: proyección de la cuenta accesible a su titular, con nombre editable y correo/rol de lectura; sin secretos ni facultades administrativas.
- **Acceso autenticado**: vínculo temporal entre la cuenta y su credencial de acceso, con vigencia y condición de cierre o invalidación. Su formato queda para DT-01.
- **Recuperación de contraseña**: cuenta destinataria, prueba protegida, momento de emisión, vencimiento a los 30 minutos y condición de consumo/invalidez; como máximo una vigente por cuenta y un uso exitoso.
- **Comunicación de recuperación**: destinatario, solicitud relacionada y situación del envío que permita verificar fallos y reintentos sin registrar secretos.
- **Relación cuenta–restaurante**: la cuenta existe antes de la solicitud y su acceso personal es independiente del resultado comercial (POL-02). RF-05/RF-06 deberán vincular la futura solicitud y el establecimiento con esa cuenta existente, sin crear otra identidad ni atribuir aprobación al alta. La cardinalidad de establecimientos por cuenta y sus operadores se definirá en el incremento de restaurantes; no es necesaria para registrar y probar ahora la identidad de restaurante.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 — Acceso de los cuatro actores**: Los 4 de 4 actores completan su alta autorizada e inicio de sesión con una cuenta persistida; el 100 % de registros inválidos, duplicados y manipulados de US1 se rechaza sin altas indebidas. Su cumplimiento requiere verificar el procedimiento privado de POL-01 y el registro y acceso personal independientes de la aprobación comercial de POL-02.
- **SC-002 — Recuperación autónoma**: En los 4 roles, el 100 % de recuperaciones válidas de US3 permite volver a entrar sin administrador con la nueva contraseña y rechaza la anterior; el 100 % de casos inválidos, vencidos, consumidos y concurrentes conserva las invariantes descritas, con rechazo desde el instante exacto en que se cumplen 30 minutos de su emisión. Cero cambios de rol, estado o aprobación por recuperación.
- **SC-003 — Autogestión**: En los 4 roles, el nombre actualizado permanece tras recargar y volver a entrar en el 100 % de los casos válidos; cero modificaciones de atributos protegidos o perfiles ajenos en los casos negativos de US4.
- **SC-004 — Protección de cuentas**: El 100 % de las combinaciones aplicables de la matriz se comporta según lo declarado; cero divulgaciones de datos privados y cero cambios persistidos en intentos denegados. Cierre, expiración, recuperación y restricción impiden reutilizar el acceso correspondiente. En el 100 % de pruebas de límite, el décimo fallo activa la pausa de 15 minutos y la sexta solicitud de recuperación en 15 minutos no produce envíos ni invalida un enlace vigente.
- **SC-005 — Continuidad y uso**: Los 5 recorridos —registro, inicio, solicitud de recuperación, restablecimiento y perfil— son completables con teclado en las 6 ventanas de ACC-017, también con texto al 200 %, con todos los campos y errores reconocibles y cero controles inaccesibles. Se conservan las 5 secciones públicas y el footer con la paleta existente y sus destinos vigentes salvo la integración expresa de cuentas.
- **SC-006 — Protección en la entrega**: El 100 % de solicitudes sin cifrar de los recorridos entregados se redirige o rechaza y la entrada cifrada presenta certificado válido. La revisión de los datos y registros del conjunto de pruebas detecta cero contraseñas legibles o secretos expuestos fuera de sus intercambios autorizados.
- **SC-007 — Verificación reproducible**: Todos los escenarios definidos y regresiones de los flujos afectados pasan; la lógica de negocio tiene al menos 90 % de líneas cubiertas y reporte de ramas. La versión aceptada dispone de evidencia de pruebas y despliegue automáticos. Este resultado comprueba calidad de entrega, no sustituye los resultados de usuario SC-001 a SC-006.

Los resultados se verifican en el flujo integrado con datos persistidos y, para recuperación, envío de correo comprobado en el entorno de aceptación. Se admite aislamiento de dependencias para pruebas unitarias, pero no como única evidencia del flujo completo. Esta especificación no incorpora un objetivo de tiempos de cuentas inventado ni cambia los umbrales originales de los RNF.

## Assumptions

### Supuestos explícitos de esta especificación

- **A-01**: El usuario confirmó el 2026-11-13 una cuenta por correo normalizado y un único rol por cuenta. Otro rol requiere otra cuenta con distinto correo; no se incluye selección, acumulación ni cambio de roles. Se mantiene como supuesto el autoservicio de cliente y repartidor sin una aprobación adicional no documentada. No autoriza autoservicio administrativo; el autoservicio de la cuenta de restaurante antes de su solicitud está confirmado por POL-02.
- **A-02**: Correo como identificador y canal de recuperación; nombre como único dato personal editable en este incremento. Los límites exactos de ACC-002 y la normalización del correo son supuestos propuestos para dar aceptación comprobable, no decisiones previas atribuidas al usuario. No incluye teléfono, avatar, direcciones de reparto, documentos, datos comerciales ni cambio de correo; ampliarlos requiere precisar validación y seguridad.
- **A-03**: Contraseña de 12 a 128 caracteres y confirmación en alta/restablecimiento como base propuesta, sin reglas adicionales de composición. El algoritmo de protección y sus parámetros no quedan seleccionados por este supuesto. No se agrega autenticación social, verificación obligatoria de correo, múltiples factores ni recuperación alternativa sin una decisión de alcance.
- **A-04**: Se permiten cuentas preparadas para probar inicio, perfil y autorización independientemente de la creación de otros actores. Son datos de prueba identificados, no un procedimiento de alta de producción ni evidencia de RF-01 completo.
- **A-05**: “Cuenta restringida” expresa la condición negativa que RF-04 exige verificar; no incorpora la interfaz ni el ciclo administrativo de RF-32. Una restricción de cuenta y una aprobación comercial son condiciones distintas.

### Políticas de negocio: decisiones y pendientes

POL-01 y POL-02 se resolvieron mediante las respuestas del usuario del 2026-11-13 registradas en Clarifications. Su resolución permite concretar la aceptación de cuentas sin adelantar la gestión comercial de RF-05/RF-06; no acredita que los flujos ya estén implementados o verificados.

| ID | Política y estado | Decisión o cuestión pendiente | Criterios afectados / alcance |
| --- | --- | --- | --- |
| POL-01 | Aprovisionamiento de cuentas administrativas — resuelta por el usuario, 2026-11-13 | El responsable del despliegue crea la primera cuenta administrativa y todas las siguientes mediante un procedimiento privado, con registro de quién realiza cada alta, cuándo y para qué cuenta; sin contraseñas predeterminadas compartidas ni interfaz web de alta administrativa. El mecanismo técnico se concreta al planificar | ACC-001, US1.6, SC-001. El registro público y el rol administrativo de la aplicación no conceden acceso al procedimiento privado. Las cuentas de prueba no sustituyen su verificación. |
| POL-02 | Relación entre cuenta y aprobación comercial — resuelta por el usuario, 2026-11-13 | Crear la cuenta antes de la solicitud; permitir inicio, perfil propio y recuperación con solicitud ausente, pendiente o rechazada mientras la cuenta esté habilitada. La futura solicitud se vinculará a esa identidad. Las operaciones comerciales requieren aprobación; su vínculo operativo y cardinalidad se concretan con RF-05/RF-06 | ACC-001/ACC-012/ACC-016, US1.5, US2.3, US5.2 y SC-001/SC-004. No crear ni tramitar solicitudes o aprobaciones en este incremento ni habilitar pedidos por registrar una cuenta. |

### Decisiones necesarias para la planificación

| ID | Consulta pendiente conforme a la constitución | Consecuencia |
| --- | --- | --- |
| DT-01 | Formato y ciclo de vida de credenciales; expiración, renovación, cierre e invalidación; protección de recuperación (vigencia de 30 minutos ya decidida por el usuario); algoritmo y parámetros de contraseñas | Concretar ACC-005/ACC-007/ACC-008/ACC-014 y pruebas de límites, manteniendo acceso sin dependencia de una sola instancia. No presumir un formato de token ni un algoritmo. |
| DT-02 | Controles adicionales por origen; tiempos, número de reintentos y resultado final del envío de correo. Los límites por correo ya están decididos: 10 fallos consecutivos en 15 minutos producen pausa de 15 minutos; máximo 5 recuperaciones en 15 minutos | Concretar los controles adicionales y US3.7 sin alterar ACC-009 ni filtrar identidades. Conservar las decisiones previas de comunicación asíncrona; los reintentos internos no amplían la vigencia ni cuentan como nuevas solicitudes. |
| DT-03 | Herramientas y agregación de cobertura de negocio, exclusiones justificadas y adaptación de la validación de interfaz pendiente | Concretar SC-007 manteniendo al menos 90 % de líneas e informe de ramas. Respetar el aplazamiento registrado de Playwright durante diseño; no omitir evidencia necesaria al aceptar el flujo funcional. |

Se completó el máximo de cinco preguntas de esta sesión. Se difieren a la consulta inicial de planificación las decisiones de DT-01 (incluido el ciclo de acceso y la protección de credenciales), DT-02 (controles por origen y reintentos de correo) y DT-03 (medición de cobertura). Son decisiones técnicas que no se han seleccionado ni autorizado implícitamente. Los criterios afectados deben completarse antes de implementar esas decisiones; no se declaran totalmente cerrados los requisitos y escenarios dependientes de ellas. La relación operativa y cardinalidad de establecimientos se difiere expresamente a RF-05/RF-06.

Las versiones ya declaradas y la base arquitectónica están inventariadas en [existing-state.md](existing-state.md); no se sustituyen por decisiones implícitas. El correo de recuperación y la entrega cifrada requieren la configuración de los servicios ya elegidos por el proyecto. El registro anterior de falta de destino de despliegue y de validación de navegador sigue siendo una dependencia pendiente, no evidencia de aceptación.
