# Quickstart: validar el incremento 009

Prerrequisitos: PostgreSQL local con `mikuna_db`/`mikuna_test_db`, backend y frontend como en 008, y cuentas de
prueba de cliente, restaurante aprobado y repartidor. Detalle de endpoints en
[contracts/tracking-api.md](./contracts/tracking-api.md) y tablas en [data-model.md](./data-model.md).

## Pruebas automáticas

```text
cd backend && mvnw test          # unitarias + integración (incluye avisos, ubicación y seguimiento)
cd backend && mvnw verify        # reporte JaCoCo de lógica de negocio (≥ 90 %)
cd frontend && npm test          # Vitest: sondeo, formato de antigüedad, envío de ubicación
```

## Recorrido manual

1. **Avisos al crear y avanzar**: el cliente confirma un pedido → el restaurante ve 1 aviso no leído en la campana.
   El restaurante acepta, prepara y marca Listo → el cliente ve avisos de cada cambio con contador creciente. El estado del pedido debe verse en menos de 3 s (SC-001).
2. **Asignación**: con un repartidor disponible, se le asigna la entrega → cliente, restaurante y repartidor
   reciben el aviso «Asignado».
3. **Ubicación y seguimiento**: el repartidor abre `/repartidor/entregas` y permite la geolocalización. El cliente
   abre `/pedido/:id`: mientras esté Asignado ve las etapas y «la ubicación aún no está disponible» (no se muestra).
4. **En camino**: el repartidor registra la recogida → el cliente ve coordenadas y hora de actualización, que se
   renuevan en < 10 s sin recargar (cronometrar la aparición de una nueva posición, SC-002).
5. **Privacidad**: con el token de otro cliente, del restaurante y del administrador, `GET …/tracking` devuelve
   `404`/`403` y nunca coordenadas.
6. **Fin de la entrega**: el repartidor entrega → el cliente deja de ver la ubicación («Pedido entregado»); en la
   base queda una fila por recogida y una por entrega en `hitos_entrega` y ninguna en `ubicaciones_repartidor`.
7. **Leídos**: abrir un aviso lo marca leído y baja el contador; «Marcar todos» deja el contador en 0.
8. **Fallo de aviso** (prueba automática): forzar un error al guardar el aviso → el pedido igual cambia de estado.
