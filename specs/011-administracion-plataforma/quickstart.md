# Quickstart: validar la administración de la plataforma

**Prerrequisitos**: PostgreSQL local con `mikuna_db` y `mikuna_test_db` creadas, `backend/.env` y el
administrador local de la spec 002 (`AdminAccountInitializer`). Rutas en
[contracts/admin-api.md](./contracts/admin-api.md); entidades en [data-model.md](./data-model.md).

1. **Automáticas** (desde `backend/`): `./mvnw test` y el reporte JaCoCo; desde `frontend/`: `npm test`.
   Esperado: todo en verde y cobertura de líneas de negocio ≥ 90 %.
2. **Arranque local**: `./mvnw spring-boot:run` y `npm run dev`. Crear con la interfaz un cliente, un restaurante
   aprobado y un repartidor.
3. **Acceso exclusivo**: con el token de cada rol no administrador, y sin token, llamar a un endpoint de cada
   grupo. Esperado: 403 y 401, sin cambios.
4. **Usuarios**: `GET /api/admin/users?role=CUSTOMER&q=<nombre>` y abrir el detalle. Esperado: la cuenta, su estado
   y su registro de acciones vacío.
5. **Desactivar acceso**: `POST /api/admin/users/{id}/disable`; con el token ya emitido del cliente llamar a
   `GET /api/notifications`. Esperado: 403; el inicio de sesión nuevo también falla. `enable` lo restablece.
6. **Restaurante**: `POST /api/admin/restaurants/{id}/deactivate`. Esperado: desaparece de
   `/api/public/restaurants`, confirmar un pedido nuevo falla, un pedido en curso sigue avanzando y el titular aún
   inicia sesión y ve su historial. `activate` lo devuelve.
7. **Repartidor**: `POST /api/admin/couriers/{id}/deactivate`. Esperado: ya no aparece entre los candidatos de
   `GET /api/admin/deliveries/pending`; su entrega en curso se completa; conserva el acceso.
8. **Idempotencia y registro**: repetir un cambio → 200 sin fila nueva; el detalle muestra solo las acciones
   efectivas con fecha y administrador.
9. **Protección**: intentar `disable` sobre la cuenta del administrador → 409.
10. **Pedidos y entregas**: `GET /api/admin/orders?status=DELIVERED&size=2` y
    `GET /api/admin/deliveries?status=ASSIGNED`. Esperado: solo ese estado, del más reciente al más antiguo,
    paginado; estado inválido → 400; el detalle muestra pago y cambios de estado.
