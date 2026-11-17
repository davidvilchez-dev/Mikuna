# Quickstart: validar el incremento 003

## Requisitos

- Los de [002](../002-registro-acceso/quickstart.md): PostgreSQL local con `mikuna_db` y
  `mikuna_test_db`, `backend/.env` completo y una cuenta administradora creada con el
  procedimiento local de 002.
- Backend en `http://localhost:8080` y frontend en `http://localhost:5173`.

## Pruebas automáticas

```powershell
cd backend ; ./mvnw verify               # unitarias + integración + JaCoCo (≥ 90 % de líneas de negocio)
cd ../frontend ; pnpm test:coverage     # Vitest (validación de datos y horario)
pnpm build ; pnpm lint
```

Deben cubrir los casos de [restaurant-api](./contracts/restaurant-api.md): 401/403 por rol y
estado, 409 por doble solicitud y por doble resolución concurrente, y `acceptingOrders` con reloj
fijo.

## Recorrido manual (resultado esperado entre paréntesis)

1. Registrar una cuenta «Restaurante» (002) e iniciar sesión (`/mi-cuenta` ofrece «Mi restaurante»).
2. En `/mi-restaurante` enviar la solicitud con datos válidos (estado **Pendiente**, sin pedidos).
   Reiniciar el backend y recargar (sigue pendiente). Reintentar el envío (error 409 controlado).
3. Con la cuenta administradora abrir `/admin/solicitudes` (aparece la solicitud, la más antigua primero).
4. Rechazar sin motivo (se exige motivo); rechazar con motivo (el titular ve **Rechazado** y el
   motivo); corregir y reenviar (vuelve a **Pendiente**).
5. Aprobar (el titular ve **Aprobado**, no disponible y sin pedidos).
6. Definir el horario semanal (un cierre menor o igual a la apertura se rechaza) y activar la
   disponibilidad dentro del horario (`acceptingOrders = true`); pausar (`false`).
7. Con una cuenta cliente, `GET /api/admin/restaurants` (403); sin token (401).

## Fuera de esta validación

E2E automatizadas (Playwright), carga, HTTPS y CI/CD siguen aplazados y no se declaran cumplidos.
