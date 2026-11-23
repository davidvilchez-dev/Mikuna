# Research: Vencimiento automático de pedidos pendientes

## §1. Cómo se detectan los pedidos vencidos

- **Decision**: barrido periódico con `@Scheduled` (Spring, `@EnableScheduling`), cada 15 s por defecto
  (`mikuna.orders.expiry-sweep`), con un primer barrido a los 5 s del arranque. Busca hasta 100 ids de pedidos
  `PENDING` con `expires_at <= ahora`, los más antiguos primero, y procesa cada uno en su propia transacción.
- **Rationale**: cumple FR-005 (≤ 1 min) y FR-009 (tras un reinicio, el primer barrido llega a los 5 s) sin
  dependencias nuevas. El estado vive en PostgreSQL, así que nada se pierde con una caída. Un fallo en un
  pedido no afecta al resto (transacción por pedido).
- **Alternatives considered**: temporizador en memoria por pedido (se pierde al reiniciar; duplicado con
  varias instancias); Quartz o ShedLock (dependencia nueva innecesaria: el bloqueo de fila ya evita el doble
  procesamiento); cola con retardo (infraestructura aplazada por la constitución).

## §2. Exclusión mutua con aceptar, rechazar, cancelar y otras instancias

- **Decision**: el vencimiento de un pedido bloquea su fila con `findByIdForUpdate` (el mismo bloqueo de la
  spec 007), y con la fila bloqueada vuelve a comprobar `status = PENDING` y `expires_at <= ahora`. Si no se
  cumple, no hace nada. Después bloquea el pago (orden fijo pedido → pago) y libera.
- **Rationale**: aceptar, rechazar y cancelar ya se serializan con ese bloqueo; quien llega segundo ve el
  estado nuevo. Varias instancias ejecutando el barrido a la vez se serializan igual y solo la primera cambia
  el estado (FR-007, FR-008). Sin interbloqueos porque el orden pedido → pago es el mismo en todas las
  operaciones.
- **Alternatives considered**: `for update skip locked` en la consulta del barrido (no evita la recomprobación
  y complica el caso de un pedido que se está aceptando: es más simple esperar y recomprobar).

## §3. Aceptar o rechazar un pedido ya vencido que el barrido aún no procesó

- **Decision**: `accept` y `reject` del restaurante, con la fila bloqueada y el pedido `PENDING`, comprueban
  `expires_at <= ahora`. Si venció, aplican ellos mismos el vencimiento (liberar + Cancelado por el sistema
  con el motivo de vencimiento), confirman la transacción y el controlador responde **409 `ORDER_EXPIRED`**.
  Mismo patrón que la reserva vencida de la spec 007 (resultado con indicador, no excepción, para no revertir).
- **Rationale**: el servidor es la única fuente de verdad (edge case del reloj del navegador): nunca se acepta
  un pedido cuyo plazo terminó, aunque el barrido vaya hasta 15 s atrasado.
- **Cancelación del cliente**: se mantiene sin cambios (FR-015). Si el cliente cancela un pedido vencido que
  el barrido aún no procesó, el resultado de dinero es idéntico (Cancelado, liberado) y queda registrado como
  cancelación del cliente; no hay motivo para convertir su acción en un error.

## §4. Mensaje al intentar decidir sobre un pedido ya cancelado por vencimiento (FR-014)

- **Decision**: si `accept`/`reject` encuentran el pedido `CANCELLED` y su último cambio tiene el motivo de
  vencimiento, responden 409 `ORDER_EXPIRED` con «El pedido venció porque no se respondió a tiempo»; en otro
  caso, el `INVALID_TRANSITION` genérico de siempre. Consulta nueva en `OrderStatusChangeRepository`.

## §5. Momento de vencimiento: columna fija por pedido y pedidos anteriores

- **Decision**: columna nueva `pedidos.expires_at` (nullable en BD por `ddl-auto=update`), fijada al crear el
  pedido como `created_at + plazo vigente` e inmutable. Al arrancar, un `UPDATE` nativo rellena
  `expires_at = created_at + plazo` en las filas que lo tengan nulo (pedidos anteriores al incremento).
- **Rationale**: el plazo fijado al nacer cumple el edge case de cambio de configuración sin saltos; el relleno
  hace que los Pendientes antiguos venzan en el primer barrido, como dice la spec. Los no Pendientes también
  se rellenan, pero solo se muestra el tiempo en Pendientes.
- **Alternatives considered**: calcular siempre `created_at + plazo` sin columna (un cambio de configuración
  movería todos los plazos); migración Flyway (el proyecto usa `ddl-auto=update`, sin migraciones).

## §6. Plazo configurable

- **Decision**: `mikuna.orders.response-timeout` (`Duration`, por defecto `PT15M`, variable
  `MIKUNA_ORDER_RESPONSE_TIMEOUT`). En pruebas se fija en `PT15M` y los pedidos se vencen moviendo el reloj o
  la columna.

## §7. Tiempo restante sin depender del reloj del navegador (SC-005)

- **Decision**: las respuestas de pedido del cliente y del restaurante incluyen `expiresAt` (instante) y
  `secondsToExpire` (segundos restantes calculados en el servidor al responder; `null` si no está Pendiente).
  La interfaz fija su fecha límite local como `recibido + secondsToExpire` y descuenta con un intervalo de 1 s.
- **Rationale**: un desfase del reloj del cliente no altera la cuenta; el error queda acotado a la latencia de
  la respuesta (< 5 s). El restaurante ya refresca su lista periódicamente, lo que recalibra la cuenta.
- **Alternatives considered**: solo `expiresAt` (sensible al reloj local); cabecera `Date` (no accesible desde
  el cliente HTTP compartido).

## §8. Avisos con el motivo

- **Decision**: `OrderStatusChangedEvent` gana un campo `reason` (con constructor de compatibilidad de 6
  argumentos). `NotificationRecipients` usa textos específicos para Cancelado con el motivo de vencimiento:
  cliente «Se canceló tu pedido X: el restaurante no respondió a tiempo»; restaurante «Se canceló el pedido X
  porque no se respondió a tiempo». El responsable es el sistema (sin cuenta), así que nadie queda excluido.
- **Rationale**: reutiliza el tipo `CANCELLED` existente (spec 009) y el aviso sale en la misma transacción que
  el cambio, una sola vez (SC-006).

## §9. Umbral «próximo a vencer»

- **Decision**: lógica pura en la interfaz: próximo a vencer cuando el tiempo restante ≤ 20 % del plazo total
  (`expiresAt - createdAt`). Probada con Vitest.
