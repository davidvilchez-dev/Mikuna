# Quickstart: validar el incremento 014

## Requisitos

- PostgreSQL local con `mikuna_db` y `mikuna_test_db`; `backend/.env` con claves de Stripe de prueba.
- Backend: `cd backend && ./mvnw spring-boot:run`. Frontend: `cd frontend && pnpm dev`.

## Pruebas automáticas

```bash
cd backend && ./mvnw test          # incluye las integraciones del 014 (cancelación, reembolso, efectivo, carreras)
cd frontend && pnpm test && pnpm lint && pnpm exec tsc -b
```

## Recorrido manual

1. **Tarjeta (US1, US5)**: como cliente, paga un pedido con la tarjeta de prueba `4242 4242 4242 4242`. Como
   restaurante, acéptalo. Como admin, en *Pedidos* abre el detalle, pulsa *Cancelar pedido*, escribe un
   motivo y confirma. Esperado: estado Cancelado, pago Reembolsado y transacción de reembolso en el detalle;
   en el panel de Stripe (modo prueba) el pago figura reembolsado; el cliente ve "Cancelado por la
   plataforma", el motivo y "Reembolsado", y lo mismo en su historial.
2. **Sin motivo (US1)**: intenta confirmar con el motivo vacío. Esperado: el diálogo no lo envía.
3. **Estado no cancelable (US1)**: un pedido Entregado o Pendiente no muestra la acción.
4. **Repartidor (US4)**: con un pedido Asignado, cancélalo. Esperado: desaparece de las entregas activas
   del repartidor, que recibe el aviso y vuelve a poder recibir entregas.
5. **Efectivo (US3)**: haz un pedido en efectivo, acéptalo (pago: "pagarás al recibirlo") y llévalo hasta
   Entregado (pago: "Pagado en efectivo"). Otro pedido en efectivo cancelado por el admin queda "Sin cobro".
6. **Fallo de la pasarela (US2)**: cubierto por pruebas de integración con la pasarela simulada; a mano,
   detén la red y cancela: el admin ve "No se pudo reembolsar…" y el pedido no cambia.
