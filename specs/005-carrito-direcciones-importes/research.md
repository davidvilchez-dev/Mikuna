# Research: Carrito, direcciones y cálculo de importes

Decisiones rutinarias (principio VII), sin comparar alternativas en extenso. Origen: **C** =
constitución, **U** = usuario o spec, **P** = este plan.

## 1. Carrito y direcciones como entidades propias

- **Decision (U, spec; P):** tablas `carritos` (una por cliente), `lineas_carrito` y `direcciones`.
  `customer_id`, `restaurant_id`, `product_id` y `selected_address_id` son columnas UUID simples, sin
  `@ManyToOne` entre módulos, como `owner_id` en 003 y `restaurant_id` en 004. Solo `Cart` →
  `CartLine` usa relación (`@OneToMany`, mismo módulo).
- **Rationale:** acoplamiento mínimo entre `cart`, `address`, `catalog` y `restaurant`.

## 2. Concurrencia sobre el carrito (constitución IV)

- **Decision (P):** cada mutación (agregar, cambiar cantidad, eliminar, vaciar, seleccionar
  dirección) corre en una transacción que lee el carrito con `PESSIMISTIC_WRITE` (`select ... for
  update`). Dos pestañas del mismo cliente se serializan, de modo que el segundo «agregar» suma a la
  línea creada por el primero. `UNIQUE (cart_id, product_id)` y `UNIQUE (customer_id)` son la red de
  seguridad; una violación se reintenta una vez o se traduce a 409.
- **Carrito inexistente:** se crea al primer uso; si dos solicitudes lo crean a la vez, la
  violación de `UNIQUE (customer_id)` se captura y se relee el carrito existente.
- **Rationale:** el carrito es de un solo cliente, por lo que el bloqueo es barato y evita
  versiones desfasadas que el usuario tendría que reintentar. Comprobar-y-escribir sin protección no
  basta (constitución IV).
- **Alternativa descartada:** `@Version` con reintento por el cliente: obliga a la interfaz a
  recargar tras cada doble clic.

## 3. Importes calculados, no persistidos (RF-13)

- **Decision (U, spec; P):** `CartPricing` calcula en cada consulta:
  - `subtotal = Σ precio vigente × cantidad` de las líneas elegibles.
  - `deliveryFee` = tarifa fija configurada si hay dirección válida seleccionada; si no, pendiente.
  - `commission = round(subtotal × commissionRate / 100, 2, HALF_UP)`.
  - `total = subtotal + deliveryFee + commission`.
  - Con carrito vacío o sin líneas elegibles, todo vale `0.00`.
  Se calcula en el servidor con `BigDecimal`; el cliente nunca envía importes. Se redondea una sola
  vez (la comisión) y el total suma componentes ya redondeados, por lo que no hay diferencias de
  céntimos entre componentes y total. Este mismo cálculo es el que debe invocar la confirmación del
  pedido (spec 006) para fijar el monto a pagar; no se confía en importes mostrados antes.
- **Rationale:** «recalcular cada vez que cambia carrito o dirección» se cumple sin invalidar nada,
  y evita importes obsoletos tras cambios de precio. El pedido (spec 006) fijará los valores.
- **Sin dirección válida:** `deliveryFee`, `commission` y `total` se devuelven `null` y un campo
  `pending` explica qué falta. **Decisión derivada:** la comisión también queda pendiente, porque la
  spec dice que sin dirección «el envío y el total quedan pendientes» y el total depende de ella;
  solo el subtotal se muestra.

## 4. Elegibilidad al agregar y al consultar

- **Decision (U, spec; P):** al agregar, el producto debe estar activo y disponible y su restaurante
  debe pasar `OrderEligibility.canReceiveOrders` (aprobado, cuenta habilitada, disponible y dentro
  de horario). El motivo del rechazo es 409 con un mensaje claro.
- **Al consultar:** cada línea se evalúa de nuevo; si el producto o el restaurante dejaron de ser
  elegibles, la línea sale con `available = false` y no entra en los importes. El carrito no se
  vacía automáticamente.
- **Cambio de restaurante:** agregar un producto de otro restaurante con líneas existentes → 409
  `CART_OTHER_RESTAURANT`; la interfaz ofrece `DELETE /api/customer/cart` y reintenta.
- **Rationale:** una sola regla de elegibilidad, la de 003.

## 5. Zona de reparto por lista de distritos (RF-12)

- **Decision (U, spec; P):** `DeliveryZone` normaliza cada distrito (minúsculas, sin tildes) y
  comprueba que el distrito de la dirección figure en la lista configurada. La lista viene de
  `mikuna.delivery.districts` (valores separados por comas, en `backend/.env`).
- **Dirección fuera de zona:** 400 con error por el campo `district`; no se guarda ni se edita.
- **Selección vigente:** al consultar el carrito se revalida la dirección seleccionada; si el
  distrito ya no está cubierto, se limpia la selección y el resumen lo avisa.
- **Rationale:** sin mapas (MapTiler aplazado); misma normalización de texto que 004.

## 6. Dirección seleccionada

- **Decision (P):** `selected_address_id` en `carritos`. Seleccionar exige una dirección propia y en
  zona; eliminar una dirección limpia la selección si era esa (en la misma transacción, con el
  carrito bloqueado).
- **Rationale:** «solo una seleccionada a la vez» queda garantizado por construcción; no hace falta
  una marca por dirección.

## 7. Configuración sin valores por defecto

- **Decision (C, U):** `mikuna.delivery.districts`, `mikuna.delivery.fee` y
  `mikuna.delivery.commission-rate` se leen de variables de entorno sin valor por defecto; el
  arranque falla si faltan o son inválidos (tarifa ≥ 0, comisión entre 0 y 100, al menos un distrito).
  `backend/.env.example` las lista sin valor. Las pruebas usan valores fijos en
  `src/test/resources/application.properties`.
- **Rationale:** la constitución prohíbe inventar estos valores; el usuario debe indicarlos.

## 8. Límites

- **Decision (P):** cantidad máxima por línea 20; líneas máximas por carrito 30; direcciones
  máximas por cliente 10. Se aplican en el servicio y se reflejan en el formulario.
- **Rationale:** valores razonables que la spec deja a este plan; ajustables en una constante.

## 9. Seguridad y propiedad

- **Decision (P):** `SecurityConfig` añade `/api/customer/** → hasRole("CUSTOMER")`. Las consultas
  filtran siempre por `customer_id` del token; un recurso ajeno equivale a inexistente (404).
- **Rationale:** consistente con `/api/restaurant/**` y con 004 (producto ajeno → 404).
