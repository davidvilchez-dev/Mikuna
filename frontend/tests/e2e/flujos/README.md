# Pruebas E2E de flujos

Pruebas de navegador (Playwright) de flujos completos contra el **backend real** y **PostgreSQL**. Son una suite
transversal: cada prueba indica en su título qué specs recorre, por ejemplo `[004·005·006·007·009]`.

| Archivo | Flujo | Specs |
|---------|-------|-------|
| `registro.spec.ts` | Registro de cliente, inicio de sesión, rutas protegidas y contraseña incorrecta | 002, 004 |
| `pedido.spec.ts` | El cliente pide en efectivo (catálogo → carrito → confirmación) y el restaurante lo acepta, prepara y deja listo; el cliente ve cada cambio | 004, 005, 006, 007, 009 |
| `administracion.spec.ts` | El admin aprueba una solicitud (aparece en la búsqueda pública, con la caché de la 015) y cancela un pedido aceptado (el cliente ve el motivo) | 003, 011, 014, 015 |

Lo que la prueba no verifica en pantalla se prepara por la API real (`support.ts`): crear el cliente y su dirección
o un pedido de partida. El alta de direcciones exige marcar un punto en un mapa y queda fuera.

## Correr en local

Una sola vez, crear la base dedicada (nunca la de desarrollo):

```powershell
& 'C:\Program Files\PostgreSQL\16\bin\createdb.exe' -U postgres mikuna_e2e_db
```

Después, desde `frontend/`:

```powershell
pnpm test:e2e:flujos
```

- Playwright arranca el backend (`backend/.env` aporta las claves) contra `mikuna_e2e_db` en el puerto 8080 y Vite
  en el 5174. **El puerto 8080 debe estar libre**: nunca se reutiliza un backend ya levantado, porque podría estar
  apuntando a la base de desarrollo.
- El proyecto `semilla` vacía `mikuna_e2e_db` y carga `seed-e2e.sql`: un administrador (`admin@e2e.test`) y un
  restaurante aprobado con su carta (`restaurante@e2e.test`, «Picantería E2E»). La contraseña de ambos es
  `MikunaE2e2026!`.
- `psql` se busca en `PSQL`, luego en PostgreSQL 16 de Windows y por último en el `PATH`.
- Informe: `playwright-report-flujos/` (`pnpm exec playwright show-report playwright-report-flujos`).

## CI

`.github/workflows/e2e.yml` corre la misma suite con un servicio PostgreSQL y claves de prueba ficticias: los
flujos pagan en efectivo y no suben imágenes, así que no llaman a Stripe, Cloudinary ni Gemini.

La suite de la portada (`pnpm test:e2e`) sigue sin backend y no incluye estos flujos.
