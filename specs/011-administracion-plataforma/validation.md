# Validación: Administración de la plataforma (011)

Fecha: 2026-11-18. Entorno local (PostgreSQL local, `mikuna_test_db`; Stripe y Cloudinary simulados en las pruebas).

## Resultado de las comprobaciones

| Comprobación | Resultado |
| --- | --- |
| `cd backend && ./mvnw verify` (última corrida completa, con `-Dmaven.test.failure.ignore=true`) | Compila. 705 pruebas, 704 pasan y 1 falla de forma intermitente en un test ajeno a este incremento (ver «Fallos intermitentes»). Las 63 pruebas nuevas de este incremento pasan en todas las corridas. |
| `cd frontend && pnpm test` | 13 archivos, 110 pruebas, todas pasan (las nuevas están en `admin-logic.test.ts`). |
| `cd frontend && pnpm exec tsc -b` y `pnpm exec eslint src` | Sin errores ni avisos. |
| Cobertura de líneas (JaCoCo, `verify`) | Paquete `admin`: 256 de 257 líneas (99,6 %). Total del backend: 2 497 de 2 558 (97,6 %). Ramas del total: 785 de 856, sin umbral. Meta RNF-11 (≥ 90 %) superada. |
| Rendimiento (`AdminPerformanceIntegrationTest`) | Con 1.000 cuentas y 1.000 pedidos, la primera página de `/users`, `/orders` y `/deliveries` responde en 0,012 s, 0,018 s y 0,014 s (tras una consulta de calentamiento). No acredita RNF-01. |

Única línea sin cubrir de `admin`: el getter `AdminAccountAction.getId()`, que ninguna regla de negocio usa.

## Trazabilidad

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| RF-32 (gestionar usuarios, restaurantes y repartidores) | Verificado en local | `AdminAccountListIntegrationTest`, `AdminAccountStatusIntegrationTest`, `AdminAccountServiceTest`, `AdminAccountQueryServiceTest`: listados, búsqueda, filtros, detalle, activar y desactivar con su efecto real |
| RF-33 (consultar pedidos y entregas por estado) | Verificado en local | `AdminOrderListIntegrationTest`, `AdminOrderDetailIntegrationTest`, `AdminOrderServiceTest`: filtro por estado, orden descendente, paginación, detalle con pago y cambios de estado |
| RF-04 / RNF-07 (acceso solo del administrador) | Verificado en local para las rutas nuevas | `AdminAccessControlIntegrationTest`: los 16 endpoints responden 403 a cliente, restaurante y repartidor, 401 sin sesión y 403 con el administrador deshabilitado, sin cambios en datos |
| FR-005 (cuenta desactivada no inicia sesión) | Verificado | Usuario bloqueado: su token ya emitido da 403 en la siguiente solicitud y el inicio de sesión falla; al restablecer vuelve a entrar |
| FR-006 / FR-007 (restaurante y repartidor desactivados) | Verificado | Restaurante: sale de `/api/public/restaurants`, no admite pedidos nuevos, su titular conserva el acceso y un pedido en curso se acepta, prepara y entrega. Repartidor: deja de ser candidato (automático y manual), conserva el acceso y su entrega en curso se completa; al activarlo vuelve a poder asignarse |
| FR-008 / FR-018 (sin eliminar, solo lectura) | Verificado | `DELETE` sobre cuentas y `POST/PUT/PATCH/DELETE` sobre pedidos y entregas responden 405 sin cambios |
| FR-009 / FR-010 (administradores protegidos, idempotencia) | Verificado | 409 `ADMIN_ACCOUNT_PROTECTED` (propia y otra); repetir un cambio responde 200 sin registro nuevo; cuatro desactivaciones simultáneas dejan un solo cambio y un solo registro |
| FR-017 (registro con fecha y responsable) | Verificado | `AdminAuditIntegrationTest`: cada cambio efectivo aparece en el detalle con acción, ámbito, fecha y administrador, más reciente primero |
| FR-019 (sin credenciales) | Verificado | Ninguna respuesta de listado ni detalle contiene `password`, `hash` ni `pbkdf2` |
| SC-001 (encontrar una cuenta en menos de 1 minuto) | No medido | Es una métrica de uso; la búsqueda responde en milisegundos, pero el recorrido de la interfaz queda para el responsable |

## Fallos intermitentes en pruebas de otros incrementos

En cuatro corridas completas del backend fallaron, cada vez, de 1 a 4 pruebas distintas y **ajenas a este incremento**:
`NotificationIntegrationTest` (orden de avisos), `DeliveryAssignmentConcurrencyIntegrationTest` y
`CartTotalsIntegrationTest.unaLineaQueDejaDeSerElegibleNoSumaYSeMarca` (orden de líneas del carrito). Todas
comparten el mismo patrón: ordenan por una marca de tiempo y, si dos filas coinciden, el desempate es un UUID
aleatorio. Pasan al ejecutarlas solas o en parejas (8 corridas de `NotificationIntegrationTest` sin fallo, con y sin
mis cambios) y no tocan código modificado aquí. No se corrigieron por estar fuera del alcance; conviene
estabilizarlas con un desempate determinista en un cambio aparte.

## Pendiente o aplazado

- **T039, recorrido manual del quickstart en navegador:** no se ejecutó. Los pasos 3 a 10 están cubiertos por las
  pruebas automatizadas; queda por probar a mano la interfaz (`/admin/cuentas` y `/admin/pedidos`).
- RNF-01 (carga con 1.000 usuarios concurrentes), E2E automatizadas de navegador, HTTPS y CI/CD: aplazados por la
  constitución; no se declaran cumplidos. RNF-06 y RNF-08 tampoco.

## Adaptaciones respecto de tasks.md

- **T016:** las actualizaciones condicionales `updateStatus` y `updateOperation` viven en
  `AdminAccountQueryRepository` (módulo `admin`), no en `AccountRepository`, para que `account` no conozca la
  administración.
- **T015:** no hizo falta ajustar los auxiliares de prueba; solo se añadió `delete from acciones_admin_cuenta` a
  `IntegrationTestSupport`.
- **Respuestas de listado de restaurantes:** `id` es el del restaurante y `accountId` el de su titular, para que el
  enlace «Ver cuenta del titular» abra la cuenta en la pestaña Usuarios.
- **Cambios fuera de `admin`:** `Account` (columna `operation_enabled`, `canOperate()`), `RestaurantRepository`
  (`findVisible*`), `RestaurantService` y `RestaurantReviewService` (`canOperate()`), `CourierAvailabilityRepository`
  (`CANDIDATE`), el índice `ix_pedidos_estado_fecha` en `Order`, las excepciones `AdminAccountProtectedException` y
  `AccountNotFoundException` con su manejador, `App.tsx` y `AccountPage.tsx` (enlaces para el administrador).
