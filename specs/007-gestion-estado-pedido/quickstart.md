# Quickstart: validar la gestión de estado del pedido (007)

Prerrequisitos: los de 006 (PostgreSQL local, `backend/.env` con claves `sk_test_`/`pk_test_`), un restaurante
aprobado con productos, un cliente con carrito y dirección.

1. Arranque: backend (`mvnw spring-boot:run`) y frontend (`npm run dev`).
2. Como cliente, confirmar dos pedidos con la tarjeta `4242 4242 4242 4242` → ambos Pendientes.
3. Como restaurante, abrir `/mi-restaurante/pedidos`: aparecen los dos pendientes. Aceptar uno
   → `ACCEPTED`, pago `CAPTURED` (cobro realizado en el panel de Stripe en pruebas). Rechazar el otro →
   `REJECTED`, pago `RELEASED`.
4. Avanzar el aceptado: En preparación → Listo para recoger. Probar saltarse un paso (API) → 409.
5. Confirmar un tercer pedido y, como cliente, cancelarlo en `/pedido/:id` → `CANCELLED`, `RELEASED`.
   Intentar cancelar el aceptado → 409 sin reembolso.
6. Con otra cuenta de restaurante o cliente, repetir una acción sobre esos pedidos → 404.
7. Pruebas: `mvnw test` (carrera aceptar/cancelar, reserva vencida, registro de cambios); `npm test`.

Resultados esperados: ver [contracts](./contracts/order-status-api.md) y [data-model](./data-model.md);
sin llamadas a Stripe real en las pruebas automáticas.
