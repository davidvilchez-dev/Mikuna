# Quickstart: validar el incremento 005

## Requisitos

- Los de [004](../004-catalogo-consulta/quickstart.md): PostgreSQL local con `mikuna_db` y
  `mikuna_test_db`, `backend/.env` completo, una cuenta de restaurante aprobada, **abierta ahora**
  (disponible y dentro de horario) con al menos dos productos disponibles; un segundo restaurante
  aprobado con un producto; y una cuenta de cliente.
- **Valores de negocio** (los indica el usuario; no hay valores por defecto) en `backend/.env`:
  `MIKUNA_DELIVERY_DISTRICTS` (lista separada por comas), `MIKUNA_DELIVERY_FEE` (soles) y
  `MIKUNA_COMMISSION_RATE` (porcentaje). `backend/.env.example` los lista sin valor. Sin ellos, el
  backend no arranca.
- Backend en `http://localhost:8080` y frontend en `http://localhost:5173`.

## Pruebas automáticas

```powershell
cd backend ; ./mvnw verify               # unitarias + integración + JaCoCo (≥ 90 % de líneas de negocio)
cd ../frontend ; pnpm test:coverage     # Vitest (validación de dirección y cantidad)
pnpm build ; pnpm lint
```

Deben cubrir los casos de [cart-api](./contracts/cart-api.md): 401/403/404 por rol, estado y
pertenencia; un solo restaurante; productos no disponibles, retirados o de restaurantes no
elegibles; cantidades inválidas y límites; zona de reparto; importes y redondeo; recálculo tras cada
cambio; y altas simultáneas en un mismo carrito sin perder ni duplicar líneas. Las pruebas usan los
valores de `mikuna.delivery.*` de `src/test/resources/application.properties`.

## Recorrido manual (resultado esperado entre paréntesis)

1. Con la cuenta cliente abrir `/mis-direcciones` y registrar una dirección con un distrito
   cubierto (se guarda) y otra con un distrito no cubierto (se rechaza con un mensaje claro).
2. Abrir el catálogo de un restaurante abierto y agregar dos productos (el carrito los muestra con
   precio, cantidad y subtotal por línea). Reiniciar el backend y recargar (se conserva).
3. Intentar agregar un producto del segundo restaurante (aviso de carrito de un solo restaurante y
   opción de vaciarlo); vaciar y reintentar (se acepta).
4. En `/carrito`, cambiar una cantidad a `0`, `-1` y `21` (se rechazan sin cambios) y a `3` (se
   actualiza); eliminar una línea (desaparece).
5. Sin dirección seleccionada: solo se ve el subtotal y un aviso de que falta la dirección.
   Seleccionar una dirección (aparecen envío, comisión y total = subtotal + envío + comisión).
   Cambiar cantidades y de dirección (los importes se recalculan cada vez).
6. Con el restaurante: marcar un producto del carrito como no disponible o pausar el restaurante
   (la línea del cliente sale como no disponible y no suma). Cambiar el precio (el carrito muestra el
   nuevo).
7. Eliminar la dirección seleccionada (el carrito queda sin dirección).
8. Con un segundo cliente, pedir el carrito, las direcciones o las líneas del primero por id (404);
   con la cuenta de restaurante, `GET /api/customer/cart` (403); sin token (401).

## Fuera de esta validación

Confirmación del pedido y pago (spec 006), historial (spec 010), E2E automatizadas (Playwright),
carga (RNF-01), caché (RNF-03), HTTPS y CI/CD siguen aplazados y no se declaran cumplidos.
