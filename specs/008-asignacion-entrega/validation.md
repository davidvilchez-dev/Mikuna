# Validación: disponibilidad, asignación y entrega (008)

**Fecha**: 2026-11-17. Distingue lo verificado automáticamente de lo pendiente (constitución VI).

## Verificado

| Requisito | Evidencia |
| --- | --- |
| RF-21 disponibilidad | `AutoAssignmentIntegrationTest` (activar/desactivar, 400 sin cuerpo válido, 401/403), `CourierServicesTest` |
| RF-22 asignación automática | `AutoAssignmentIntegrationTest` (solo disponible y habilitado, el que lleva más tiempo libre, pendiente y asignación al activarse, una entrega activa por repartidor, registro `SYSTEM`), `DeliveryAssignmentServiceTest` |
| RF-23 reasignación | `DeliveryRejectionIntegrationTest` (a otro candidato en < 5 s, nunca al que rechazó, pendiente si todos rechazan, el pedido nunca pasa a REJECTED/CANCELLED) |
| RF-24 asignación manual | `AdminDeliveryIntegrationTest` (candidatos, responsable `ADMIN`, 409 para no candidatos y ya asignadas, 400/404, liberación FR-010a), `DeliveryAdminServiceTest` |
| RF-25 aceptar/rechazar, recoger, entregar | `CourierDeliveryIntegrationTest` (secuencia, saltos y reenvíos → 409 sin duplicar registros, entrega ajena → 404, mensajes claros), `CourierServicesTest` |
| RNF-05 una sola asignación | `DeliveryAssignmentConcurrencyIntegrationTest`: 6 escenarios × 30 repeticiones sobre PostgreSQL (asignaciones simultáneas, automática vs manual, rechazo vs reasignación, dos pedidos y un repartidor, dos repartidores que se activan a la vez, reenvío de `pickup`); se comprueba `entregas.courier_id`, los cambios registrados y que ningún repartidor tenga dos entregas activas |
| RNF-07 (alcance del incremento) | 401 sin token y 403 con otro rol o cuenta deshabilitada en `/api/courier/**` y `/api/admin/deliveries/**` |
| FR-014 registro de cambios | `cambios_estado_pedido` con `SYSTEM`, `COURIER` y `ADMIN`, comprobado en las pruebas de integración |
| Cancelación con estados nuevos (T010a) | `CourierDeliveryIntegrationTest.elClienteNoCancelaUnPedidoAsignadoRecogidoOEntregado...` |
| RNF-11 cobertura | Backend (`mvnw verify`): 522 pruebas, 0 fallos; líneas 97,29 % (1833/1884); `delivery.service` 100 %, `delivery.controller` 100 %. Frontend: 68 pruebas; líneas 98,7 % (umbral 90 %) |

Compilación del backend y `tsc -b` y ESLint del frontend sin errores.

## Validación manual (T046)

- **API contra un backend real** (asistente, `mikuna_test_db`, puerto 8089): arranque y creación de las tres tablas,
  registro de repartidores, 401/403 por ruta, disponibilidad (consultar, activar, 400 con cuerpo vacío), 404 en
  entregas inexistentes y 400 en la asignación sin `courierId`. No se recorrió la asignación con pedidos reales por
  requerir restaurante aprobado, Cloudinary y Stripe; esa parte la cubren las pruebas de integración.
- **Páginas `/repartidor/entregas` y `/admin/entregas`**: probadas a mano por el responsable del proyecto, que confirmó
  el resultado el 2026-11-17 (declaración del usuario; sin prueba de navegador automatizada, aplazada por la
  constitución).

## Pendiente (no declarado como cumplido)
- Avisos y notificaciones (spec 009), ubicación del repartidor (RF-26/RF-28), historial (spec 010), vistas globales de
  administración y gestión de usuarios (spec 011/RF-32).
- **Límite conocido**: si un repartidor se inhabilita con la entrega ya Recogida, el administrador no puede liberarla en
  este incremento (solo las Asignadas); queda para la gestión de usuarios (RF-32).
- RNF-01/RNF-10 (carga y monitoreo), RNF-06/RNF-08 (despliegue) y CI/CD siguen aplazados.
