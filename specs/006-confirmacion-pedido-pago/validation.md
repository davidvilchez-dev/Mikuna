# Validación del incremento 006

Evidencia y estado de los requisitos (constitución VI). Fecha: 2026-11-17.

## Trazabilidad

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| RF-04 (autorización) | Verificado para estas rutas | `OrderConfirmationIntegrationTest` y `OrderQueryIntegrationTest`: 401 sin token, 403 con otro rol y con cuenta `DISABLED`, 404 en pedido ajeno. |
| RF-14 (pedido pendiente validado) | Verificado | `OrderConfirmationIntegrationTest`, `OrderValidationIntegrationTest` (restaurante, productos, cantidades, dirección, importes), `OrderIdempotencyConcurrencyIntegrationTest` (un solo pedido ante reintentos y 10 envíos simultáneos). |
| RF-15 (método de pago) | Verificado | Solo `CARD` se acepta; cualquier otro valor o un `paymentMethodId` que no sea `pm_...` responde 400. `GET /api/customer/checkout` lista los métodos. |
| RF-16 (reservar, cobrar, liberar) | Verificado con Stripe **simulado** | `PaymentLifecycleIntegrationTest`, `PaymentServiceTest`, `StripeGatewayTest`. Captura y liberación excluyentes, una sola vez, fallo reintentable, sin reembolso. **Pendiente**: recorrido manual con el sandbox de Stripe (requiere las claves en `backend/.env`). |
| RF-17 (registro del pago) | Verificado | `PaymentTransactionIntegrationTest` / `OrderQueryIntegrationTest`: autorización, cobro, liberación y fallo quedan registrados y asociados al pedido; sin datos de tarjeta ni claves. |
| RNF-07 | Verificado para estas rutas | Pruebas 401/403/404 anteriores. |
| RNF-11 | Ver informe JaCoCo de `./mvnw verify` | Frontend: `pnpm test:coverage` (≥ 90 % de líneas en las reglas de checkout). |
| RNF-06 | **Pendiente** | HTTP en localhost; la evidencia de HTTPS corresponde al despliegue. |
| RNF-08 | **Pendiente (parcial)** | Los datos de tarjeta nunca pasan por el sistema (los captura el formulario de Stripe) y no se guardan; la evidencia completa corresponde al despliegue. |

## Pendientes declarados (sin declarar pagos completos)

- Recorrido manual con tarjetas de prueba de Stripe (ver [quickstart](./quickstart.md)): requiere `STRIPE_SECRET_KEY` y `STRIPE_PUBLISHABLE_KEY` de prueba en `backend/.env`. Confirmar también que la cuenta admite la moneda `pen`.
- Webhooks, conciliación y recuperación ante una caída del servidor entre autorizar y guardar (riesgo residual: una reserva sin pedido).
- Vencimiento de las reservas en Stripe (spec 007).
- La falla en el commit de la transacción se aproxima con fallos al vaciar el buffer (`saveAndFlush`) y con la inyección de fallos tras autorizar; no se simula un fallo del propio commit.
