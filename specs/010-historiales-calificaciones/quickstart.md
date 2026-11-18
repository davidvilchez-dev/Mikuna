# Quickstart: validar Historiales y calificaciones

Guía de validación local. Contratos en [contracts/history-api.md](./contracts/history-api.md) y modelo en
[data-model.md](./data-model.md).

## Requisitos previos

- PostgreSQL local con `mikuna_db` (y `mikuna_test_db` para las pruebas) y `backend/.env` como en 006.
- Cuentas de prueba: dos clientes, dos restaurantes aprobados, dos repartidores habilitados.
- Backend: `./mvnw spring-boot:run` en `backend/`. Frontend: `pnpm dev` en `frontend/`.

## Pruebas automáticas

```text
cd backend && ./mvnw verify          # unitarias + integración + JaCoCo (≥ 90 % de líneas de negocio)
cd frontend && pnpm test             # lógica de filtros, paginación y calificación
```

## Escenarios manuales

1. **Historial del cliente (US1, FR-001, FR-004).** Con el cliente A, confirmar tres pedidos y llevarlos a
   Entregado, Rechazado y Cancelado. En `/historial` aparecen los tres con estado, productos, importes y pago; el
   cliente B no ve ninguno.
2. **Calificar (US2, FR-008…FR-011).** En el pedido Entregado, calificar con 4: queda registrada. Un segundo
   intento y los pedidos no entregados o ajenos devuelven error; `0`, `6` y `3.5` se rechazan.
3. **Restaurante y repartidor (US3, US4, FR-016).** El restaurante del pedido ve su historial con la
   calificación y solo el nombre del cliente; el repartidor que lo entregó ve su entrega (también las en curso)
   con productos, dirección de entrega y costo de envío, sin comisión ni importes; el otro restaurante y el otro
   repartidor no ven nada. Inhabilitar al restaurante o al repartidor para pedidos: conservan su historial.
4. **Paginación y filtros (US5).** Con más de 20 pedidos, recorrer las páginas (sin repetir ni omitir), filtrar
   por estado, por rango de fechas y por ambos; un rango invertido muestra un mensaje claro.
5. **Datos congelados (US6).** Cambiar el nombre y el precio de un producto ya pedido y luego retirarlo: el
   historial conserva los valores originales.
6. **Calificación simultánea (SC-005).** Enviar dos solicitudes de calificación a la vez: una `201` y otra
   `409`, con una sola fila en `calificaciones`.

## Resultado esperado

Todos los escenarios cumplen su criterio; la evidencia se registra en `validation.md` al implementar.
RNF-01 (carga) permanece aplazado.
