# Quickstart: validar disponibilidad, asignación y entrega (008)

Prerrequisitos: los de 007 (PostgreSQL local, `backend/.env` con claves `sk_test_`/`pk_test_`, restaurante
aprobado con productos, cliente con carrito y dirección), más tres cuentas de repartidor (A, B, C) y la cuenta
administradora local.

1. Arranque: backend (`mvnw spring-boot:run`) y frontend (`npm run dev`).
2. Como repartidor A y B, abrir `/repartidor/entregas` y activar la disponibilidad; C la deja desactivada.
3. Como cliente, confirmar un pedido; como restaurante, aceptarlo, pasarlo a En preparación y Listo para recoger.
   → El pedido queda `ASSIGNED` a A o B (el que lleva más tiempo libre), nunca a C.
4. Como el repartidor asignado, rechazar la entrega → vuelve a `READY` y se reasigna al otro; el primero no la
   vuelve a recibir. Rechazarla también con el segundo → queda pendiente.
5. Como administrador, abrir `/admin/entregas`: aparece la pendiente sin candidatos para A y B; activar C y
   asignarla desde ahí (o activar la disponibilidad de C y comprobar que la recibe sola).
6. Como C: `pickup` → `PICKED_UP`; `complete` → `DELIVERED`. Probar saltar un paso por API → 409.
7. Con otro repartidor o rol, repetir una acción sobre esa entrega → 404 / 403; sin token → 401.
8. Consultar `cambios_estado_pedido` del pedido: cada cambio con fecha y responsable (`SYSTEM`, `COURIER`,
   `ADMIN`).
9. Inhabilitar la cuenta de un repartidor que tenga una entrega `ASSIGNED` (por la base local, ya que la gestión de
   usuarios es la spec 011): su acceso da 403 y la entrega aparece en `/admin/entregas` marcada. Como administrador,
   «Liberar» → vuelve a `READY` sin rechazo, con cambio registrado como `ADMIN`, y se reasigna a otro candidato.
   Liberar una entrega cuyo repartidor sigue habilitado → 409.
10. Pruebas: `mvnw test` (carreras: N asignaciones simultáneas, automática vs manual, rechazo vs reasignación,
   dos pedidos para un repartidor; elección del candidato; registro de cambios); `npm test`.

Resultados esperados: ver [contracts](./contracts/delivery-api.md) y [data-model](./data-model.md); las pruebas
de carrera comprueban en base de datos que cada entrega termina con exactamente un `courier_id`.
