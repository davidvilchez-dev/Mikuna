# Validación: Registro, aprobación y datos del restaurante (003)

**Fecha**: 2026-11-17 · **Constitución**: 3.0.0 · **Entorno**: Windows 11, Java 25, PostgreSQL 16 local, Node + pnpm

## Comprobaciones automatizadas

| Comprobación | Resultado |
| --- | --- |
| `./mvnw verify` (backend) | ✅ 0 fallos. Pruebas nuevas: `OrderEligibilityTest` (10), `RestaurantServiceTest` (48), `RestaurantReviewServiceTest` (10) y las de integración `RestaurantOwnerIntegrationTest` (12), `RestaurantAdminIntegrationTest` (12) y `RestaurantScheduleIntegrationTest` (9) contra `mikuna_test_db`. Las pruebas de 002 se adaptaron al retiro de `restaurantApproval` y siguen verdes. |
| JaCoCo, total del código medido | ✅ 408/415 líneas (**98,3 %**) y 144/148 ramas (97,3 %). `restaurant`: `RestaurantService` 88/90, `RestaurantReviewService`, `OrderEligibility` y controladores al 100 %. Exclusiones heredadas de 002: `BackendApplication`, `dto/**`, `SecurityConfig`. |
| `pnpm test:coverage` (frontend) | ✅ 30 pruebas; 98,7 % de líneas y 100 % de ramas en los archivos medidos (`restaurant-validation.ts` incluido) |
| `pnpm build` y `pnpm lint` | ✅ sin errores |
| Playwright | No se ejecutó ni se modificó (constitución 3.0.0, principio V) |

## Trazabilidad

| Requisito | Estado | Evidencia |
| --- | --- | --- |
| RF-05 solicitud de registro | Verificado (API y lógica); interfaz pendiente de recorrido manual | `RestaurantServiceTest`, `RestaurantOwnerIntegrationTest` |
| RF-06 aprobación y habilitación | Verificado (API y lógica); interfaz pendiente de recorrido manual | `RestaurantReviewServiceTest`, `RestaurantAdminIntegrationTest` (incluye resoluciones simultáneas: una 200 y el resto 409), `OrderEligibilityTest` |
| RF-07 datos, horarios y disponibilidad | Verificado (API y lógica); interfaz pendiente de recorrido manual | `RestaurantServiceTest`, `RestaurantScheduleIntegrationTest` |
| RF-04 autorización | **Parcial**: aplicada a las operaciones de este incremento (401, 403 por rol, cuenta inhabilitada y estado del restaurante; sin ids ajenos). Pedidos y entregas siguen pendientes. | Pruebas de integración anteriores |
| RNF-07 | Verificado para las rutas `/api/restaurant/**` y `/api/admin/**` | Matriz de acceso del contrato probada en las tres clases de integración |
| RNF-11 | Verificado: 98,3 % de líneas de negocio en backend | Informe JaCoCo (`backend/target/site/jacoco`) |
| RNF-06, RNF-08, RNF-12 y demás aplazados | Pendientes; no se declaran cumplidos | Constitución 3.0.0 |

## Revisión de seguridad y alcance (T041)

- Ninguna ruta nueva es pública: `/api/restaurant/**` exige `ROLE_RESTAURANT` y `/api/admin/**` exige `ROLE_ADMIN`; el resto sigue autenticado.
- Las respuestas del titular no incluyen `ownerId` ni `resolvedBy` (probado).
- `status`, `ownerId`, `approved` y `available` enviados en el cuerpo de la solicitud responden 400 (probado); la aprobación solo cambia mediante la resolución del administrador.
- No se añadieron secretos, dependencias, imágenes ni integraciones externas.

## Pendiente

- **T040, recorrido manual en el navegador** ([quickstart.md](./quickstart.md)): lo realiza el responsable del proyecto (los agentes no ejecutan Playwright). Debe anotarse también el tiempo de SC-001 (< 3 min) y SC-002 (< 1 min).
- Las bases locales existentes conservan la columna `restaurant_approval` sin uso; no se elimina.
