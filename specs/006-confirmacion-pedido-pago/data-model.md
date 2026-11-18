# Data Model: Confirmación del pedido y pago (006)

Tablas nuevas creadas por `ddl-auto=update` (sin migraciones). Importes en `NUMERIC(10,2)`; fechas en
UTC. Los identificadores son UUID. Las referencias a otros módulos son UUID sin clave foránea
cruzada obligatoria, como en 005.

## Pedido (`pedidos`) — módulo `order`

| Campo | Tipo | Regla |
| --- | --- | --- |
| id | UUID | Generado en el servidor antes de autorizar. |
| customer_id | UUID | Del token; nunca del cuerpo. |
| restaurant_id | UUID | Del carrito. |
| status | enum | `PENDING` en este incremento (los demás estados son de la spec 007). |
| confirmation_key | String(64) | Clave de idempotencia del cliente. **UNIQUE (customer_id, confirmation_key).** |
| payment_method | enum | `CARD`. |
| subtotal, delivery_fee, commission, total | NUMERIC(10,2) | Recalculados con `CartPricing` al confirmar; el total = suma de los tres componentes. |
| restaurant_name | String | Fijado al confirmar. |
| delivery_label, delivery_street, delivery_district, delivery_reference, delivery_phone | String | Copia de la dirección al confirmar (no cambia si se edita o elimina después). |
| created_at | Instant | |

Índice por `(customer_id, created_at)`.

## Línea de pedido (`lineas_pedido`) — módulo `order`

| Campo | Tipo | Regla |
| --- | --- | --- |
| id | UUID | |
| order_id | UUID | FK al pedido; se eliminan con él. |
| product_id | UUID | Referencia informativa. |
| product_name | String | Fijado. |
| unit_price | NUMERIC(10,2) | Fijado al confirmar. |
| quantity | int | 1–20. |
| line_total | NUMERIC(10,2) | `unit_price × quantity`, exacto. |

## Pago (`pagos`) — módulo `payment`

Uno por pedido autorizado; fila que se bloquea para capturar o liberar.

| Campo | Tipo | Regla |
| --- | --- | --- |
| id | UUID | |
| order_id | UUID | **UNIQUE.** |
| customer_id | UUID | |
| status | enum | `AUTHORIZED` → `CAPTURED` o `RELEASED` (terminales y excluyentes). |
| amount | NUMERIC(10,2) | Total autorizado. |
| currency | String(3) | De configuración (`pen`). |
| gateway_ref | String | Id del `PaymentIntent` de Stripe (referencia, no dato de tarjeta). |
| created_at, updated_at | Instant | |

Transiciones: `AUTHORIZED --capture--> CAPTURED`; `AUTHORIZED --release--> RELEASED`. Cualquier otra
operación se rechaza (409) sin llamar a la pasarela. No existe estado de reembolso.

## Transacción de pago (`transacciones_pago`) — módulo `payment`

Registro histórico, un renglón por intento.

| Campo | Tipo | Regla |
| --- | --- | --- |
| id | UUID | |
| order_id | UUID, nulo | Nulo si la autorización falló y no hubo pedido. |
| customer_id | UUID | |
| confirmation_key | String(64), nulo | Presente en autorizaciones. |
| type | enum | `AUTHORIZATION`, `CAPTURE`, `RELEASE`. |
| status | enum | `AUTHORIZED`, `CAPTURED`, `RELEASED`, `FAILED`. |
| amount | NUMERIC(10,2) | |
| result_code, result_message | String, nulo | Código y mensaje seguros (sin datos de tarjeta). |
| gateway_ref | String, nulo | Id del `PaymentIntent`. |
| created_at | Instant | |

Índices por `order_id` y por `(customer_id, confirmation_key)`.

## Relaciones y dependencias de módulos

- `order` depende de `cart` (lee y vacía el carrito), `address`, `catalog`, `restaurant` y `payment`.
- `payment` no depende de `order`: usa `order_id` como UUID. Sin ciclos.
- Se vacía el carrito con `Cart.clear()` (conserva la dirección seleccionada, regla de 005).

## Reglas de validación y estado

- Confirmar exige carrito con al menos una línea elegible y dirección seleccionada dentro de zona.
- Toda línea del carrito debe seguir siendo elegible (producto activo y disponible, restaurante
  habilitado y abierto, cantidad 1–20); si alguna no lo es, se rechaza sin pedido (no se descartan en
  silencio).
- El pedido, sus líneas, el pago y la transacción de autorización se guardan en una sola transacción de
  BD junto con el vaciado del carrito.
