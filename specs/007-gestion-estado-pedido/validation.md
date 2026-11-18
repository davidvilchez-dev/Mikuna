# Validación: gestión de estado del pedido (007)

Fecha: 2026-11-17. Estado: implementación completa y verificada con Stripe **simulado**; recorrido manual con el
sandbox de Stripe **pendiente**.

## Trazabilidad

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| RF-18 (aceptar/rechazar) | Verificado (pasarela simulada) | `RestaurantOrderDecisionIntegrationTest`, `OrderStatusServiceTest` |
| RF-19 (preparación hasta Listo) | Verificado | `OrderProgressIntegrationTest`, `OrderTransitionTest` |
| RF-20 (cancelación del cliente) | Verificado | `OrderCancellationIntegrationTest` |
| RF-04 (autorización) | Verificado | 401/403/404 por rol y pertenencia en las tres pruebas anteriores |
| RF-16/RF-17 (cobrar y liberar al decidir) | Verificado (pasarela simulada) | pago `CAPTURED`/`RELEASED` coherente con el estado; `PaymentServiceCaptureExpiredTest`, `StripeGatewayTest` |
| Carrera aceptar/cancelar (constitución IV) | Verificado | `OrderDecisionConcurrencyIntegrationTest` (30 repeticiones; un solo cobro o liberación) |
| FR-012 (registro de cambios) | Verificado | `OrderStatusChangeIntegrationTest` |
| RNF-07 | Verificado en las rutas nuevas | pruebas 401/403/404 |
| RNF-11 | Backend 97,2 % de líneas (`./mvnw verify`, JaCoCo, 1583 líneas medidas); frontend 98,57 % (`pnpm test:coverage`) | informes locales |

## Comprobaciones ejecutadas

- `./mvnw verify`: 464 pruebas, 0 fallos. `pnpm run build`, `eslint` y `pnpm test:coverage`: sin errores (59 pruebas).

## Cambios respecto al plan

- Las pruebas unitarias de T012, T018 y T023 viven en `OrderStatusServiceTest`; no se creó `orders.css`.
- Un restaurante sin registro o no aprobado recibe 403 `RESTAURANT_NOT_ENABLED` (también en el listado).
- `StripeGateway.cancel` da por hecho un intento ya cancelado (reserva vencida), para que rechazar o cancelar un
  pedido con la reserva vencida no quede bloqueado en 503. Añadido a la pasarela, con prueba.

## Pendiente (no declarado como cumplido)

- Recorrido manual de [quickstart.md](./quickstart.md) con el sandbox de Stripe (requiere claves en `backend/.env`).
- Webhooks y conciliación (aplazados): una caída entre el cobro en Stripe y la confirmación de la transacción
  puede dejar un pedido Pendiente ya cobrado en la pasarela.
- No hay barrido de reservas vencidas; solo se detectan al aceptar, rechazar o cancelar.
- RNF-06 y RNF-08: evidencia de despliegue pendiente.
