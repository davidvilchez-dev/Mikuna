# Feature Specification: Vencimiento automático de pedidos pendientes

**Feature Branch**: Sin rama nueva; el directorio de la especificación es independiente de Git.

**Created**: 2026-11-22

**Status**: Draft

**Input**: Incremento 012 del usuario. Completa RF-16 y RF-20 con el caso que la spec 007 dejó fuera
("Un pedido Pendiente que nadie decide permanece Pendiente; el vencimiento automático queda fuera").
Si el restaurante no acepta ni rechaza un pedido Pendiente dentro de un plazo (por defecto 15 minutos
desde su creación, configurable), el sistema lo pasa a Cancelado con el motivo "sin respuesta del
restaurante", libera la reserva sin cobro y avisa al cliente y al restaurante. Depende de la spec 006
(pedido Pendiente con reserva y la operación de liberarla), 007 (estados del pedido, cancelación por el
sistema ya usada para "reserva vencida", bloqueo frente a decisiones simultáneas) y 009 (avisos).
Fuera de alcance: cancelación después de aceptar, cancelación por el administrador y reembolsos.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Un pedido sin respuesta del restaurante se cancela solo (Priority: P1)

Como cliente, quiero que si el restaurante no responde a mi pedido en un tiempo razonable se cancele
automáticamente y se libere mi reserva, para no quedarme esperando indefinidamente ni con dinero
retenido.

**Why this priority**: Es el núcleo del incremento. Sin él, un pedido ignorado queda Pendiente para
siempre, el cliente no sabe qué pasa y la reserva sigue retenida hasta que la pasarela la venza sola.

**Independent Test**: Crear un pedido Pendiente, dejar pasar el plazo sin decidir y comprobar que queda
Cancelado por el sistema con el motivo "sin respuesta del restaurante" y la reserva liberada sin cobro.

**Acceptance Scenarios**:

1. **Given** un pedido Pendiente pagado con tarjeta cuyo plazo vence, **When** el sistema lo detecta,
   **Then** pasa a Cancelado por el sistema con motivo "sin respuesta del restaurante" y la reserva se
   libera sin cobro.
2. **Given** un pedido Pendiente pagado en efectivo cuyo plazo vence, **When** el sistema lo detecta,
   **Then** pasa a Cancelado por el sistema con el mismo motivo y no se intenta ninguna operación con la
   pasarela.
3. **Given** un pedido que el restaurante aceptó o rechazó antes de que venciera el plazo, **When** el
   plazo se cumple, **Then** el pedido no cambia.
4. **Given** un pedido que el cliente canceló antes de que venciera el plazo, **When** el plazo se cumple,
   **Then** el pedido no cambia y la reserva no se libera dos veces.
5. **Given** un pedido cancelado por vencimiento, **When** el restaurante intenta aceptarlo o rechazarlo,
   **Then** se rechaza con un mensaje que indica que el pedido venció por falta de respuesta.

---

### User Story 2 - Ninguna decisión simultánea con el vencimiento deja el pedido incoherente (Priority: P1)

Como plataforma, necesito que si el restaurante acepta (o rechaza, o el cliente cancela) justo cuando el
plazo vence, el pedido termine en un único estado coherente, para no cobrar un pedido cancelado ni
cancelar un pedido cobrado.

**Why this priority**: Es una garantía de integridad del dinero y del estado (constitución: carreras
entre aceptación y cancelación resueltas y probadas). Sin ella el vencimiento no es confiable.

**Independent Test**: Lanzar de forma simultánea el vencimiento y la aceptación del mismo pedido,
repetidas muchas veces; comprobar que en todos los casos el resultado es exactamente uno: Aceptado y
cobrado, o Cancelado y liberado.

**Acceptance Scenarios**:

1. **Given** un pedido Pendiente en el instante de su vencimiento, **When** el restaurante lo acepta al
   mismo tiempo, **Then** solo una acción prevalece; si gana la aceptación el pedido queda Aceptado y
   cobrado y el vencimiento no hace nada; si gana el vencimiento el restaurante recibe el mensaje de que
   el pedido venció.
2. **Given** el mismo caso con un rechazo del restaurante o una cancelación del cliente, **When** se
   procesan junto al vencimiento, **Then** la reserva se libera una sola vez y queda un único motivo y un
   único responsable registrados.
3. **Given** cualquier desenlace, **When** se revisa el pedido y su pago, **Then** nunca está cobrado y
   liberado a la vez ni Aceptado y Cancelado a la vez.

---

### User Story 3 - Cliente y restaurante ven cuánto tiempo queda (Priority: P2)

Como restaurante, quiero ver cuánto tiempo me queda para responder cada pedido pendiente, para priorizar
los que están por vencer. Como cliente, quiero ver cuánto falta para que mi pedido se cancele si el
restaurante no responde, para saber qué esperar.

**Why this priority**: Mejora la experiencia y reduce vencimientos evitables, pero el vencimiento
funciona sin esta vista.

**Independent Test**: Con un pedido Pendiente, abrir la vista del restaurante y la del cliente y
comprobar que ambas muestran el tiempo restante y que coincide con el plazo configurado.

**Acceptance Scenarios**:

1. **Given** un pedido Pendiente, **When** el restaurante abre su lista de pedidos, **Then** ve el tiempo
   restante para responder ese pedido, que disminuye mientras la vista está abierta.
2. **Given** un pedido Pendiente propio, **When** el cliente abre la página del pedido, **Then** ve el
   tiempo restante antes de la cancelación automática.
3. **Given** un pedido al que le queda poco tiempo (último 20 % del plazo), **When** el restaurante lo ve,
   **Then** el pedido se destaca como próximo a vencer.
4. **Given** que el tiempo mostrado llega a cero, **When** la vista sigue abierta, **Then** deja de
   ofrecer aceptar o rechazar y refleja el estado Cancelado en cuanto el sistema lo registra, sin
   mostrar tiempos negativos.
5. **Given** un pedido que ya no está Pendiente, **When** se muestra, **Then** no aparece ningún tiempo
   restante.

---

### User Story 4 - El vencimiento sobrevive a reinicios del servidor (Priority: P2)

Como plataforma, necesito que los pedidos cuyo plazo venció mientras el servicio estaba detenido se
cancelen al volver, para que una caída no deje pedidos Pendientes vencidos para siempre.

**Why this priority**: Garantiza que la regla se cumpla siempre, pero solo importa ante caídas o
reinicios.

**Independent Test**: Crear pedidos Pendientes, detener el servicio, esperar más que el plazo, volver a
iniciarlo y comprobar que todos los vencidos se cancelan con la reserva liberada.

**Acceptance Scenarios**:

1. **Given** pedidos Pendientes cuyo plazo venció con el servicio detenido, **When** el servicio vuelve a
   iniciarse, **Then** se cancelan por vencimiento poco después del arranque.
2. **Given** varias instancias del servicio en ejecución, **When** vence un pedido, **Then** se cancela
   una sola vez, con un único registro de cambio de estado, una única liberación y un único aviso a cada
   destinatario.

---

### Edge Cases

- **Fallo de la pasarela al liberar**: el pedido no debe quedar Cancelado con la reserva activa sin que
  quede constancia; se aplica el mismo comportamiento consistente y reintentable que la spec 007 define
  para la liberación al rechazar o cancelar.
- **Reserva que la pasarela ya venció por su cuenta**: el pedido igual se cancela por falta de respuesta y
  el pago queda como liberado, sin error.
- **Cambio del plazo configurado**: el nuevo plazo se aplica a los pedidos creados a partir del cambio;
  los pedidos ya creados conservan el plazo con el que nacieron, así el tiempo mostrado no salta.
- **Restaurante cerrado, deshabilitado o con la cuenta suspendida**: no detiene el plazo; el pedido vence
  igual si nadie lo decide.
- **El cliente cancela entre el vencimiento y su procesamiento**: prevalece la cancelación del cliente (se
  registra como suya); el efecto sobre el dinero es el mismo, Cancelado y liberado sin cobro.
- **Muchos pedidos vencidos a la vez** (por ejemplo, tras una caída larga): todos se cancelan; el fallo de
  uno no impide procesar los demás.
- **Reloj del navegador desfasado**: el tiempo restante mostrado se basa en el momento de vencimiento que
  da el servidor y no debe permitir aceptar un pedido que el servidor ya considera vencido.
- **Pedidos anteriores a este incremento**: los Pendientes que ya existían toman como plazo su momento de
  creación más el plazo vigente al desplegar, por lo que los muy antiguos vencen en la primera revisión.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST asignar a cada pedido, al crearse como Pendiente, un momento de vencimiento
  igual a su momento de creación más el plazo de respuesta vigente.
- **FR-002**: El plazo de respuesta MUST ser configurable por la plataforma, con un valor por defecto de
  15 minutos, y MUST ser el mismo para todos los restaurantes.
- **FR-003**: El sistema MUST pasar a Cancelado todo pedido que siga Pendiente después de su momento de
  vencimiento, registrando como responsable al sistema y como motivo "sin respuesta del restaurante".
- **FR-004**: Al cancelar por vencimiento un pedido pagado con tarjeta, el sistema MUST liberar la
  reserva sin cobro; con efectivo MUST NOT intentar ninguna operación con la pasarela.
- **FR-005**: El sistema MUST cancelar un pedido vencido en un máximo de 1 minuto después de su momento de
  vencimiento mientras el servicio esté en marcha.
- **FR-006**: El vencimiento MUST reutilizar la transición existente de Pendiente a Cancelado por el
  sistema y MUST NOT introducir estados ni transiciones nuevas.
- **FR-007**: El vencimiento, la aceptación, el rechazo y la cancelación del cliente sobre un mismo pedido
  MUST serializarse de modo que solo una acción prevalezca; las demás MUST recibir un mensaje de que el
  pedido ya cambió de estado y MUST NOT producir efectos.
- **FR-008**: El sistema MUST NOT cobrar, liberar, registrar el cambio de estado ni avisar más de una vez
  por pedido vencido, aunque haya varias instancias o reintentos.
- **FR-009**: Tras un reinicio, el sistema MUST cancelar los pedidos que vencieron mientras estaba detenido
  dentro del primer minuto de funcionamiento.
- **FR-010**: El sistema MUST avisar al cliente y al restaurante cuando un pedido se cancela por
  vencimiento, con un texto que indique la falta de respuesta del restaurante.
- **FR-011**: El restaurante MUST ver, para cada pedido Pendiente, el tiempo restante para responder, y
  los pedidos en el último 20 % del plazo MUST destacarse como próximos a vencer.
- **FR-012**: El cliente MUST ver, en la página de su pedido Pendiente, el tiempo restante antes de la
  cancelación automática.
- **FR-013**: Las vistas MUST calcular el tiempo restante a partir del momento de vencimiento informado por
  el servidor, MUST NOT mostrar tiempos negativos y MUST dejar de ofrecer aceptar o rechazar cuando el
  tiempo llegue a cero.
- **FR-014**: Un intento de aceptar o rechazar un pedido cancelado por vencimiento MUST rechazarse con un
  mensaje que indique que venció por falta de respuesta.
- **FR-015**: La regla de cancelación del cliente MUST NOT cambiar: solo puede cancelar mientras el pedido
  esté Pendiente (RF-20, spec 007).
- **FR-016**: El cambio de estado por vencimiento MUST registrar su motivo, de modo que la consulta
  administrativa de pedidos lo muestre igual que los demás motivos. Mostrar motivos en el historial de
  cliente y restaurante queda fuera (spec 010).

### Key Entities

- **Pedido**: gana el **momento de vencimiento** (fijado al crearse, inmutable después). Su cambio de
  estado por vencimiento se registra como cualquier otro: estado anterior y nuevo, responsable (sistema),
  motivo y momento.
- **Plazo de respuesta**: parámetro de la plataforma, en minutos, con valor por defecto 15.
- **Pago**: pasa de Reservado a Liberado al vencer el pedido; en efectivo no cambia de forma visible para
  la pasarela.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los pedidos que siguen Pendientes al cumplirse su plazo quedan Cancelados en
  menos de 1 minuto después del vencimiento, con el servicio en marcha.
- **SC-002**: En 100 repeticiones de vencimiento simultáneo con aceptación, rechazo o cancelación del
  cliente, ningún pedido queda cobrado y liberado, ni con más de un cambio de estado por vencimiento.
- **SC-003**: Tras un reinicio, el 100 % de los pedidos vencidos durante la caída quedan Cancelados en el
  primer minuto de funcionamiento.
- **SC-004**: Ningún pedido permanece Pendiente más de su plazo más 1 minuto mientras el servicio está en
  marcha.
- **SC-005**: El tiempo restante que ven cliente y restaurante difiere en menos de 5 segundos del
  vencimiento real registrado.
- **SC-006**: Cada pedido cancelado por vencimiento genera exactamente un aviso al cliente y uno al
  restaurante.

## Assumptions

- El plazo se mide desde la creación del pedido Pendiente, no desde que el restaurante abre su panel.
- Un único plazo global para toda la plataforma basta en esta etapa; plazos por restaurante o por horario
  quedan fuera.
- Cancelar por vencimiento es una cancelación por el sistema antes de la aceptación, coherente con la
  constitución (desde Pendiente solo Rechazado o Cancelado) y con el precedente de "reserva vencida" de
  la spec 007; no se requiere enmienda.
- El plazo por defecto (15 minutos) es bastante menor que la duración de la reserva en la pasarela
  (alrededor de 7 días), así que la reserva siempre se libera antes de que la pasarela la venza sola.
- Los avisos usan el mecanismo de la spec 009 y el tipo de aviso de cancelación ya existente.
- La cancelación después de aceptar, la cancelación por el administrador y los reembolsos quedan fuera de
  este incremento.
