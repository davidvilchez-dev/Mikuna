# Research: Confirmación del pedido y pago (006)

Decisiones puntuales para este incremento (constitución VII). Las tecnologías base ya están elegidas;
aquí solo se resuelve cómo integrar Stripe y cómo garantizar las invariantes de pedido y pago.

## 1. Cliente de Stripe en el backend

- **Decisión**: llamar a la API REST de Stripe (`https://api.stripe.com/v1`) con `RestClient`, igual
  que `CloudinaryImageStorage` (004). Una clase `StripeGateway` encapsula las tres llamadas y las
  pruebas la sustituyen por un mock de Mockito. No se añade el SDK `stripe-java`.
- **Razón**: sigue el patrón ya aceptado, evita una dependencia grande y solo se necesitan tres
  operaciones (crear y confirmar, capturar, cancelar). El soporte de `Idempotency-Key` es un simple
  encabezado.
- **Alternativas**: SDK `stripe-java` (más cómodo, pero dependencia nueva sin necesidad concreta).

## 2. Flujo de autorización con captura manual

- **Decisión**: el frontend crea un `PaymentMethod` con Stripe.js (`CardElement`, clave `pk_test_`); el
  backend recibe solo su id (`pm_...`) y crea un `PaymentIntent` con `capture_method=manual`,
  `confirm=true`, `payment_method`, `automatic_payment_methods[enabled]=true` y
  `automatic_payment_methods[allow_redirects]=never`. Estado `requires_capture` = reserva autorizada.
  Capturar = `POST /payment_intents/{id}/capture`; liberar = `POST /payment_intents/{id}/cancel`.
- **Razón**: los datos de tarjeta nunca pasan por el backend (RNF-08, FR-013); la captura manual
  reserva sin cobrar y el cobro llega con la spec 007.
- **Alternativas**: Checkout alojado (redirige fuera de la app; más difícil de integrar con el
  carrito); `PaymentIntent` creado antes y confirmado en el navegador (requiere webhooks para saber el
  resultado, aplazados por la constitución).

## 3. Verificación adicional (3D Secure)

- **Decisión**: con `allow_redirects=never`, una tarjeta que exige autenticación deja el intent en
  `requires_action`; el backend lo **cancela** y lo trata como autorización fallida (aclaración
  2026-11-17, opción A). Se usan tarjetas de prueba sin 3DS (p. ej. `4242 4242 4242 4242`).
- **Razón**: evita un estado intermedio «pendiente de verificación» que rompería la idempotencia y el
  estado Pendiente del pedido.

## 4. Moneda

- **Decisión**: soles (`pen`) como valor configurable `mikuna.payments.currency` (por defecto `pen`).
  Monto en céntimos enteros: `total × 100` exacto con `BigDecimal` (`longValueExact`).
- **Riesgo conocido**: confirmar con el sandbox que la cuenta de Stripe admite `pen`; si no, cambiar el
  valor sin tocar código. Se anota en el quickstart.

## 5. Idempotencia y concurrencia

- **Decisión**: el frontend genera una clave por intento de compra (UUID) y la envía en el encabezado
  `Idempotency-Key`. En el backend:
  1. `confirm` bloquea la fila del carrito del cliente (`lockByCustomerId`, igual que 005); las
     confirmaciones simultáneas del mismo cliente se serializan.
  2. `UNIQUE (customer_id, confirmation_key)` en `pedidos` es la red de seguridad persistente; una
     clave ya usada devuelve el pedido existente (200) sin tocar la pasarela.
  3. La llamada a Stripe usa `Idempotency-Key = "confirm-" + customerId + "-" + confirmationKey`, que
     depende solo de la clave del cliente: un reintento tras un fallo intermedio recupera el mismo
     `PaymentIntent` y no crea otra reserva.
- **Razón**: la constitución IV exige que reintentos no dupliquen pedidos ni reservas y que la garantía
  no sea solo «comprobar y luego escribir».

## 6. Ninguna reserva sin pedido (FR-010)

- **Decisión**: el orden es validar → autorizar en Stripe → guardar pedido, líneas, pago y
  transacción, vaciar carrito → confirmar la transacción de BD. Si cualquier paso posterior a la
  autorización falla, se **compensa** cancelando el `PaymentIntent` (best effort) antes de propagar el
  error. Si la llamada a Stripe agota el tiempo, se repite una vez con la misma clave de idempotencia;
  si devuelve un intent `requires_capture`, se cancela porque la confirmación ya se da por fallida.
- **Razón**: aislar el efecto externo al final minimiza la ventana. Webhooks, conciliación y
  recuperación avanzada están aplazados por la constitución; el riesgo residual (caída del servidor
  justo entre autorizar y guardar) se registra como pendiente, sin declararlo resuelto.
- **Alternativa descartada**: pedido «pendiente de pago» creado antes; añade estados no previstos en
  la máquina de estados de la constitución.

## 7. Registro de fallos de autorización

- **Decisión**: el fallo se registra como `PaymentTransaction` (`FAILED`, sin pedido) mediante un
  componente con `REQUIRES_NEW`, de modo que persista aunque la transacción principal se revierta.
  Se guarda un mensaje y el código de error de Stripe, nunca datos de tarjeta.

## 8. Capturar y liberar (operaciones internas)

- **Decisión**: `PaymentService.capture(orderId)` y `release(orderId)` bloquean la fila `pagos` del
  pedido (`PESSIMISTIC_WRITE`), exigen estado `AUTHORIZED`, llaman a Stripe con clave
  `capture-{orderId}` o `release-{orderId}` y registran la transacción. Un fallo se registra y no
  cambia el estado. Solo una de las dos prospera. No hay endpoint HTTP: la spec 007 las invocará desde
  su servicio; aquí se prueban desde pruebas de integración.
- **Razón**: FR-008/009 y SC-006; sin reembolso.

## 9. Importes: coherencia con 005

- **Decisión**: la confirmación reutiliza `CartService`/`CartPricing` (005) para recalcular. El cuerpo
  incluye `expectedTotal`, que **solo se compara** con el total recalculado; si difiere, 409
  `AMOUNTS_CHANGED` con el resumen vigente, sin pedido ni reserva. El monto autorizado es siempre el
  recalculado.
- **Razón**: FR-003, SC-003.

## 10. Frontend

- **Decisión**: añadir `@stripe/stripe-js` (10.3.0) y `@stripe/react-stripe-js` (7.1.0). La clave
  publicable se obtiene de `GET /api/customer/checkout` (vive solo en `backend/.env`; no se duplica
  en el frontend). Páginas `/confirmar-pedido` y `/pedido/:id`. Sin Playwright.
- **Razón**: el formulario seguro de Stripe mantiene la tarjeta fuera del sistema.

## 11. Configuración y arranque

- **Decisión**: `StripeProperties` lee `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` y
  `mikuna.payments.currency`. El arranque falla con mensaje claro si la clave secreta no empieza con
  `sk_test_` o la publicable no empieza con `pk_test_`. Las claves no se registran en logs ni
  respuestas. Las pruebas usan valores ficticios y un `StripeGateway` mock.
- **Consecuencia**: sin claves el backend no arranca; `backend/.env.example` ya las lista sin valor.
