# Quickstart: vencimiento de pedidos pendientes

## Requisitos

Los de las specs 006–009: PostgreSQL local con `mikuna_db` y `mikuna_test_db`, `backend/.env` con las claves
de Stripe de prueba. Opcional: `MIKUNA_ORDER_RESPONSE_TIMEOUT=PT2M` en `backend/.env` para probar a mano sin
esperar 15 minutos.

## Pruebas automáticas

```bash
cd backend && ./mvnw test -Dtest='PendingOrderExpiry*,OrderStatusServiceTest,NotificationRecipientsTest'
cd backend && ./mvnw verify          # suite completa + JaCoCo
cd frontend && npm test -- orders-logic
```

Qué demuestran:

| Escenario | Prueba | Esperado |
| --- | --- | --- |
| US1 · tarjeta y efectivo vencen | integración del barrido | Cancelado, `SYSTEM`, motivo «sin respuesta del restaurante», pago `RELEASED`; efectivo sin llamar a la pasarela |
| US1 · ya decidido o cancelado | integración del barrido | sin cambios, sin segunda liberación |
| US1/FR-014 · aceptar o rechazar vencido | integración del controlador | 409 `ORDER_EXPIRED`; si no estaba barrido, queda Cancelado y liberado |
| US2 · carreras | concurrencia (vencer ‖ aceptar, vencer ‖ cancelar, dos barridos), ≥ 50 repeticiones | un único desenlace, una liberación o un cobro, un único cambio y un aviso por destinatario |
| US3 · tiempo restante | Vitest `orders-logic` + respuesta con `secondsToExpire` | cuenta sin negativos, «próximo a vencer» en el último 20 % |
| US4 · reinicio | integración: filas con `expires_at` nulo o pasado y barrido inicial | rellenado y cancelado |

## Prueba manual

1. Con `MIKUNA_ORDER_RESPONSE_TIMEOUT=PT2M`, arrancar backend y frontend, y crear un pedido con tarjeta.
2. En `/pedido/:id` el cliente ve «Se cancela en 1:59…»; en `/mi-restaurante/pedidos` el restaurante ve la
   cuenta y el pedido se destaca en los últimos 24 s.
3. No decidir. A los ~2 min (como mucho +15 s) ambos ven Cancelado y reciben el aviso; en Stripe (modo de
   pruebas) el PaymentIntent queda `canceled`.
4. Repetir aceptando a tiempo: el pedido no vence.
5. Detener el backend con un pedido Pendiente, esperar más del plazo y arrancar: a los pocos segundos queda
   Cancelado.
