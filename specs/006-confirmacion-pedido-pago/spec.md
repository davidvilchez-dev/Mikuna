# Feature Specification: Confirmación del pedido y procesamiento del pago

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-17

**Status**: Draft

**Input**: Incremento 006 del usuario (pasarela: Stripe en modo de pruebas): RF-14, RF-15, RF-16 y RF-17. Depende de la spec 002 (cuentas y
permisos), 003 (restaurantes habilitados), 004 (catálogo) y 005 (carrito, dirección de entrega e
importes calculados). Fuera de alcance: aceptación, rechazo y cancelación del pedido (spec 007),
asignación de repartidor, historial (spec 010), reembolsos y conciliación contable, y pasarelas
reales o varias pasarelas.

## Clarifications

### Session 2026-11-17

- Q: Si Stripe pide verificación adicional de la tarjeta (3D Secure) al reservar el monto, ¿qué debe
  hacer el sistema en este incremento? → A: Se trata como autorización fallida (sin pedido, carrito
  conservado, invitar a usar otra tarjeta); la verificación adicional queda fuera del incremento.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Confirmar el pedido y reservar el pago (Priority: P1)

Como cliente, quiero revisar el resumen de mi carrito (productos, dirección e importes), elegir un
método de pago y confirmar el pedido, para que el restaurante reciba mi pedido y mi dinero quede
reservado hasta que decida.

**Why this priority**: Es el núcleo del incremento: convierte el carrito en un pedido pendiente con el
pago autorizado (RF-14, RF-15, RF-16).

**Independent Test**: Con un carrito válido, dirección seleccionada y un medio de pago de prueba
aprobado, confirmar; comprobar que existe un pedido Pendiente con sus importes, que el monto quedó
reservado y que el carrito quedó vacío.

**Acceptance Scenarios**:

1. **Given** un cliente con carrito válido y dirección seleccionada, **When** abre la confirmación,
   **Then** ve el resumen con productos, cantidades, dirección, subtotal, envío, comisión y total, y
   los métodos de pago disponibles.
2. **Given** el resumen, **When** elige un método de pago y confirma, **Then** el monto total se
   autoriza (se reserva) en su medio de pago, se registra un pedido en estado Pendiente de
   aceptación asociado a su cliente, restaurante, productos, dirección e importes, y el carrito queda
   vacío.
3. **Given** la confirmación, **When** el pedido se registra, **Then** los precios, nombres, cantidades
   e importes quedan fijados en el pedido y ya no cambian aunque el restaurante modifique después el
   catálogo.
4. **Given** una persona sin sesión o con rol distinto de cliente, **When** intenta confirmar,
   **Then** se deniega (401 o 403) sin crear pedido ni reservar monto.
5. **Given** un carrito vacío o sin dirección seleccionada, **When** intenta confirmar, **Then** se
   rechaza con un mensaje que indica qué falta, sin contactar a la pasarela.

---

### User Story 2 - Validar de nuevo antes de registrar (Priority: P1)

Como cliente, quiero que el sistema verifique en el momento de confirmar que todo sigue siendo
válido, para no pagar por algo que ya no se puede entregar.

**Why this priority**: El carrito pudo quedar desactualizado desde que se armó; RF-14 exige validar
restaurante, productos, cantidades y dirección.

**Independent Test**: Armar un carrito y, antes de confirmar, pausar un producto, cerrar el
restaurante o sacar la dirección de la zona; comprobar que cada caso se rechaza sin reserva ni pedido.

**Acceptance Scenarios**:

1. **Given** un restaurante que ya no está habilitado ni abierto, **When** el cliente confirma,
   **Then** se rechaza con un mensaje claro; no se crea pedido ni se reserva monto.
2. **Given** algún producto no disponible o retirado, **When** el cliente confirma, **Then** se
   rechaza indicando qué productos no están disponibles; el carrito se conserva.
3. **Given** cantidades fuera de los límites permitidos, **When** el cliente confirma, **Then** se
   rechaza sin crear pedido.
4. **Given** una dirección que ya no está dentro de la zona de reparto, **When** el cliente
   confirma, **Then** se rechaza pidiendo elegir otra dirección.
5. **Given** cualquier importe enviado por el cliente, **When** confirma, **Then** el sistema lo
   ignora o rechaza y recalcula todos los importes en el servidor con los precios vigentes; el monto
   reservado coincide siempre con el total recalculado.
6. **Given** que los importes recalculados difieren de los que el cliente vio en el resumen (por
   ejemplo, cambió un precio), **When** confirma, **Then** no se crea pedido ni se reserva monto y se
   le muestra el resumen actualizado para que confirme de nuevo.

---

### User Story 3 - Fallo de pago y confirmación repetida (Priority: P1)

Como cliente, quiero un mensaje claro si mi pago no se autoriza, y que mi pedido no se duplique si
confirmo dos veces o reintento, para no quedar con pedidos ni cargos repetidos.

**Why this priority**: Protege dinero y pedidos del cliente ante errores y reintentos (RF-14, RF-16).

**Independent Test**: Simular autorización rechazada y confirmaciones duplicadas o simultáneas;
comprobar que no se crea pedido en el primer caso y que solo se crea un pedido en el segundo.

**Acceptance Scenarios**:

1. **Given** que la pasarela rechaza la autorización (fondos insuficientes, medio de pago inválido),
   **When** el cliente confirma, **Then** no se registra pedido, el carrito se conserva, se registra
   la transacción como fallida y el cliente recibe un mensaje claro que le permite reintentar o
   cambiar de método.
2. **Given** que la pasarela no responde o falla, **When** el cliente confirma, **Then** no se
   registra pedido ni queda reserva activa, y se le informa que puede reintentar.
3. **Given** una tarjeta que exige verificación adicional (3D Secure), **When** el cliente confirma,
   **Then** se trata como autorización fallida: no se registra pedido, no queda reserva activa, el
   carrito se conserva y se le invita a usar otra tarjeta.
4. **Given** una confirmación ya procesada, **When** el cliente la envía de nuevo (doble clic,
   reintento por red), **Then** no se crea otro pedido ni otra reserva y recibe el resultado del
   pedido ya registrado.
5. **Given** dos confirmaciones simultáneas del mismo carrito, **When** se procesan, **Then** se
   registra un único pedido y una única reserva.

---

### User Story 4 - Operaciones de cobro y liberación de la reserva (Priority: P2)

Como sistema, necesito poder cobrar (capturar) la reserva de un pedido pendiente y liberarla, para
que la spec 007 las dispare cuando el restaurante acepte, rechace o el cliente cancele.

**Why this priority**: Completa RF-16 y RF-17 en el ciclo de vida del pago; las decisiones que las
disparan pertenecen a la spec 007.

**Independent Test**: Para un pedido Pendiente con reserva, ejecutar la captura y comprobar el cobro
y su registro; en otro, ejecutar la liberación y comprobar que nunca se cobra.

**Acceptance Scenarios**:

1. **Given** un pedido Pendiente con pago autorizado, **When** se solicita capturar, **Then** el
   monto reservado se cobra por completo y la transacción queda registrada como cobrada.
2. **Given** un pedido Pendiente con pago autorizado, **When** se solicita liberar la reserva,
   **Then** la reserva se libera, nunca se cobra y la transacción queda registrada como liberada.
3. **Given** una reserva ya cobrada o ya liberada, **When** se solicita de nuevo cualquiera de las
   dos operaciones, **Then** se rechaza sin cambiar el estado ni volver a cobrar o liberar en la
   pasarela.
4. **Given** que la pasarela falla al capturar o liberar, **When** se ejecuta la operación, **Then**
   el fallo se registra, el estado del pago no se da por cambiado y la operación puede reintentarse.
5. **Given** cualquier pedido, **When** se revisa su ciclo de pago, **Then** no existe operación de
   reembolso: una reserva liberada no se cobra y un pago cobrado no se devuelve en este incremento.
6. **Given** una persona sin la autorización interna requerida, **When** intenta invocar estas
   operaciones, **Then** se deniega; no se exponen como acciones libres del cliente.

---

### User Story 5 - Registro y consulta del pago de un pedido (Priority: P2)

Como cliente, quiero ver el estado de mi pedido y de su pago, y como equipo, que cada transacción
quede registrada, para tener trazabilidad de lo ocurrido con el dinero.

**Why this priority**: Cumple RF-17 y permite verificar el flujo de extremo a extremo.

**Independent Test**: Tras confirmar, capturar, liberar y fallar pagos, comprobar que cada transacción
existe asociada a su pedido con su estado y resultado, sin datos de tarjeta.

**Acceptance Scenarios**:

1. **Given** una autorización, un cobro, una liberación o un fallo, **When** ocurren, **Then** queda
   registrada una transacción con su tipo, estado, monto, resultado, referencia de la pasarela y
   fecha, asociada al pedido correspondiente.
2. **Given** un cliente con un pedido propio, **When** lo consulta, **Then** ve su estado (Pendiente),
   sus importes, productos, dirección y el estado de su pago (reservado, cobrado, liberado).
3. **Given** un pedido de otro cliente, **When** intenta consultarlo, **Then** se responde como no
   encontrado, sin revelar su existencia.
4. **Given** cualquier registro, respuesta o bitácora, **When** se inspecciona, **Then** no contiene
   números de tarjeta, códigos de seguridad ni otros datos sensibles de pago.

### Edge Cases

- El producto se pausa o el restaurante cierra entre el resumen y la confirmación: se rechaza sin
  reserva ni pedido (Historia 2).
- La autorización prospera pero falla el guardado del pedido: la compensación libera la reserva; si el
  servidor cae antes de compensar, el reintento con la misma clave recupera la misma reserva y completa
  el pedido.
- Respuesta de la pasarela tardía o duplicada: no genera un segundo pedido ni una segunda reserva.
- Dos pedidos distintos del mismo cliente (carritos de momentos distintos): son independientes, cada
  uno con su propia reserva.
- La captura y la liberación solicitadas a la vez para el mismo pedido: solo una prospera.
- Tiempo de espera de la pasarela agotado: se trata como fallo, sin pedido.
- Tarjeta que exige verificación adicional (3D Secure): se trata como autorización fallida.
- Método de pago no ofrecido o manipulado: se rechaza.
- El pedido se rechaza o se cancela antes de aceptación: su reserva se libera y nunca se cobra
  (disparado por la spec 007).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 (RF-14)**: Un cliente con sesión DEBE poder confirmar su carrito; el sistema DEBE registrar
  un pedido en estado Pendiente de aceptación, asociado al cliente, al restaurante, a las líneas
  (producto, nombre, precio y cantidad), a la dirección de entrega y a los importes.
- **FR-002 (RF-14)**: Antes de registrar el pedido, el sistema DEBE validar de nuevo que el restaurante
  esté habilitado y abierto, que los productos estén disponibles y no retirados, que las cantidades
  sean válidas y que la dirección esté dentro de la zona de reparto; si alguna falla, NO DEBE crear
  pedido ni reservar monto.
- **FR-003 (RF-14)**: El sistema DEBE recalcular en el servidor subtotal, envío, comisión y total con
  los precios vigentes (reglas de la spec 005), y NO DEBE aceptar importes, precios ni estados
  enviados por el cliente. Si los importes difieren de los mostrados al cliente, DEBE pedir una nueva
  confirmación sin crear pedido.
- **FR-004 (RF-14)**: Los precios, nombres, cantidades e importes DEBEN quedar fijados en el pedido al
  registrarse, de modo que cambios posteriores del catálogo no los alteren.
- **FR-005 (RF-14)**: Un pedido DEBE registrarse una sola vez por confirmación lógica: una
  confirmación repetida o simultánea NO DEBE duplicar pedido ni reserva y DEBE devolver el resultado
  del pedido ya registrado.
- **FR-006 (RF-15)**: El cliente DEBE poder revisar el resumen y seleccionar un método de pago entre
  los ofrecidos al confirmar; un método no ofrecido DEBE rechazarse.
- **FR-007 (RF-16)**: Al confirmar, el sistema DEBE autorizar (reservar) el monto total en el medio de
  pago del cliente mediante la pasarela externa. Si la autorización falla o la pasarela no responde,
  NO DEBE registrar el pedido, DEBE conservar el carrito y DEBE mostrar un mensaje claro.
- **FR-008 (RF-16)**: El sistema DEBE ofrecer la operación de capturar (cobrar) el monto reservado de
  un pedido Pendiente y la de liberar la reserva. Cada una DEBE ejecutarse a lo sumo una vez sobre la
  misma reserva y nunca ambas. NO DEBE existir ninguna operación de reembolso.
- **FR-009 (RF-16)**: Capturar y liberar DEBEN estar disponibles para que la spec 007 las dispare al
  aceptar, rechazar o cancelar; en este incremento NO se implementan esas decisiones ni se exponen
  como acciones libres del cliente.
- **FR-010 (RF-16)**: NO DEBE quedar un pedido sin su reserva ni una reserva sin pedido: ante un fallo
  controlado entre ambos pasos, el estado final DEBE ser coherente (sin reserva huérfana). Una caída
  del servidor entre autorizar y guardar queda como riesgo residual registrado (webhooks y
  conciliación aplazados por la constitución).
- **FR-011 (RF-17)**: Toda transacción de pago (autorizada, cobrada, liberada o fallida) DEBE
  registrarse con su tipo, estado, monto, resultado, referencia de la pasarela y fecha, asociada al
  pedido; un fallo de autorización DEBE registrarse aunque no se cree pedido.
- **FR-012 (RF-17)**: El cliente DEBE poder consultar sus pedidos propios con su estado y el estado de
  su pago; un pedido ajeno se responde como no encontrado.
- **FR-013**: El sistema NUNCA DEBE almacenar ni registrar datos de tarjeta; los datos sensibles de
  pago DEBEN tratarse únicamente por la pasarela, que se usa en modo de pruebas, con comunicación
  cifrada hacia ella.
- **FR-014**: Tras registrar el pedido con la reserva autorizada, el carrito del cliente DEBE
  vaciarse; si la confirmación falla, el carrito DEBE conservarse íntegro.
- **FR-015 (RF-04)**: Solo un cliente con sesión válida y cuenta habilitada DEBE confirmar y consultar
  sus pedidos, y SOLO los propios: 401 sin autenticación, 403 con otro rol o cuenta inhabilitada,
  recurso ajeno como no encontrado. El propietario se determina por la sesión.
- **FR-016**: La interfaz DEBE ofrecer el resumen de confirmación, la selección del método de pago, el
  resultado (pedido registrado o error claro) y la consulta del pedido, sin presentar como
  implementados la aceptación, el rechazo, la cancelación ni el historial de pedidos.

### Key Entities *(include if feature involves data)*

- **Pedido**: cliente, restaurante, estado (Pendiente en este incremento), dirección de entrega
  fijada, importes (subtotal, envío, comisión, total), método de pago elegido, fecha de creación y
  clave de confirmación que evita duplicados.
- **Línea de pedido**: producto, nombre y precio fijados, cantidad y total de la línea.
- **Transacción de pago**: pedido asociado, tipo (autorización, captura, liberación), estado
  (autorizada, cobrada, liberada, fallida), monto, resultado y mensaje, referencia de la pasarela y
  fecha; sin datos de tarjeta.
- **Método de pago**: opción ofrecida al cliente al confirmar, procesada por Stripe en pruebas.
- **Carrito, Dirección, Producto, Restaurante y Cuenta** (specs 005, 004, 003 y 002): aportan los
  datos a confirmar y las reglas de validación; el carrito se vacía al confirmar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cliente con carrito y dirección listos confirma su pedido y ve el resultado en menos
  de 1 minuto, y el pedido se conserva tras reiniciar la aplicación.
- **SC-002**: En el 100 % de los casos de prueba, no se crea pedido si el restaurante no está
  habilitado y abierto, si algún producto no está disponible, si las cantidades son inválidas o si la
  dirección está fuera de zona.
- **SC-003**: En el 100 % de los casos de prueba, el monto reservado coincide al céntimo con el total
  recalculado en el servidor, sin importar los valores enviados por el cliente.
- **SC-004**: En el 100 % de los casos de prueba con autorización fallida o pasarela caída, no se crea
  pedido, no queda reserva activa y el carrito se conserva con un mensaje claro.
- **SC-005**: Con confirmaciones repetidas o simultáneas (al menos 10 envíos de la misma
  confirmación), existe exactamente un pedido y una reserva.
- **SC-006**: En el 100 % de los casos de prueba, cada reserva se cobra o se libera como máximo una
  vez, nunca ambas, y no existe reembolso.
- **SC-007**: El 100 % de las transacciones de pago de prueba quedan registradas y asociadas a su
  pedido, y una inspección de registros y respuestas no encuentra datos de tarjeta.
- **SC-008**: El 100 % de los accesos sin sesión, con otro rol, con cuenta inhabilitada o sobre
  pedidos de otro cliente se rechaza sin revelar datos ni modificar estado.

## Assumptions

- **Pasarela**: Stripe, única pasarela, solo en modo de pruebas (sandbox), según decisión del usuario
  del 2026-11-17 y constitución 3.1.0. Las claves de prueba (`sk_test_` y `pk_test_`) residen solo en
  `backend/.env`, fuera de commits, registros y respuestas; el sistema rechaza cualquier clave
  secreta que no sea de prueba. No hay pasarelas reales ni otras pasarelas.
- **Reserva con captura manual**: la autorización reserva el monto en el medio de pago del cliente
  sin cobrarlo; el cobro ocurre solo al capturar. RNF-06 y RNF-08 no se declaran cumplidos por una
  ejecución local con claves de prueba; su evidencia completa queda pendiente del despliegue.
- **Métodos de pago**: tarjeta de crédito o débito procesada por Stripe en pruebas; efectivo y otros
  métodos quedan fuera. Los datos de tarjeta los captura el formulario seguro de Stripe en el
  frontend, no el sistema; este solo maneja referencias de la pasarela.
- **Pruebas**: las pruebas unitarias usan dobles y no llaman a Stripe real; las pruebas contra el
  sandbox son opcionales y manuales (constitución, Principio V).
- **Estado inicial**: el pedido queda en Pendiente; los estados posteriores (Aceptado, Rechazado,
  Cancelado, etc.) y cualquier tiempo límite de respuesta del restaurante pertenecen a la spec 007.
- **Verificación adicional**: no se soporta 3D Secure ni otros pasos de autenticación extra; se usan
  tarjetas de prueba que no los exigen (decisión del usuario, 2026-11-17).
- **Cobro completo**: la captura cobra el monto total reservado; no hay cobros parciales ni propinas.
- **Reserva vigente**: se asume que la reserva se mantiene hasta que la spec 007 decide; el manejo de
  vencimiento de reservas por la pasarela queda fuera de este incremento.
- **Quién dispara**: capturar y liberar son operaciones internas que usará la spec 007; aquí se
  verifican desde pruebas, no desde acciones libres del cliente.
- **Importes y precios**: soles con dos decimales y reglas de redondeo de la spec 005; el pedido fija
  los precios vigentes al confirmar.
- **Alcance de consulta**: el cliente consulta un pedido propio concreto; el listado e historial de
  pedidos pertenecen a la spec 010.
- **Fuera del incremento**: aceptación, rechazo y cancelación (spec 007), asignación de repartidor,
  historial (spec 010), reembolsos, conciliación contable y pasarelas reales o varias.
- **Dependencias y restricciones heredadas**: specs 002 a 005 y constitución 3.1.0 (local, HTTP en
  localhost, PostgreSQL, RNF-07 aplicado a estas operaciones, RNF-11 con al menos 90 % de líneas de
  lógica de negocio, pruebas sin servicios externos reales, sin E2E automatizadas).
- **Trazabilidad**: RF-04, RF-14, RF-15, RF-16 y RF-17 de la [propuesta](../../Propuesta_de_Proyecto.docx)
  y la [constitución](../../.specify/memory/constitution.md); solicitud del usuario del 2026-11-17.
