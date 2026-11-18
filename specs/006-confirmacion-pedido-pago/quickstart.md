# Quickstart: validar el incremento 006

## Requisitos

- Los de [005](../005-carrito-direcciones-importes/quickstart.md): PostgreSQL local con `mikuna_db` y
  `mikuna_test_db`, `backend/.env` completo (incluidas las variables `MIKUNA_DELIVERY_*`), un
  restaurante aprobado y **abierto ahora** con productos disponibles, y una cuenta de cliente con al
  menos una dirección dentro de la zona.
- **Stripe en modo de pruebas** (constitución 3.1.0): en `backend/.env`, `STRIPE_SECRET_KEY`
  (`sk_test_...`) y `STRIPE_PUBLISHABLE_KEY` (`pk_test_...`) de tu cuenta sandbox. Sin ellas, o con
  claves que no sean de prueba, el backend no arranca. Opcional: `MIKUNA_PAYMENTS_CURRENCY` (por
  defecto `pen`; confirma que tu cuenta de pruebas la admite).
- Backend en `http://localhost:8080` y frontend en `http://localhost:5173`.

## Pruebas automáticas

```powershell
cd backend ; ./mvnw verify               # unitarias + integración + JaCoCo (≥ 90 % de líneas de negocio)
cd ../frontend ; pnpm install ; pnpm test:coverage
pnpm build ; pnpm lint
```

Las pruebas **no llaman a Stripe real**: sustituyen `StripeGateway` por un mock. Deben cubrir los casos
de [checkout-api](./contracts/checkout-api.md): 401/403/404; confirmación correcta (pedido `PENDING`,
reserva, carrito vacío, líneas y precios fijados); cada validación (restaurante, productos,
cantidades, dirección) sin pedido ni reserva; `AMOUNTS_CHANGED`; autorización rechazada, 3DS y pasarela
caída sin pedido y con carrito intacto; reintento y confirmaciones simultáneas (≥ 10) con un solo
pedido y una sola reserva; compensación si falla el guardado tras autorizar; captura y liberación
(una sola vez, nunca ambas, fallo reintentable); transacciones registradas y sin datos de tarjeta.

## Recorrido manual con el sandbox de Stripe (opcional)

Tarjetas de prueba de Stripe: `4242 4242 4242 4242` (aprueba), `4000 0000 0000 0002` (rechazada),
`4000 0025 0000 3155` (exige 3DS: se trata como fallo). Cualquier fecha futura, CVC y código postal.

1. Con la cuenta cliente, armar un carrito, seleccionar dirección y pulsar «Continuar» en `/carrito`
   (se abre `/confirmar-pedido` con el resumen y el formulario de tarjeta de Stripe).
2. Pagar con `4242...` (aparece el pedido en estado Pendiente con pago «Reservado»; el carrito queda
   vacío; en el panel de Stripe en modo de pruebas el pago figura como «sin capturar»).
3. Repetir con `4000 0000 0000 0002` (mensaje claro; sin pedido; carrito intacto) y con la tarjeta
   3DS (mismo tratamiento).
4. Pulsar dos veces «Confirmar» (un único pedido y una única reserva).
5. Antes de confirmar, pausar un producto o cambiar su precio desde la cuenta del restaurante (se
   rechaza o se pide confirmar de nuevo con el resumen actualizado).
6. Reiniciar el backend y abrir `/pedido/:id` (el pedido y el estado del pago se conservan); confirmar un
   segundo pedido del mismo cliente (es independiente, con su propia reserva).
7. Capturar y liberar solo se verifican en las pruebas de integración; la spec 007 las conectará a
   las decisiones del restaurante.

## Evidencia y pendientes

- RF-14 a RF-17: cumplimiento verificable con las pruebas anteriores; RNF-06 y RNF-08 **no** se
  declaran cumplidos (HTTP local; evidencia de despliegue pendiente).
- Aplazado: webhooks, conciliación y recuperación ante caída entre autorizar y guardar, y el
  vencimiento de reservas de Stripe (spec 007).
