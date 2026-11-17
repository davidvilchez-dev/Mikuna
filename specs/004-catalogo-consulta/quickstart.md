# Quickstart: validar el incremento 004

## Requisitos

- Los de [003](../003-registro-restaurante/quickstart.md): PostgreSQL local con `mikuna_db` y
  `mikuna_test_db`, `backend/.env` completo, una cuenta administradora y una cuenta de restaurante
  aprobada con horario y disponibilidad definidos.
- **Cloudinary** (solo para el recorrido manual): `backend/.env` ya contiene `cloudinary.cloud-name`,
  `cloudinary.api-key` y `cloudinary.api-secret` (el archivo no se versiona; la plantilla
  `backend/.env.example` los lista sin valores). Las pruebas automáticas no las necesitan
  (sustituyen el cliente por un mock).
- Backend en `http://localhost:8080` y frontend en `http://localhost:5173`.

## Pruebas automáticas

```powershell
cd backend ; ./mvnw verify               # unitarias + integración + JaCoCo (≥ 90 % de líneas de negocio)
cd ../frontend ; pnpm test:coverage     # Vitest (validación de producto e imagen)
pnpm build ; pnpm lint
```

Deben cubrir los casos de [catalog-api](./contracts/catalog-api.md): 401/403/404 por rol, estado y
pertenencia; visibilidad pública (pendiente, rechazado, inhabilitado, pausado, fuera de horario);
producto retirado o no disponible nunca visible; 409 por duplicado concurrente y por versión
desfasada; y rechazo de imágenes inválidas con reloj fijo para la atención.

## Recorrido manual (resultado esperado entre paréntesis)

1. Con la cuenta de restaurante aprobada abrir `/mi-restaurante/productos` y crear un producto con
   imagen (aparece en la lista, disponible). Reiniciar el backend y recargar (se conserva).
2. Intentar crear otro con el mismo nombre y categoría (error 409 claro) y uno con precio `0`, sin
   imagen o con un PDF renombrado a `.jpg` (se explican los errores y no se guarda nada).
3. Editar precio y categoría; marcar **no disponible** y luego **disponible**.
4. Abrir `/restaurantes` **sin iniciar sesión**: el restaurante aparece con su situación de atención
   (**Abierto**). Buscar por nombre con y sin tildes, por categoría y por zona (resultados
   correctos; sin coincidencias aparece el aviso).
5. Abrir su catálogo `/restaurantes/:id` (productos disponibles por categoría con precio e imagen;
   el no disponible no aparece).
6. Pausar el restaurante desde `/mi-restaurante` (sigue visible como **Sin atención por ahora**);
   cambiar el horario para que esté cerrado (**Fuera de horario**).
7. Retirar el producto (desaparece de la gestión y del catálogo público).
8. Con el administrador, rechazar o inhabilitar la cuenta del restaurante (deja de aparecer en la
   búsqueda y su catálogo responde «no encontrado»).
9. Con una cuenta cliente, `POST /api/restaurant/me/products` (403); con el token de otro
   restaurante, `PUT` a un producto ajeno (404); sin token (401).

## Fuera de esta validación

E2E automatizadas (Playwright), carga (RNF-01), caché (RNF-03), HTTPS y CI/CD siguen aplazados y no
se declaran cumplidos.
