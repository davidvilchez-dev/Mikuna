# Validación: Historiales y calificaciones (010)

Fecha: 2026-11-18. Entorno local (PostgreSQL local, `mikuna_test_db`; Stripe y Cloudinary simulados en las pruebas).

## Resultado de las comprobaciones

| Comprobación | Resultado |
| --- | --- |
| `cd backend && ./mvnw verify` | Compila y pasa: 642 pruebas, 0 fallos, 0 errores (51 son nuevas de este incremento). |
| `cd frontend && pnpm test` | 12 archivos, 97 pruebas, todas pasan (las nuevas están en `history-logic.test.ts`). |
| `cd frontend && pnpm exec tsc -b` y `pnpm build` | Sin errores. |
| `eslint` sobre `features/history`, `features/notifications`, `OrderPage.tsx` y `App.tsx` | Sin avisos. |
| Cobertura de líneas (JaCoCo, `verify`) | Paquete `history`: 149 de 150 líneas (99,3 %). Total del backend: 2 250 de 2 310 (97,4 %). Ramas del total: 681 de 742, sin umbral. Meta RNF-11 (≥ 90 %) cumplida para la lógica de negocio local. |
| SC-001: primera página con 1 000 pedidos | 0,020 s (prueba `HistoryPerformanceIntegrationTest`, tras una consulta de calentamiento). No acredita RNF-01. |

Única línea sin cubrir de `history`: el getter `Rating.getId()`, que ninguna regla de negocio usa.

## Trazabilidad

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| RF-30 (historiales) | Verificado en local | `CustomerHistoryIntegrationTest`, `RestaurantHistoryIntegrationTest`, `CourierHistoryIntegrationTest`, `HistoryPagingFilterIntegrationTest`, `HistorySnapshotIntegrationTest` |
| RF-31 (calificación única de 1 a 5) | Verificado en local | `RatingServiceTest`, `RatingIntegrationTest`, `RatingConcurrencyIntegrationTest` (2 y 5 solicitudes simultáneas: una `201`, el resto `409`, una sola fila) |
| RF-04 (pertenencia y estado de cuenta) | Verificado en local | 401/403 por rol y 401/403 con cuenta bloqueada en las tres suites de historial; aislamiento entre clientes, restaurantes y repartidores |
| RNF-07 (denegar accesos sin permiso) | Verificado en local para las rutas nuevas | Pruebas 401/403/404 de las cuatro rutas |
| FR-005 / SC-003 (datos del momento del pedido) | Verificado | `HistorySnapshotIntegrationTest`: producto renombrado, repreciado y retirado; el historial conserva los valores originales |
| FR-016 / FR-017 (visibilidad por rol) | Verificado | El restaurante no recibe dirección ni teléfono; el repartidor no recibe `payment`, `totals`, comisión, subtotal, precios ni teléfono |
| FR-002 / FR-003 (acceso de cuentas no habilitadas) | Verificado | Restaurante con establecimiento rechazado y repartidor con la disponibilidad apagada conservan su historial |

## Pendiente o aplazado

- **T039, recorrido manual del quickstart en navegador:** no se ejecutó. Los seis escenarios están cubiertos por las
  pruebas automatizadas, salvo la interfaz (SC-004: calificar en menos de 30 s), que queda para el responsable.
- RNF-01 (carga con 1 000 usuarios concurrentes), E2E automatizadas de navegador, HTTPS y CI/CD: aplazados por la
  constitución; no se declaran cumplidos.

## Adaptaciones respecto de tasks.md

- **T013:** el enlace «Historial» está en `NotificationsBell.tsx` (que `AuthLayout` ya renderiza), no directamente en
  `AuthLayout.tsx`; aparece solo en las cuentas que reciben avisos (cliente, restaurante, repartidor).
- **T021:** el control de calificación (`RatingControl.tsx`) está en `HistoryPage.tsx`. `OrderPage.tsx` enlaza al
  historial porque `OrderResponse` no incluye la calificación y no se modificó el módulo `order`.
- **Cambio fuera de `history`:** solo el índice `ix_pedidos_restaurante_fecha` en `Order.java` (T003), la excepción
  `RatingConflictException` con su manejador, y `delete from calificaciones` en `IntegrationTestSupport`.
